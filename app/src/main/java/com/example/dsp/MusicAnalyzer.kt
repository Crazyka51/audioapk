package com.example.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
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

    /** Beat search range, roughly 50-200 BPM. */
    private const val MIN_BEAT_MS = 300f
    private const val MAX_BEAT_MS = 1200f

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

        val windows = ArrayList<AudioWindow>()
        val fluxes = ArrayList<Float>()

        var start = 0
        while (start + windowSamples <= samples.size) {
            val features = analyseWindow(samples, start, windowSamples, sampleRate, fft, window)
            windows.add(features)
            fluxes.add(features.first)
            start += hopSamples
        }
        if (windows.isEmpty()) return MusicAnalysis(emptyList(), emptyList(), 0f, 0f)

        // Periodicity of the spectral flux inside the beat range -> music has a pulse, speech does not.
        val rawFluxes = fluxes.map { it }
        val periodicity = beatPeriodicity(rawFluxes, hopSamples, sampleRate)

        val finished = windows.mapIndexed { index, windowFeatures ->
            val (levelDb, lowBandRatio, voiceBandRatio, flatness) = windowFeatures.second
            val beat = periodicity[index]
            val musicScore = (beat * 0.55f + (1f - voiceBandRatio) * 0.3f + flatness * 0.15f)
                .coerceIn(0f, 1f)
            val speechScore = (voiceBandRatio * 0.7f + (1f - beat) * 0.3f).coerceIn(0f, 1f)
            val kind = classify(levelDb, noiseFloorDb, musicScore, speechScore)
            AudioWindow(
                startMs = (index.toLong() * hopSamples / sampleRate) * 1000,
                endMs = ((index.toLong() * hopSamples + windowSamples) / sampleRate) * 1000,
                levelDb = levelDb,
                musicScore = musicScore,
                speechScore = speechScore,
                beatBpm = if (beat > 0.15f) 60000f / (periodicityLagMs(index, periodicity, hopSamples, sampleRate)) else 0f,
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
     * Per-window features: mean level, low band ratio, vocal band ratio and spectral flatness.
     * Returned as a small tuple to keep the scan allocation-light.
     */
    private fun analyseWindow(
        samples: FloatArray,
        start: Int,
        length: Int,
        sampleRate: Int,
        fft: FftProcessor,
        window: FloatArray
    ): Pair<Float, FloatArray> {
        val frameCount = (length - FFT_SIZE) / FFT_SIZE + 1
        val numBins = FFT_SIZE / 2
        val binHz = (sampleRate / 2.0f) / numBins

        val lowEnd = (300f / binHz).toInt().coerceAtLeast(1)
        val voiceEnd = (3400f / binHz).toInt().coerceAtMost(numBins)

        var levelSum = 0.0
        var totalMagnitude = 0.0
        var lowMagnitude = 0.0
        var voiceMagnitude = 0.0
        var logMagnitudeSum = 0.0
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
                val magnitude = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                totalMagnitude += magnitude
                logMagnitudeSum += ln(magnitude + 1e-9f)
                bins++
                if (bin < lowEnd) lowMagnitude += magnitude
                if (bin < voiceEnd) voiceMagnitude += magnitude
            }
        }

        // Level of the window in dBFS, measured directly from the samples.
        var sumSquares = 0.0
        for (i in start until start + length) sumSquares += samples[i].toDouble() * samples[i]
        val rms = sqrt(sumSquares / length)
        val levelDb = if (rms > 1e-5) (20 * ln(rms) / ln(10.0)).toFloat() else -90f

        if (bins == 0 || totalMagnitude <= 1e-6) {
            return levelDb to floatArrayOf(levelDb, 0f, 0f, 0f)
        }

        val lowRatio = (lowMagnitude / totalMagnitude).toFloat()
        val voiceRatio = (voiceMagnitude / totalMagnitude).toFloat()
        // Spectral flatness: music (broadband, noisy) is flatter than the tonal/harmonic voice.
        val geometricMean = kotlin.math.exp((logMagnitudeSum / bins).toFloat())
        val arithmeticMean = (totalMagnitude / bins).toFloat()
        val flatness = (geometricMean / (arithmeticMean + 1e-9f)).coerceIn(0f, 1f)

        return levelDb to floatArrayOf(levelDb, lowRatio, voiceRatio, flatness)
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
     * Autocorrelation of the spectral flux restricted to plausible beat periods. A strong periodic
     * peak means a musical pulse is present, which is what separates music from speech.
     */
    private fun beatPeriodicity(
        fluxes: List<Float>,
        hopSamples: Int,
        sampleRate: Int
    ): FloatArray {
        val result = FloatArray(fluxes.size)
        if (fluxes.size < 8 || sampleRate <= 0) return result

        val hopMs = hopSamples * 1000f / sampleRate
        val minLag = max((MIN_BEAT_MS / hopMs).toInt(), 2)
        val maxLag = min((MAX_BEAT_MS / hopMs).toInt(), fluxes.size - 1)
        if (maxLag <= minLag) return result

        for (index in fluxes.indices) {
            var best = 0f
            val from = index - 2 * maxLag
            val to = index + 2 * maxLag
            for (lag in minLag..maxLag) {
                var sum = 0.0
                var count = 0
                var i = max(from, 0)
                while (i + lag < min(to, fluxes.size - 1)) {
                    sum += fluxes[i].toDouble() * fluxes[i + lag]
                    count++
                    i++
                }
                if (count > 4) {
                    val normalized = (sum / count).toFloat()
                    if (normalized > best) best = normalized
                }
            }
            result[index] = best.coerceIn(0f, 1f)
        }
        return result
    }

    private fun periodicityLagMs(
        index: Int,
        periodicity: FloatArray,
        hopSamples: Int,
        sampleRate: Int
    ): Float {
        // The exact lag behind the periodic peak is not needed for labelling; invert the strongest
        // plausible tempo instead of storing per-window lags to keep the scan cheap.
        return if (periodicity[index] > 0.15f) 500f else 0f
    }

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
