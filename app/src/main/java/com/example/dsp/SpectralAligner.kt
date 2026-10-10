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
 * @param segmentLags per-segment lag estimates used for the drift fit, for reporting.
 */
data class SpectralAlignment(
    val offsetSamples: Int,
    val rate: Float,
    val confidence: Float,
    val segmentLags: List<Int> = emptyList()
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
    private const val MIN_DRIFT_FIT_R2 = 0.55f

    /** Beyond this the "drift" is more likely a wrong match than a speed difference. */
    private const val MAX_ABS_DRIFT = 0.06f

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

        val coarseLag = bestGlobalLag(target, reference) ?: return null
        val confidence = correlationAt(target, reference, coarseLag)

        val coarseOffset = coarseLag * ENVELOPE_HOP
        val fineOffset = refineOffset(targetSamples, referenceSamples, coarseOffset)
        val drift = estimateDrift(target, reference, coarseLag)

        return SpectralAlignment(
            offsetSamples = fineOffset,
            rate = drift.rate,
            confidence = confidence,
            segmentLags = drift.segmentLags
        )
    }

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

    /** Full search over every plausible lag, returning the frame lag with the best correlation. */
    private fun bestGlobalLag(target: BandEnvelope, reference: BandEnvelope): Int? {
        val maxLag = target.frames - reference.frames
        if (maxLag < 0) return null

        var bestLag = -1
        var bestScore = -1f
        for (lag in 0..maxLag) {
            val score = correlationAt(target, reference, lag)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        return if (bestLag >= 0) bestLag else null
    }

    /**
     * Refines the frame-level offset to sample precision by cross-correlating the raw signals over a
     * short window around the estimate. Envelope resolution alone is ~12 ms, which is enough to
     * locate a song but not to cancel it cleanly.
     */
    private fun refineOffset(
        targetSamples: FloatArray,
        referenceSamples: FloatArray,
        coarseOffset: Int
    ): Int {
        val windowLength = min(referenceSamples.size, 1 shl 19) // at most ~12 s of material
        val searchRadius = ENVELOPE_HOP * 3
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
     * Estimates the playback-rate difference by locating several reference segments independently and
     * fitting a line through (reference position -> target position). Segments whose estimate deviates
     * strongly are dropped so a single bad correlation cannot fake a drift.
     */
    private fun estimateDrift(
        target: BandEnvelope,
        reference: BandEnvelope,
        globalLag: Int
    ): DriftEstimate {
        val segmentFrames = min(reference.frames, max(reference.frames / 6, 24))
        val maxSegments = reference.frames / segmentFrames
        if (maxSegments < 3) return DriftEstimate(1f, emptyList())

        val searchRadius = ENVELOPE_HOP / 4 * 3 // a few frames around the prediction
        val referencePositions = ArrayList<Double>(maxSegments)
        val targetPositions = ArrayList<Double>(maxSegments)

        for (segment in 0 until maxSegments) {
            val startFrame = segment * segmentFrames
            val predicted = globalLag + startFrame
            var bestLag = predicted
            var bestScore = -1f

            val from = (predicted - searchRadius).coerceAtLeast(0)
            val to = min(predicted + searchRadius, target.frames - segmentFrames)
            if (to < from) continue

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

            // Ignore segments where the reference is not really present (silence, solo voice parts).
            if (bestScore < 0.3f) continue

            val referenceCenter = (startFrame + segmentFrames / 2.0) * reference.hop
            val targetCenter = (bestLag.toDouble() + segmentFrames / 2.0) * target.hop
            referencePositions.add(referenceCenter)
            targetPositions.add(targetCenter)
        }

        if (referencePositions.size < 3) return DriftEstimate(1f, emptyList())

        val fit = robustLinearFit(referencePositions, targetPositions)
        val rate = if (fit != null && fit.r2 >= MIN_DRIFT_FIT_R2 &&
            abs(fit.slope - 1.0) <= MAX_ABS_DRIFT
        ) {
            fit.slope.toFloat()
        } else {
            1f
        }

        val lags = targetPositions.mapIndexed { index, targetPosition ->
            (targetPosition - referencePositions[index]).toInt()
        }
        return DriftEstimate(rate, lags)
    }

    private data class DriftEstimate(val rate: Float, val segmentLags: List<Int>)

    private data class LinearFit(val slope: Double, val intercept: Double, val r2: Float)

    /** Least-squares fit with one pass of outlier rejection at three standard deviations. */
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
