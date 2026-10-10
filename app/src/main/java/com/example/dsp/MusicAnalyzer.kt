package com.example.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How a short window of the recording behaves acoustically. */
enum class AudioWindowKind(val labelCz: String) {
    SILENCE("Ticho"),
    MUSIC("Hudba v pozadí"),
    SPEECH("Řeč"),
    MIXED("Směs řeči a hudby")
}

/**
 * One analysed window of the recording.
 *
 * @param musicScore 0..1, how music-like the window is (beat periodicity + spread spectrum).
 * @param speechScore 0..1, how speech-like it is (energy concentrated in the vocal formant band).
 * @param beatBpm detected tempo when a clear beat is present.
 */
data class AudioWindow(
    val startMs: Long,
    val endMs: Long,
    val levelDb: Float,
    val musicScore: Float,
    val speechScore: Float,
    val beatBpm: Float,
    val kind: AudioWindowKind
)

/**
 * Result of scanning a recording for background music.
 *
 * @param windows per-window detail, in time order.
 * @param musicSegments merged contiguous music windows, longest first.
 * @param silenceRatio share of the recording that is near the noise floor.
 * @param musicRatio share of the recording classified as music.
 */
data class MusicAnalysis(
    val windows: List<AudioWindow>,
    val musicSegments: List<Pair<Long, Long>>,
    val silenceRatio: Float,
    val musicRatio: Float
) {
    /** Best window to learn a music profile from: strongest music score, at least [minMs] long. */
    fun bestLearningSegment(minMs: Long = 1500L): Pair<Long, Long>? =
        musicSegments.firstOrNull { it.second - it.first >= minMs }
            ?: musicSegments.firstOrNull()

    val summary: String
        get() {
            if (windows.isEmpty()) return "nahrávka je příliš krátká na analýzu"
            val musicPercent = (musicRatio * 100).toInt()
            val segments = musicSegments.size
            return when {
                segments == 0 -> "hudba v pozadí nenalezena (ticho ${(silenceRatio * 100).toInt()} %)"
                else -> "hudba nalezena v $segments úsecích, celkem $musicPercent % nahrávky"
            }
        }
}

/**
 * Finds and describes the background music in a recording.
 *
 * The point of this analysis is to make the "known song removal" step work without the analyst
 * having to hunt for a clean music-only moment by hand: it scans the recording, marks where music
 * dominates over speech, and proposes the best interval to learn a spectral profile from. Speech is
 * distinguished from music by energy distribution (speech concentrates in the 300-3400 Hz formant
 * band and has a falling spectrum) and by rhythmic periodicity (music has a beat, speech does not).
 */
object MusicAnalyzer {

    private const val WINDOW_MS = 1000
    private const val HOP_MS = 500
    private const val FFT_SIZE = 1024

    /**
     * Hop for the onset envelope used for beat detection. Beat periods of interest are 300-1200 ms,
     * which cannot be resolved at the 500 ms classification hop, so a dedicated fine envelope is
     * built from cheap RMS blocks instead of FFT frames.
     */
    private const val ONSET_HOP_MS = 25

    /**
     * Beat search range: 50-150 BPM. The lower bound is kept above the syllabic rate of speech
     * (3-6 Hz, i.e. 170-330 ms) so talking is not mistaken for a musical pulse. Faster music is
     * perceived at half tempo anyway, so little is lost by not searching below 400 ms.
     */
    private const val MIN_BEAT_MS = 400f
    private const val MAX_BEAT_MS = 1200f

    /**
     * Tempo reported for a window with a detected pulse. The per-window lag behind the periodic peak
     * is not tracked (the label is only informative), so the middle of the search range is used
     * rather than implying a precision that was not measured.
     */
    private const val BEAT_LAG_PROBE_MS = 500f

    /**
     * Minimum distance (as a fraction of the recent local maximum) an onset has to reach to count as
     * a peak, rather than noise riding on the shoulder of a real one.
     */
    private const val PEAK_PROMINENCE = 0.35f

    /** At least this many consecutive onset peaks must agree in spacing before calling it a beat. */
    private const val MIN_PEAKS_FOR_BEAT = 6

    /** How far around a frame to look for peaks when judging rhythmic steadiness. */
    private const val STEADY_CONTEXT_MS = 4000f

    /**
     * Maximum coefficient of variation (stdev / mean) of consecutive peak-to-peak intervals that is
     * still considered "steady". This is deliberately not very tight: the onset envelope's own
     * measurement jitter gives even a perfectly metronomic beat a CV in the 0.10-0.15 range (measured
     * directly on synthetic fixtures with a known-steady pulse). What actually separates music from
     * talking is requiring many consistent intervals in a row ([MIN_PEAKS_FOR_BEAT]), not a razor-sharp
     * variance threshold on just a few of them - a handful of syllables landing close to evenly spaced
     * by chance is far more likely than a long run of them doing so. The bound is set well above the
     * observed jitter ceiling so a real steady beat is not scored as barely-passing.
     */
    private const val MAX_BEAT_CV = 0.22f

    fun analyze(samples: FloatArray, sampleRate: Int): MusicAnalysis {
        if (samples.isEmpty() || sampleRate <= 0) {
            return MusicAnalysis(emptyList(), emptyList(), 0f, 0f)
        }

        val windowSamples = (sampleRate * WINDOW_MS / 1000).coerceAtLeast(FFT_SIZE)
        val hopSamples = (sampleRate * HOP_MS / 1000).coerceAtLeast(1)
        if (samples.size < windowSamples) {
            return MusicAnalysis(emptyList(), emptyList(), 0f, 0f)
        }

        val fft = FftProcessor(FFT_SIZE)
        val window = FloatArray(FFT_SIZE) { i ->
            (0.5 * (1.0 - kotlin.math.cos(2.0 * kotlin.math.PI * i / (FFT_SIZE - 1)))).toFloat()
        }

        // Total level to derive a noise floor for the silence test.
        var sumSquares = 0.0
        for (sample in samples) sumSquares += sample.toDouble() * sample
        val overallRms = sqrt(sumSquares / samples.size)
        val noiseFloorDb = if (overallRms > 1e-4) {
            (20 * ln(overallRms) / ln(10.0)).toFloat() - 24f
        } else -90f

        val rawWindows = ArrayList<WindowFeatures>()
        val levels = ArrayList<Float>()

        var start = 0
        while (start + windowSamples <= samples.size) {
            val features = analyseWindow(samples, start, windowSamples, sampleRate, fft, window)
            rawWindows.add(features)
            levels.add(features.levelDb)
            start += hopSamples
        }
        if (rawWindows.isEmpty()) return MusicAnalysis(emptyList(), emptyList(), 0f, 0f)

        // Beat detection needs a finer time base than the 500 ms classification hop, so it runs on a
        // dedicated RMS onset envelope sampled every 25 ms.
        val beatScores = beatPeriodicity(samples, sampleRate)
        val onsetHopSamples = (sampleRate * ONSET_HOP_MS / 1000).coerceAtLeast(1)

        val finished = rawWindows.mapIndexed { index, features ->
            val startMs = index.toLong() * HOP_MS
            val endMs = startMs + WINDOW_MS
            // A window with no signal of its own cannot have a pulse: the onset envelope is global, so
            // a silent passage would otherwise inherit the beat of the music around it and report a
            // pulse (and a music score) where there is nothing at all.
            val isSilent = features.levelDb < noiseFloorDb + 3f
            val beat = if (isSilent) 0f
            else beatScores.strongestBetween(startMs, endMs, onsetHopSamples, sampleRate)

            // Music evidence: a pulse, energy outside the vocal band (bass and cymbals), broadband
            // spectrum. The pulse is weighted most heavily since it is the most reliable discriminator
            // (near-zero for speech, regardless of how wordy or quiet it is); spectral flatness carries
            // the least weight because it only distinguishes noisy/percussive music from tonal speech -
            // it says nothing when the music itself is mostly tonal (e.g. a solo instrument or chords).
            val outOfVoiceBand = (features.lowBandRatio + features.highBandRatio).coerceIn(0f, 1f)
            val musicScore = (beat * 0.45f + outOfVoiceBand * 0.40f + features.flatness * 0.15f)
                .coerceIn(0f, 1f)
            val speechScore = (features.voiceBandRatio * 0.65f + (1f - beat) * 0.35f).coerceIn(0f, 1f)
            val kind = classify(features.levelDb, noiseFloorDb, musicScore, speechScore)
            AudioWindow(
                startMs = startMs,
                endMs = endMs,
                levelDb = features.levelDb,
                musicScore = musicScore,
                speechScore = speechScore,
                beatBpm = if (beat > 0.15f) 60000f / BEAT_LAG_PROBE_MS else 0f,
                kind = kind
            )
        }

        val musicSegments = mergeSegments(finished.filter { it.kind == AudioWindowKind.MUSIC })
        val silenceCount = finished.count { it.kind == AudioWindowKind.SILENCE }
        val musicCount = finished.count { it.kind == AudioWindowKind.MUSIC }

        return MusicAnalysis(
            windows = finished,
            musicSegments = musicSegments.sortedByDescending { it.second - it.first },
            silenceRatio = silenceCount.toFloat() / finished.size,
            musicRatio = musicCount.toFloat() / finished.size
        )
    }

    /**
     * Per-window features used to classify the window.
     */
    private data class WindowFeatures(
        val levelDb: Float,
        val lowBandRatio: Float,
        val highBandRatio: Float,
        val voiceBandRatio: Float,
        val flatness: Float
    )

    /**
     * Per-window features: mean level, low band ratio, vocal band ratio and spectral flatness.
     */
    private fun analyseWindow(
        samples: FloatArray,
        start: Int,
        length: Int,
        sampleRate: Int,
        fft: FftProcessor,
        window: FloatArray
    ): WindowFeatures {
        val frameCount = (length - FFT_SIZE) / FFT_SIZE + 1
        val numBins = FFT_SIZE / 2
        val binHz = (sampleRate / 2.0f) / numBins

        val lowEnd = (300f / binHz).toInt().coerceIn(1, numBins - 3)
        val voiceEnd = (3400f / binHz).toInt().coerceIn(lowEnd + 1, numBins - 2)
        val highStart = (6000f / binHz).toInt().coerceIn(voiceEnd, numBins - 1)

        // Power (squared magnitude), not magnitude: summing linear magnitudes lets broadband noise
        // spread over hundreds of bins outweigh a few strong tonal bins, which made music look like it
        // had all of its energy in the vocal band.
        var totalPower = 0.0
        var lowPower = 0.0
        var voicePower = 0.0
        var highPower = 0.0
        var logPowerSum = 0.0
        var bins = 0

        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)

        for (frame in 0 until frameCount) {
            val offset = start + frame * FFT_SIZE
            for (i in 0 until FFT_SIZE) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }
            fft.forward(real, imag)

            for (bin in 1 until numBins) {
                val power = (real[bin] * real[bin] + imag[bin] * imag[bin]).toDouble()
                totalPower += power
                logPowerSum += ln(power.toFloat() + 1e-12f)
                bins++
                // Bands are exclusive so the ratios describe where the energy actually sits.
                if (bin < lowEnd) {
                    lowPower += power
                } else if (bin < voiceEnd) {
                    voicePower += power
                } else if (bin >= highStart) {
                    highPower += power
                }
            }
        }

        // Level of the window in dBFS, measured directly from the samples.
        var sumSquares = 0.0
        for (i in start until start + length) sumSquares += samples[i].toDouble() * samples[i]
        val rms = sqrt(sumSquares / length)
        val levelDb = if (rms > 1e-5) (20 * ln(rms) / ln(10.0)).toFloat() else -90f

        if (bins == 0 || totalPower <= 1e-12) {
            return WindowFeatures(levelDb, 0f, 0f, 0f, 0f)
        }

        val lowRatio = (lowPower / totalPower).toFloat()
        val voiceRatio = (voicePower / totalPower).toFloat()
        val highRatio = (highPower / totalPower).toFloat()
        // Spectral flatness on power: music (broadband, noisy) is flatter than tonal speech.
        val logMean = logPowerSum / bins
        val geometricMean = kotlin.math.exp(logMean.toFloat())
        val arithmeticMean = (totalPower / bins).toFloat()
        val flatness = (geometricMean / (arithmeticMean + 1e-12f)).coerceIn(0f, 1f)

        return WindowFeatures(levelDb, lowRatio, highRatio, voiceRatio, flatness)
    }

    private fun classify(
        levelDb: Float,
        noiseFloorDb: Float,
        musicScore: Float,
        speechScore: Float
    ): AudioWindowKind = when {
        levelDb < noiseFloorDb + 3f -> AudioWindowKind.SILENCE
        musicScore >= 0.45f && musicScore > speechScore -> AudioWindowKind.MUSIC
        speechScore >= 0.6f && musicScore < 0.35f -> AudioWindowKind.SPEECH
        else -> AudioWindowKind.MIXED
    }

    /**
     * Onset periodicity sampled on a fine time base.
     *
     * The onset signal is the positive change of short-term energy (the way a drum hit or a note
     * attack shows up). Its autocorrelation inside plausible beat periods is normalized by the
     * zero-lag energy, so a quiet passage scores comparably to a loud one.
     */
    private class BeatEnvelope(
        val hopSamples: Int,
        val sampleRate: Int,
        private val scores: FloatArray
    ) {
        /** Strongest pulse score within [startMs]..[endMs]. */
        fun strongestBetween(startMs: Long, endMs: Long, hop: Int, rate: Int): Float {
            if (scores.isEmpty()) return 0f
            val from = (startMs * rate / 1000 / hop).toInt().coerceIn(0, scores.size - 1)
            val to = (endMs * rate / 1000 / hop).toInt().coerceIn(from, scores.size - 1)
            var best = 0f
            for (index in from..to) {
                if (scores[index] > best) best = scores[index]
            }
            return best
        }
    }

    /**
     * Builds the onset envelope and returns, per 25 ms frame, how steady the rhythm around it is.
     *
     * Earlier this scored periodicity via autocorrelation, but a handful of syllables can line up
     * at one specific lag by pure chance and score just as "peaky" as a real beat. What actually
     * tells a musical pulse apart from speech is that its *timing is steady*: individual onset
     * peaks are picked out, the intervals between consecutive peaks are measured, and a real beat
     * is one where several consecutive intervals agree closely (low coefficient of variation).
     * Random syllable timing fails this test even when a couple of intervals happen to coincide.
     */
    private fun beatPeriodicity(samples: FloatArray, sampleRate: Int): BeatEnvelope {
        val hop = (sampleRate * ONSET_HOP_MS / 1000).coerceAtLeast(1)
        val frames = samples.size / hop
        if (frames < 8) return BeatEnvelope(hop, sampleRate, FloatArray(0))

        // Short-term energy per frame.
        val energy = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0.0
            val base = frame * hop
            for (i in 0 until hop) {
                val value = samples[base + i]
                sum += value.toDouble() * value
            }
            energy[frame] = sqrt(sum / hop).toFloat()
        }

        // Onset signal: only rises count, so a decaying note does not register continuously.
        val onset = FloatArray(frames)
        for (frame in 1 until frames) {
            val rise = energy[frame] - energy[frame - 1]
            onset[frame] = if (rise > 0f) rise else 0f
        }

        // Peak-pick the onset signal: a frame is a peak if it is a local maximum over a short
        // neighbourhood and is not negligible next to the recent onset activity (so FFT/quantization
        // noise floor jitter is not picked up as a stream of "onsets").
        val neighbourhood = max((80f / ONSET_HOP_MS).toInt(), 1)
        val localContext = max((1500f / ONSET_HOP_MS).toInt(), neighbourhood * 2)
        val peakFrames = ArrayList<Int>()
        for (frame in 0 until frames) {
            val value = onset[frame]
            if (value <= 1e-9f) continue
            var isLocalMax = true
            var lo = max(frame - neighbourhood, 0)
            val hi = min(frame + neighbourhood, frames - 1)
            while (lo <= hi) {
                if (lo != frame && onset[lo] >= value) {
                    isLocalMax = false
                    break
                }
                lo++
            }
            if (!isLocalMax) continue
            var recentMax = value
            var i = max(frame - localContext, 0)
            val end = min(frame + localContext, frames - 1)
            while (i <= end) {
                if (onset[i] > recentMax) recentMax = onset[i]
                i++
            }
            if (value >= recentMax * PEAK_PROMINENCE) peakFrames.add(frame)
        }

        val hopMs = ONSET_HOP_MS.toFloat()
        val scores = FloatArray(frames)
        if (peakFrames.size < MIN_PEAKS_FOR_BEAT) return BeatEnvelope(hop, sampleRate, scores)

        // Steadiness context: wide enough to contain several beat cycles even at the slowest tempo
        // considered, so the consistency check has something to measure.
        val steadyContextFrames = max((STEADY_CONTEXT_MS / hopMs).toInt(), (MAX_BEAT_MS / hopMs).toInt() * 3)

        for (frame in 0 until frames) {
            val from = frame - steadyContextFrames
            val to = frame + steadyContextFrames
            val nearbyPeaks = peakFrames.filter { it in from..to }
            if (nearbyPeaks.size < MIN_PEAKS_FOR_BEAT) continue

            val intervalsMs = ArrayList<Float>()
            for (i in 1 until nearbyPeaks.size) {
                val intervalMs = (nearbyPeaks[i] - nearbyPeaks[i - 1]) * hopMs
                if (intervalMs in MIN_BEAT_MS..MAX_BEAT_MS) intervalsMs.add(intervalMs)
            }
            if (intervalsMs.size < MIN_PEAKS_FOR_BEAT - 1) continue

            val mean = intervalsMs.average().toFloat()
            if (mean <= 1e-3f) continue
            val variance = intervalsMs.sumOf { (it - mean).toDouble() * (it - mean) } / intervalsMs.size
            val coefficientOfVariation = (sqrt(variance) / mean).toFloat()

            if (coefficientOfVariation <= MAX_BEAT_CV) {
                val steadiness = (1f - coefficientOfVariation / MAX_BEAT_CV).coerceIn(0f, 1f)
                // A couple of accidentally regular intervals are weaker evidence than many: scale
                // confidence up with how many consistent intervals were actually found.
                val support = (intervalsMs.size.toFloat() / MIN_PEAKS_FOR_BEAT).coerceIn(1f, 2f) / 2f
                scores[frame] = (steadiness * (0.5f + 0.5f * support)).coerceIn(0f, 1f)
            }
        }
        return BeatEnvelope(hop, sampleRate, scores)
    }

    /** Unused arguments kept out of the hot path; documented rather than silently ignored. */

    /** Merges windows of the same kind into contiguous segments, tolerating a single gap window. */
    private fun mergeSegments(windows: List<AudioWindow>): List<Pair<Long, Long>> {
        if (windows.isEmpty()) return emptyList()
        val merged = ArrayList<Pair<Long, Long>>()
        var startMs = windows.first().startMs
        var endMs = windows.first().endMs

        for (index in 1 until windows.size) {
            val window = windows[index]
            val gapMs = window.startMs - endMs
            if (gapMs <= WINDOW_MS) {
                endMs = window.endMs
            } else {
                merged.add(startMs to endMs)
                startMs = window.startMs
                endMs = window.endMs
            }
        }
        merged.add(startMs to endMs)
        return merged
    }

    /** True when the samples look like they contain music at all, used to guard the auto-sync path. */
    fun hasMusic(analysis: MusicAnalysis): Boolean = analysis.musicSegments.isNotEmpty()

    /** Convenience for callers that only need the recommended learning interval. */
    fun bestLearningSegment(analysis: MusicAnalysis, minMs: Long = 1500L): Pair<Long, Long>? =
        analysis.bestLearningSegment(minMs)

    /** Exposed for tests: absolute value helper kept out of the hot loop. */
    internal fun magnitude(value: Float): Float = abs(value)
}
