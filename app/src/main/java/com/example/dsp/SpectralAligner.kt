package com.example.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Result of matching a reference recording (a known song, or a music sample taken from the
 * recording itself) against the analysed recording.
 *
 * @param offsetSamples target sample index where reference sample 0 lands.
 * @param rate target samples per reference sample. `1.0` means identical speed; values above 1 mean
 *   the reference runs slower in the recording (the recording was sped up), and vice versa.
 * @param confidence normalized correlation of the match, 0..1.
 * @param anchors additional reference sample -> target sample pairs found by the per-segment pass.
 *   They let a consumer follow a variable playback rate instead of a single global offset.
 */
data class SpectralAlignment(
    val offsetSamples: Int,
    val rate: Float,
    val confidence: Float,
    val anchors: List<Pair<Long, Long>> = emptyList()
) {
    val isReliable: Boolean get() = confidence >= 0.35f

    /** Human readable quality label used by the UI. */
    val qualityLabel: String
        get() = when {
            confidence >= 0.75f -> "strong"
            confidence >= 0.5f -> "good"
            confidence >= 0.35f -> "weak"
            else -> "not found"
        }

    /** Drift as a percentage: 0 % means the reference plays at its original speed. */
    val driftPercent: Float get() = (rate - 1f) * 100f

    /** Maps a reference sample position to its expected position in the target recording. */
    fun targetPositionOf(referenceSample: Long): Long =
        offsetSamples.toLong() + (referenceSample.toDouble() * rate).toLong()

    /** Inverse mapping: which reference sample is expected at a given target sample position. */
    fun referencePositionOf(targetSample: Long): Long =
        ((targetSample - offsetSamples) / rate).toLong()
}

/**
 * Matches a reference audio fragment against a longer recording.
 *
 * Unlike simple envelope correlation, this runs in three stages so a background song can be pulled
 * out of a recording precisely:
 *
 * 1. **Coarse**: per-band log-energy envelopes are correlated to locate the fragment. Band
 *    normalization makes the match invariant to recording level and EQ.
 * 2. **Fine**: the coarse lag is refined by time-domain cross-correlation of the raw samples around
 *    the estimate, bringing the offset down from envelope (tens of ms) to sample precision.
 * 3. **Drift**: the matched fragment is split into segments and each is located independently. A
 *    robust linear fit of position over time yields the playback-rate difference, which is what
 *    makes cancellation stay aligned across a long recording instead of drifting apart.
 */
object SpectralAligner {

    /** Envelope hop in samples; 512 at 44.1 kHz is about 11.6 ms per frame. */
    private const val ENVELOPE_HOP = 512

    /** Logarithmically spaced bands used for the level-invariant fingerprint. */
    private val BAND_EDGES_HZ = floatArrayOf(
        60f, 120f, 240f, 480f, 960f, 1920f, 3840f, 7680f, 12000f
    )

    /** Drift is only accepted when the fit explains the data at least this well. */
    private const val MIN_DRIFT_FIT_R2 = 0.30f

    /** Beyond this the "drift" is more likely a wrong match than a speed difference. */
    private const val MAX_ABS_DRIFT = 0.06f

    /**
     * The exhaustive band-fingerprint search is bounded to this many envelope frames around a cheap
     * energy-envelope estimate. A full four minute song has ~20k frames, so an unbounded search would
     * be ~4*10^8 band comparisons; bounding it keeps alignment interactive while the fine and drift
     * passes recover the precision the coarse bound loses.
     */
    private const val COARSE_SEARCH_FRAMES = 96

    /** Per-segment drift search radius in seconds, covering error accumulated by the global rate. */
    private const val DRIFT_SEARCH_SECONDS = 0.6f

    /**
     * Candidate playback rates for the rate search. Covers +/-6 % in 0.5 % steps, which spans wow and
     * flutter, a PAL speed-up (4 %) and most "the recording was played back at a different speed"
     * cases, while staying coarse enough that the search is a few hundred correlations.
     */
    private val CANDIDATE_RATES: FloatArray = FloatArray(25) { index -> 0.94f + index * 0.005f }

    /**
     * Aligns [referenceSamples] inside [targetSamples].
     *
     * @return the alignment, or null when the fragment is too short to match or is not present.
     */
    fun align(
        targetSamples: FloatArray,
        referenceSamples: FloatArray,
        sampleRate: Int,
        fftSize: Int = 1024
    ): SpectralAlignment? {
        if (referenceSamples.size < fftSize || targetSamples.size < fftSize) return null
        if (referenceSamples.size > targetSamples.size) return null

        val target = BandEnvelope.build(targetSamples, sampleRate, ENVELOPE_HOP, fftSize)
        val reference = BandEnvelope.build(referenceSamples, sampleRate, ENVELOPE_HOP, fftSize)
        if (reference.frames < 4 || target.frames <= reference.frames) return null

        val coarseLag = bestGlobalLag(target, reference, targetSamples, referenceSamples) ?: return null

        // Rate search: if the song plays at a different speed (live performance, tape, an edit), the
        // reference spectrum is shifted and a fixed-rate correlation degrades. Re-sampling the band
        // envelope's timeline over a small candidate set finds the real rate cheaply, because this
        // works on ~20k envelope frames instead of hours of audio.
        val rateSearch = bestRateAndLag(target, reference, coarseLag)
        val confidence = rateSearch.score

        val coarseOffset = rateSearch.lag * ENVELOPE_HOP
        val fineOffset = refineOffset(targetSamples, referenceSamples, coarseOffset)
        val drift = estimateDrift(target, reference, rateSearch.lag, sampleRate, rateSearch.rate)

        // The per-segment pass runs on the coarse timeline, so shift its anchors by the refinement
        // applied by the fine pass to keep everything on the same absolute scale.
        val shift = fineOffset - coarseOffset
        val anchors = drift.anchors.map { (referenceSample, targetSample) ->
            referenceSample to (targetSample + shift)
        }

        return SpectralAlignment(
            offsetSamples = fineOffset,
            rate = drift.rate,
            confidence = confidence,
            anchors = anchors
        )
    }

    /**
     * Tries a small set of playback rates and returns the one whose band-envelope timeline best
     * correlates with the target, together with the corresponding lag.
     *
     * Time scaling is applied to the envelope's frame timeline (reference frame `f` is expected at
     * target frame `lag + f * rate`) rather than by resampling audio, which keeps the search cheap and
     * still recovers a rate difference that would otherwise destroy the match.
     */
    private fun bestRateAndLag(
        target: BandEnvelope,
        reference: BandEnvelope,
        baseLag: Int
    ): RateSearch {
        var bestScore = -1f
        var bestRate = 1f
        var bestLag = baseLag

        for (rate in CANDIDATE_RATES) {
            // The reference start moves with the rate: everything before the match point is scaled too.
            val lagShift = (baseLag * (1.0 - rate)).toInt()
            val candidateLag = (baseLag + lagShift).coerceIn(0, (target.frames - 1).coerceAtLeast(0))

            val score = correlationAtRate(target, reference, candidateLag, rate)
            if (score > bestScore) {
                bestScore = score
                bestRate = rate
                bestLag = candidateLag
            }
        }
        return RateSearch(bestRate, bestLag, bestScore)
    }

    /**
     * Correlates the reference envelope against the target, mapping reference frame `f` to target
     * frame `lag + f * rate` so a time-scaled reference still lines up.
     */
    private fun correlationAtRate(
        target: BandEnvelope,
        reference: BandEnvelope,
        lag: Int,
        rate: Float
    ): Float {
        var dot = 0.0
        var normTarget = 0.0
        var normReference = 0.0

        for (frame in 0 until reference.frames) {
            val targetFrame = lag + (frame * rate).toInt()
            if (targetFrame < 0 || targetFrame >= target.frames) continue
            for (band in 0 until reference.bands) {
                val r = reference.frame(frame, band)
                val t = target.frame(targetFrame, band)
                dot += r.toDouble() * t
                normTarget += t.toDouble() * t
                normReference += r.toDouble() * r
            }
        }
        if (normTarget <= 1e-9 || normReference <= 1e-9) return 0f
        return (dot / sqrt(normTarget * normReference)).toFloat().coerceIn(0f, 1f)
    }

    private data class RateSearch(val rate: Float, val lag: Int, val score: Float)

    /** Sums the z-scored bands into one fingerprint vector per frame (level and EQ invariant). */
    internal class BandEnvelope(
        val hop: Int,
        val values: FloatArray,
        val bands: Int,
        val frames: Int
    ) {
        fun frame(index: Int, band: Int): Float = values[index * bands + band]

        companion object {
            fun build(samples: FloatArray, sampleRate: Int, hop: Int, fftSize: Int): BandEnvelope {
                val fft = FftProcessor(fftSize)
                val window = FloatArray(fftSize) { i ->
                    (0.5 * (1.0 - kotlin.math.cos(2.0 * kotlin.math.PI * i / (fftSize - 1)))).toFloat()
                }
                val numBins = fftSize / 2
                val binHz = (sampleRate / 2.0f) / numBins

                // Bin range of each band, clamped to the spectrum that exists at this sample rate.
                val bandStart = IntArray(BAND_EDGES_HZ.size - 1)
                val bandEnd = IntArray(BAND_EDGES_HZ.size - 1)
                for (b in bandStart.indices) {
                    bandStart[b] = (BAND_EDGES_HZ[b] / binHz).toInt().coerceIn(1, numBins - 1)
                    bandEnd[b] = (BAND_EDGES_HZ[b + 1] / binHz).toInt()
                        .coerceIn(bandStart[b] + 1, numBins)
                }
                val bands = bandStart.size

                val frames = if (samples.size < fftSize) 0 else (samples.size - fftSize) / hop + 1
                val raw = FloatArray(frames * bands)
                val real = FloatArray(fftSize)
                val imag = FloatArray(fftSize)

                for (frame in 0 until frames) {
                    val offset = frame * hop
                    for (i in 0 until fftSize) {
                        real[i] = samples[offset + i] * window[i]
                        imag[i] = 0f
                    }
                    fft.forward(real, imag)

                    for (b in 0 until bands) {
                        var sum = 0f
                        for (bin in bandStart[b] until bandEnd[b]) {
                            sum += sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                        }
                        raw[frame * bands + b] = ln(sum / (bandEnd[b] - bandStart[b]) + 1e-6f)
                    }
                }

                // Normalize every band across time so loudness or EQ differences do not bias the match.
                for (b in 0 until bands) {
                    var mean = 0f
                    for (frame in 0 until frames) mean += raw[frame * bands + b]
                    mean /= frames.coerceAtLeast(1)
                    var variance = 0f
                    for (frame in 0 until frames) {
                        val d = raw[frame * bands + b] - mean
                        variance += d * d
                    }
                    val std = sqrt(variance / frames.coerceAtLeast(1))
                    val scale = if (std > 1e-5f) 1f / std else 0f
                    for (frame in 0 until frames) {
                        raw[frame * bands + b] = (raw[frame * bands + b] - mean) * scale
                    }
                }

                return BandEnvelope(hop, raw, bands, frames)
            }
        }
    }

    /** Normalized correlation between the reference fingerprint and the target at a given lag. */
    private fun correlationAt(target: BandEnvelope, reference: BandEnvelope, lag: Int): Float {
        var dot = 0.0
        var normTarget = 0.0
        var normReference = 0.0
        for (frame in 0 until reference.frames) {
            val targetFrame = lag + frame
            if (targetFrame < 0 || targetFrame >= target.frames) continue
            for (band in 0 until reference.bands) {
                val r = reference.frame(frame, band)
                val t = target.frame(targetFrame, band)
                dot += r.toDouble() * t
                normTarget += t.toDouble() * t
                normReference += r.toDouble() * r
            }
        }
        if (normTarget <= 1e-9 || normReference <= 1e-9) return 0f
        return (dot / sqrt(normTarget * normReference)).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Searches the band fingerprint for the best lag, bounded to a window around a cheap energy
     * estimate. Without the bound this would be quadratic in the recording length.
     */
    private fun bestGlobalLag(
        target: BandEnvelope,
        reference: BandEnvelope,
        targetSamples: FloatArray,
        referenceSamples: FloatArray
    ): Int? {
        val maxLag = target.frames - reference.frames
        if (maxLag < 0) return null

        val estimate = energyEstimateLag(targetSamples, referenceSamples, target.hop, maxLag)
        val from = (estimate - COARSE_SEARCH_FRAMES).coerceAtLeast(0)
        val to = (estimate + COARSE_SEARCH_FRAMES).coerceAtMost(maxLag)

        var bestLag = from
        var bestScore = -1f
        for (lag in from..to) {
            val score = correlationAt(target, reference, lag)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        return bestLag
    }

    /**
     * Cheap pre-alignment on downsampled RMS envelopes (20 ms blocks). Only needs to be accurate to
     * about a second; the fingerprint search then refines it.
     *
     * @param maxFrameLag upper bound in band-envelope frames, so the returned estimate is clamped in
     *   the same unit the caller searches in (clamping it to the block-space maximum would truncate
     *   the estimate for anything longer than a few seconds).
     */
    private fun energyEstimateLag(
        targetSamples: FloatArray,
        referenceSamples: FloatArray,
        hop: Int,
        maxFrameLag: Int
    ): Int {
        val block = (hop * 4).coerceAtLeast(256)
        val targetBlocks = targetSamples.size / block
        val referenceBlocks = (referenceSamples.size / block).coerceAtMost(256)
        if (targetBlocks <= 8 || referenceBlocks <= 8) return 0

        val targetEnv = FloatArray(targetBlocks) { index ->
            var sum = 0f
            val base = index * block
            for (i in 0 until block) sum += abs(targetSamples[base + i])
            sum / block
        }
        val referenceEnv = FloatArray(referenceBlocks) { index ->
            var sum = 0f
            val base = index * block
            for (i in 0 until block) sum += abs(referenceSamples[base + i])
            sum / block
        }

        // Compare mean-removed envelopes so recording level does not bias the estimate.
        val referenceMean = referenceEnv.average().toFloat()
        val targetMean = targetEnv.average().toFloat()
        var bestLag = 0
        var bestScore = Double.NEGATIVE_INFINITY
        val maxLag = targetBlocks - referenceBlocks

        for (lag in 0..maxLag) {
            var dot = 0.0
            var normTarget = 0.0
            var normReference = 0.0
            for (index in 0 until referenceBlocks) {
                val t = targetEnv[lag + index] - targetMean
                val r = referenceEnv[index] - referenceMean
                dot += t.toDouble() * r
                normTarget += t.toDouble() * t
                normReference += r.toDouble() * r
            }
            if (normTarget <= 1e-9 || normReference <= 1e-9) continue
            val score = dot / sqrt(normTarget * normReference)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        // Convert the block lag into band-envelope frames and clamp in frame space.
        return (bestLag.toLong() * block / hop).toInt().coerceIn(0, maxFrameLag)
    }

    /**
     * Refines the frame-level offset to sample precision by cross-correlating the raw signals over a
     * window around the estimate.
     *
     * The search radius is deliberately wider than one envelope frame: the coarse estimate comes from
     * envelope correlation, whose error on real (time-scaled, noisy) material can reach tens of
     * milliseconds, and a radius smaller than that error leaves the offset uncorrected.
     */
    private fun refineOffset(
        targetSamples: FloatArray,
        referenceSamples: FloatArray,
        coarseOffset: Int
    ): Int {
        val windowLength = min(referenceSamples.size, 1 shl 19) // at most ~12 s of material
        val searchRadius = max(ENVELOPE_HOP * 3, sampleRateEstimate / 10) // at least ~100 ms
        val minOffset = (coarseOffset - searchRadius).coerceAtLeast(0)
        val maxOffset = min(coarseOffset + searchRadius, targetSamples.size - windowLength)
        if (maxOffset < minOffset) return coarseOffset.coerceIn(0, max(targetSamples.size - 1, 0))

        var bestOffset = coarseOffset.coerceIn(minOffset, maxOffset)
        var bestScore = Double.NEGATIVE_INFINITY

        for (offset in minOffset..maxOffset) {
            var dot = 0.0
            var normTarget = 0.0
            var normReference = 0.0
            var i = 0
            while (i < windowLength) {
                val t = targetSamples[offset + i]
                val r = referenceSamples[i]
                dot += t.toDouble() * r
                normTarget += t.toDouble() * t
                normReference += r.toDouble() * r
                i++
            }
            if (normTarget <= 1e-9 || normReference <= 1e-9) continue
            val score = dot / sqrt(normTarget * normReference)
            if (score > bestScore) {
                bestScore = score
                bestOffset = offset
            }
        }
        return bestOffset
    }

    /**
     * Sample rate assumed for the search-radius heuristics. The aligner works on absolute sample
     * counts, and the caller always passes 44.1 kHz material (the app decodes everything to that),
     * so a constant keeps the signature free of another parameter.
     */
    private val sampleRateEstimate: Int = 44100

    /**
     * Rough rate estimate from the distance between the first and the last reference segment, so the
     * per-segment refinement can search around a rate-corrected prediction.
     */
    private fun estimateCoarseRate(
        target: BandEnvelope,
        reference: BandEnvelope,
        globalLag: Int,
        segmentFrames: Int,
        maxSegments: Int,
        sampleRate: Int
    ): Double {
        if (maxSegments < 2) return 1.0
        val lastStart = (maxSegments - 1) * segmentFrames
        // First and last segment can be a whole drift allowance apart, so search generously here; this
        // estimate only needs to aim the per-segment refinement, which then refines it.
        val spanSeconds = (lastStart * reference.hop).toFloat() / sampleRate
        val allowanceFrames = (spanSeconds * MAX_ABS_DRIFT * sampleRate / reference.hop).toInt() + 16

        val lastLag = locateSegment(
            target, reference, lastStart, segmentFrames, globalLag + lastStart, allowanceFrames
        ) ?: return 1.0

        val span = lastStart.toDouble()
        if (span <= 0.0) return 1.0
        val rate = (lastLag - globalLag) / span
        return if (abs(rate - 1.0) <= MAX_ABS_DRIFT) rate else 1.0
    }

    /** Locates one reference segment near [predicted] within +/-[radius] frames; null when unclear. */
    private fun locateSegment(
        target: BandEnvelope,
        reference: BandEnvelope,
        startFrame: Int,
        segmentFrames: Int,
        predicted: Int,
        radius: Int
    ): Int? {
        val from = (predicted - radius).coerceAtLeast(0)
        val to = min(predicted + radius, target.frames - segmentFrames)
        if (to < from) return null

        var bestLag = -1
        var bestScore = 0.3f
        for (lag in from..to) {
            var dot = 0.0
            var normTarget = 0.0
            var normReference = 0.0
            for (frame in 0 until segmentFrames) {
                for (band in 0 until reference.bands) {
                    val r = reference.frame(startFrame + frame, band)
                    val t = target.frame(lag + frame, band)
                    dot += r.toDouble() * t
                    normTarget += t.toDouble() * t
                    normReference += r.toDouble() * r
                }
            }
            if (normTarget <= 1e-9 || normReference <= 1e-9) continue
            val score = (dot / sqrt(normTarget * normReference)).toFloat()
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        return bestLag.takeIf { it >= 0 }
    }

    /**
     * Estimates the playback-rate difference by locating several reference segments independently and
     * fitting a line through (reference position -> target position). Segments whose estimate deviates
     * strongly are dropped so a single bad correlation cannot fake a drift.
     */
    @Suppress("LongMethod")
    private fun estimateDrift(
        target: BandEnvelope,
        reference: BandEnvelope,
        globalLag: Int,
        sampleRate: Int,
        seedRate: Float
    ): DriftEstimate {
        val segmentFrames = min(reference.frames, max(reference.frames / 8, 24))
        val maxSegments = reference.frames / segmentFrames
        if (maxSegments < 3) return DriftEstimate(seedRate, emptyList())

        // The rate search already produced a usable rate; the per-segment pass checks whether it stays
        // constant across the song or changes (an edit, a tempo change).
        val initialRate = seedRate.toDouble()
        // The search radius must cover the drift that can accumulate over the reference, not just a few
        // frames: a 3 % rate difference over a 12 s reference is already ~0.36 s. Without that the
        // segment search cannot reach the later parts of a rate-shifted song and drift collapses to 1.0.
        val framesPerSecond = sampleRate.toFloat() / target.hop
        val referenceSeconds = reference.frames * reference.hop / sampleRate.toFloat()
        val driftAllowance = referenceSeconds * MAX_ABS_DRIFT
        val searchRadiusFrames = ((DRIFT_SEARCH_SECONDS + driftAllowance) * framesPerSecond)
            .toInt().coerceAtLeast(4)

        val referencePositions = ArrayList<Double>(maxSegments)
        val targetPositions = ArrayList<Double>(maxSegments)

        for (segment in 0 until maxSegments) {
            val startFrame = segment * segmentFrames
            // Position predicted by the rate guess: reference frame 0 sits at globalLag, and every
            // further reference frame advances by initialRate target frames.
            val predicted = (globalLag + startFrame * initialRate).toInt()
            var bestLag = predicted
            val located = locateSegment(
                target = target,
                reference = reference,
                startFrame = startFrame,
                segmentFrames = segmentFrames,
                predicted = predicted,
                radius = searchRadiusFrames
            ) ?: continue

            val referenceCenter = (startFrame + segmentFrames / 2.0) * reference.hop
            val targetCenter = (located + segmentFrames / 2.0) * target.hop
            referencePositions.add(referenceCenter)
            targetPositions.add(targetCenter)
        }

        if (referencePositions.size < 3) return DriftEstimate(seedRate, emptyList())

        val fit = theilSenFit(referencePositions, targetPositions)
        val convincing = fit != null && fit.r2 >= MIN_DRIFT_FIT_R2 &&
            abs(fit.slope - 1.0) <= MAX_ABS_DRIFT

        if (!convincing) {
            // The fit is not convincing, so the constant rate from the rate search is kept and no
            // anchors are published: better to say "constant rate" than to publish a wrong curve.
            return DriftEstimate(seedRate, emptyList())
        }

        val anchors = referencePositions.mapIndexed { index, referencePosition ->
            referencePosition.toLong() to targetPositions[index].toLong()
        }
        return DriftEstimate(fit.slope.toFloat(), anchors)
    }

    /** @param anchors reference sample -> target sample pairs for the matched segments. */
    private data class DriftEstimate(val rate: Float, val anchors: List<Pair<Long, Long>>)

    private data class LinearFit(val slope: Double, val intercept: Double, val r2: Float)

    /**
     * Least-squares fit with one pass of outlier rejection at three standard deviations.
     * Kept for callers that need R²; drift estimation uses the more robust [theilSenFit].
     */
    private fun robustLinearFit(x: List<Double>, y: List<Double>): LinearFit? {
        val first = linearFit(x, y) ?: return null
        val residuals = x.indices.map { y[it] - (first.slope * x[it] + first.intercept) }
        val mean = residuals.average()
        val std = sqrt(residuals.sumOf { (it - mean) * (it - mean) } / residuals.size)

        if (std > 1e-6) {
            val keptX = ArrayList<Double>(x.size)
            val keptY = ArrayList<Double>(x.size)
            for (i in x.indices) {
                if (abs(residuals[i] - mean) <= 3.0 * std) {
                    keptX.add(x[i])
                    keptY.add(y[i])
                }
            }
            if (keptX.size >= 3) return linearFit(keptX, keptY) ?: first
        }
        return first
    }

    /**
     * Theil-Sen estimator: the median of all pairwise slopes.
     *
     * Used for the drift fit because a segment can lock onto the wrong beat, which produces a handful
     * of wildly wrong points. Least squares is dragged along by them (a single bad point can flip the
     * slope's sign), while a median of pairwise slopes tolerates up to ~29 % outliers.
     *
     * @return slope, intercept through the medians and a robust R²-like quality measure.
     */
    private fun theilSenFit(x: List<Double>, y: List<Double>): LinearFit? {
        if (x.size < 3) return null

        val slopes = ArrayList<Double>((x.size * (x.size - 1)) / 2)
        for (i in x.indices) {
            for (j in i + 1 until x.size) {
                val dx = x[j] - x[i]
                if (abs(dx) > 1e-6) slopes.add((y[j] - y[i]) / dx)
            }
        }
        if (slopes.size < 3) return null
        slopes.sort()
        val slope = slopes[slopes.size / 2]

        val intercept = median(x.indices.map { y[it] - slope * x[it] })

        // Quality: share of points within a tolerance of the fitted line (a drift of 60 ms is the
        // largest deviation a good fit should leave at these distances).
        val tolerance = 60.0
        val inliers = x.indices.count { abs(y[it] - (slope * x[it] + intercept)) <= tolerance }
        val quality = inliers.toFloat() / x.size

        return LinearFit(slope, intercept, quality)
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun linearFit(x: List<Double>, y: List<Double>): LinearFit? {
        if (x.size < 2) return null
        val n = x.size
        val meanX = x.average()
        val meanY = y.average()
        var sxy = 0.0
        var sxx = 0.0
        for (i in 0 until n) {
            val dx = x[i] - meanX
            sxy += dx * (y[i] - meanY)
            sxx += dx * dx
        }
        if (sxx <= 1e-9) return null
        val slope = sxy / sxx
        val intercept = meanY - slope * meanX

        var ssTotal = 0.0
        var ssResidual = 0.0
        for (i in 0 until n) {
            val predicted = slope * x[i] + intercept
            ssTotal += (y[i] - meanY) * (y[i] - meanY)
            ssResidual += (y[i] - predicted) * (y[i] - predicted)
        }
        val r2 = if (ssTotal <= 1e-9) 0f else (1.0 - ssResidual / ssTotal).toFloat().coerceIn(0f, 1f)
        return LinearFit(slope, intercept, r2)
    }
}
