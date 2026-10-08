package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Diagnostic and extraction results of Cepstral analysis.
 */
data class ReverberationMetrics(
    val detectedDelaySamples: Int,
    val detectedDelayMs: Float,
    val estimatedReflectionGain: Float,
    val quefrencyPeakProminence: Float,
    val roomAcousticProfile: String
)

/**
 * Cepstral De-reverberation Processor.
 *
 * Detective principle:
 * Audio wiretaps and surveillance recordings inside empty rooms, tiled bathrooms, corridors,
 * or stairwells suffer from severe room acoustics / multipath echo (reverberation).
 * Early wall reflections convolve with speech, smearing consonants, sibilants (s, z, sh), and
 * destroying forensic intelligibility.
 *
 * Mathematical method:
 * 1. Cepstral Analysis:
 *    Real Cepstrum C[n] = IFFT( ln( |FFT(x[n])| ) )
 *    In the quefrency domain (time-lag dimension), the convolutive room impulse response (RIR)
 *    separates into an additive peak corresponding to the echo delay time (t = 2 * distance / speed of sound).
 * 2. Delay & Reflection Estimation:
 *    Searches the quefrency lag window (typically 10 ms to 150 ms) for periodic echo spikes.
 * 3. Inverse Comb Filter Dereverberation:
 *    Applies an inverse acoustic comb filter:
 *    y[n] = x[n] - g * y[n - D]  (or feedforward inverse: y[n] = x[n] - g * x[n - D])
 *    with stability damping to prevent recursive blowups, effectively "drying" the sound
 *    and un-smearing speech formants.
 */
class CepstralDeverbProcessor(
    private val fftSize: Int = 2048,
    private val sampleRate: Int = 44100
) {
    private val fft = FftProcessor(fftSize)
    private val window = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    /**
     * Analyzes room acoustics by computing the average power cepstrum over representative frames.
     */
    fun analyzeReverberation(samples: FloatArray): ReverberationMetrics {
        if (samples.size < fftSize) {
            return ReverberationMetrics(
                detectedDelaySamples = 0,
                detectedDelayMs = 0f,
                estimatedReflectionGain = 0f,
                quefrencyPeakProminence = 0f,
                roomAcousticProfile = "Nahrávka příliš krátká"
            )
        }

        val hopSize = fftSize / 2
        val numFrames = ((samples.size - fftSize) / hopSize).coerceIn(1, 40)
        val avgCepstrum = FloatArray(fftSize)

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)

        for (frame in 0 until numFrames) {
            val offset = frame * hopSize
            for (i in 0 until fftSize) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }

            // 1. FFT
            fft.forward(real, imag)

            // 2. Log magnitude spectrum
            for (i in 0 until fftSize) {
                val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
                // Avoid log(0), floor at -80 dB
                val logMag = ln(max(mag, 1e-4f))
                real[i] = logMag
                imag[i] = 0f
            }

            // 3. IFFT (Real Cepstrum via swapped FFT)
            for (i in 0 until fftSize) {
                val temp = real[i]
                real[i] = imag[i]
                imag[i] = temp
            }
            fft.forward(real, imag)
            val invScale = 1.0f / fftSize

            for (i in 0 until fftSize) {
                avgCepstrum[i] += (imag[i] * invScale) / numFrames
            }
        }

        // Search quefrency window corresponding to 10 ms .. 120 ms
        val minLag = (sampleRate * 0.010f).toInt().coerceIn(10, fftSize / 2 - 1)
        val maxLag = (sampleRate * 0.120f).toInt().coerceIn(minLag + 1, fftSize / 2 - 1)

        var peakLag = minLag
        var peakVal = 0f
        var baselineMean = 0f
        var count = 0

        for (lag in minLag..maxLag) {
            val v = kotlin.math.abs(avgCepstrum[lag])
            baselineMean += v
            count++
            if (v > peakVal) {
                peakVal = v
                peakLag = lag
            }
        }

        val avgBaseline = if (count > 0) baselineMean / count else 1e-4f
        val prominence = if (avgBaseline > 1e-6f) peakVal / avgBaseline else 1.0f

        val delayMs = (peakLag.toFloat() / sampleRate) * 1000f

        // Estimated reflection gain: exponential decay approximation
        val reflectionGain = (peakVal * 0.8f).coerceIn(0.15f, 0.75f)

        val profile = when {
            prominence > 2.5f && delayMs < 30f -> "Kachličková koupelna / Chodba (Silný flutter echo)"
            prominence > 2.0f && delayMs in 30f..70f -> "Prázdná místnost / Kancelář (Střední dozvuk)"
            prominence > 1.8f && delayMs > 70f -> "Sklad / Velká hala (Dlouhá dozvuková ocasní křivka)"
            else -> "Mírná difúzní akustika (Slabá ozvěna)"
        }

        return ReverberationMetrics(
            detectedDelaySamples = peakLag,
            detectedDelayMs = delayMs,
            estimatedReflectionGain = reflectionGain,
            quefrencyPeakProminence = prominence,
            roomAcousticProfile = profile
        )
    }

    /**
     * Dries up and de-reverberates the audio using an Inverse Comb Filter with adaptive damping.
     *
     * @param samples Input raw or filtered audio.
     * @param deverbStrength 0.0f (bypass) to 1.0f (maximum reflection cancellation).
     * @param manualDelayMs Optional manual delay override; if <= 0f, auto-detected via cepstrum.
     * @param reflectionGain Optional manual reflection gain override; if <= 0f, auto-detected.
     */
    fun process(
        samples: FloatArray,
        deverbStrength: Float = 0.75f,
        manualDelayMs: Float = 0f,
        reflectionGain: Float = 0f
    ): FloatArray {
        if (samples.isEmpty() || deverbStrength <= 0.01f) return samples.clone()

        val metrics = if (manualDelayMs <= 0f || reflectionGain <= 0f) {
            analyzeReverberation(samples)
        } else {
            null
        }

        val delaySamples = if (manualDelayMs > 0f) {
            (manualDelayMs * sampleRate / 1000f).toInt().coerceIn(8, sampleRate)
        } else {
            metrics?.detectedDelaySamples ?: (sampleRate * 0.035f).toInt()
        }

        val baseGain = if (reflectionGain > 0f) {
            reflectionGain
        } else {
            metrics?.estimatedReflectionGain ?: 0.45f
        }

        // Scale by user deverbStrength (keep below 0.85 to avoid unstable comb divergence)
        val alpha = (baseGain * deverbStrength).coerceIn(0.0f, 0.82f)

        val output = FloatArray(samples.size)

        // 1. Direct inverse comb filtering: y[n] = x[n] - alpha * x[n - D]
        // Using feedforward subtraction with a secondary feedback suppression stage
        for (i in samples.indices) {
            val delayedSample = if (i >= delaySamples) samples[i - delaySamples] else 0f
            // Feedforward cancellation of first strong wall bounce
            val dryEstimate = samples[i] - alpha * delayedSample

            // Secondary harmonic suppression (suppresses 2nd bounce at 2*D)
            val secondDelay = delaySamples * 2
            val secondDelayedSample = if (i >= secondDelay) samples[i - secondDelay] else 0f
            val cleaned = dryEstimate + (alpha * alpha * 0.45f) * secondDelayedSample

            output[i] = cleaned.coerceIn(-1.0f, 1.0f)
        }

        // 2. High-frequency formant sparkle recovery
        // Room reflections heavily smudge speech transients; apply a soft high-shelf tilt to restore intelligibility
        var prevIn = 0f
        var prevOut = 0f
        val tiltCoeff = 0.18f * deverbStrength

        for (i in output.indices) {
            val current = output[i]
            // High-boost pre-emphasis
            val highBoosted = current + tiltCoeff * (current - prevIn)
            prevIn = current
            output[i] = highBoosted.coerceIn(-1.0f, 1.0f)
        }

        return output
    }
}
