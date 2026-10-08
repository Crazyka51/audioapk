package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class VoiceSeparationMode(
    val titleCz: String,
    val subtitleCz: String,
    val fMinHz: Float,
    val fMaxHz: Float
) {
    ISOLATE_VOICE(
        titleCz = "Čistá lidská řeč",
        subtitleCz = "Izoluje pásmo hlasivek a formantů (85 Hz – 3 800 Hz), potlačí šum a motory",
        fMinHz = 85f,
        fMaxHz = 3800f
    ),
    ISOLATE_WHISPER(
        titleCz = "Detekce a separace šepotu",
        subtitleCz = "Zvýrazní neznělé aspirace a vyšší formanty F2/F3 (500 Hz – 4 200 Hz)",
        fMinHz = 500f,
        fMaxHz = 4200f
    ),
    MALE_SPEAKER(
        titleCz = "Mužský hlas (F0: 85–180 Hz)",
        subtitleCz = "Optimalizováno pro hlubší základní tón a hrudní rezonanci",
        fMinHz = 80f,
        fMaxHz = 3200f
    ),
    FEMALE_SPEAKER(
        titleCz = "Ženský / vyšší hlas (F0: 165–265 Hz)",
        subtitleCz = "Optimalizováno pro vyšší základní tón a čistotu sykavek",
        fMinHz = 160f,
        fMaxHz = 4500f
    ),
    BACKGROUND_RESIDUAL(
        titleCz = "Pouze pozadí (Inverzní forenzní maska)",
        subtitleCz = "Potlačí řeč a ponechá pouze ruchy v pozadí (analýza prostředí, motorů, hodin)",
        fMinHz = 85f,
        fMaxHz = 3800f
    )
}

data class VoiceSeparationStats(
    val vocalEnergyRatio: Float,       // 0.0 to 1.0
    val backgroundNoiseRatio: Float,   // 0.0 to 1.0
    val dominantFormantHz: Float,
    val estimatedSnrImprovementDb: Float
)

/**
 * STFT (Short-Time Fourier Transform) Spectral Voice Separator.
 * Uses Wiener-type spectral masking combined with human vocal formant (F0, F1, F2, F3)
 * frequency weighting to isolate human speech or whispers from background noise.
 */
class VoiceSeparator(
    private val fftSize: Int = 1024,
    private val hopSize: Int = 256,
    private val sampleRate: Int = 44100
) {
    private val fft = FftProcessor(fftSize)
    private val window = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    /**
     * Separates voice (or background residual) from input PCM samples using STFT spectral masking.
     *
     * @param samples Normalized PCM float samples [-1.0..1.0]
     * @param mode Target separation profile (Voice, Whisper, Male, Female, or Background Residual)
     * @param separationStrength 0.0f (dry) to 1.0f (maximum isolation)
     * @param formantBoostDb Additional gain in dB for F1/F2/F3 vocal formants (0..18 dB)
     */
    fun separate(
        samples: FloatArray,
        mode: VoiceSeparationMode = VoiceSeparationMode.ISOLATE_VOICE,
        separationStrength: Float = 0.85f,
        formantBoostDb: Float = 8.0f
    ): FloatArray {
        if (samples.size < fftSize) return samples.clone()

        val numBins = fftSize / 2
        val numFrames = (samples.size - fftSize) / hopSize
        if (numFrames <= 0) return samples.clone()

        val nyquist = sampleRate / 2.0f
        val strength = separationStrength.coerceIn(0f, 1f)
        val formantLinearBoost = 10.0f.pow(formantBoostDb / 20.0f)

        // 1. Estimate stationary noise floor per bin using minimum statistics across frames
        val noiseProfile = estimateNoiseFloor(samples, numFrames, numBins)

        // 2. Precompute vocal formant weighting curve across FFT bins
        val vocalWeight = FloatArray(numBins) { bin ->
            val freq = (bin.toFloat() / numBins) * nyquist
            computeVocalFormantEnvelope(freq, mode, formantLinearBoost)
        }

        val output = FloatArray(samples.size)
        val windowSum = FloatArray(samples.size)

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val synthReal = FloatArray(fftSize)
        val synthImag = FloatArray(fftSize)

        // Smooth mask state across frames to avoid musical noise artifacts
        val smoothedMask = FloatArray(numBins) { 1.0f }
        val temporalSmooth = 0.65f

        for (frame in 0 until numFrames) {
            val offset = frame * hopSize

            for (i in 0 until fftSize) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }

            fft.forward(real, imag)

            for (bin in 0 until numBins) {
                val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin]) + 1e-9f
                val noiseMag = noiseProfile[bin] * (1.0f + strength * 3.5f)

                // Wiener spectral mask based on a-posteriori SNR
                val power = mag * mag
                val noisePower = noiseMag * noiseMag
                val wienerGain = (power / (power + noisePower + 1e-12f)).coerceIn(0.02f, 1.0f)

                // Combine Wiener gain with vocal formant envelope
                val targetVocalMask = (wienerGain * vocalWeight[bin]).coerceIn(0.01f, 3.5f)

                val finalMask = if (mode == VoiceSeparationMode.BACKGROUND_RESIDUAL) {
                    // Inverse mask: attenuate speech bins and keep background
                    val speechPresence = (wienerGain * (vocalWeight[bin] / formantLinearBoost.coerceAtLeast(1f))).coerceIn(0f, 1f)
                    (1.0f - speechPresence * strength * 0.92f).coerceIn(0.05f, 1.2f)
                } else {
                    // Blend dry (1.0) and separated vocal mask by separationStrength
                    (1.0f - strength) + strength * targetVocalMask
                }

                smoothedMask[bin] = temporalSmooth * smoothedMask[bin] + (1.0f - temporalSmooth) * finalMask
                val gain = smoothedMask[bin]

                real[bin] *= gain
                imag[bin] *= gain

                if (bin > 0) {
                    val mirror = fftSize - bin
                    real[mirror] = real[bin]
                    imag[mirror] = -imag[bin]
                }
            }

            // Inverse FFT via conjugate symmetry
            for (i in 0 until fftSize) {
                synthReal[i] = imag[i]
                synthImag[i] = real[i]
            }
            fft.forward(synthReal, synthImag)

            val invScale = 1.0f / fftSize
            for (i in 0 until fftSize) {
                val sampleOut = (synthImag[i] * invScale) * window[i]
                output[offset + i] += sampleOut
                windowSum[offset + i] += window[i] * window[i]
            }
        }

        // Normalize overlap-add and apply peak normalization if isolating whispers
        var maxPeak = 0.01f
        for (i in output.indices) {
            val w = windowSum[i]
            if (w > 1e-4f) {
                output[i] = output[i] / w
            } else {
                output[i] = samples[i]
            }
            val absVal = kotlin.math.abs(output[i])
            if (absVal > maxPeak) maxPeak = absVal
        }

        // Gentle normalization so isolated speech/whisper is clearly audible
        val targetPeak = if (mode == VoiceSeparationMode.ISOLATE_WHISPER) 0.88f else 0.82f
        val normGain = if (maxPeak < targetPeak && maxPeak > 0.005f) {
            (targetPeak / maxPeak).coerceAtMost(4.0f)
        } else 1.0f

        for (i in output.indices) {
            output[i] = (output[i] * normGain).coerceIn(-1.0f, 1.0f)
        }

        return output
    }

    /**
     * Analyzes the spectral distribution of the audio to report Voice vs. Background ratios.
     */
    fun analyzeVoiceMetrics(samples: FloatArray): VoiceSeparationStats {
        if (samples.size < fftSize) {
            return VoiceSeparationStats(0.5f, 0.5f, 1200f, 0f)
        }

        val numBins = fftSize / 2
        val nyquist = sampleRate / 2.0f
        val step = max((samples.size - fftSize) / 40, hopSize)

        var vocalEnergy = 0.0
        var totalEnergy = 1e-9
        val binEnergyAccum = FloatArray(numBins)

        var offset = 0
        while (offset + fftSize <= samples.size) {
            val real = FloatArray(fftSize)
            val imag = FloatArray(fftSize)
            for (i in 0 until fftSize) {
                real[i] = samples[offset + i] * window[i]
            }
            fft.forward(real, imag)

            for (bin in 1 until numBins) {
                val e = (real[bin] * real[bin] + imag[bin] * imag[bin]).toDouble()
                val freq = (bin.toFloat() / numBins) * nyquist
                totalEnergy += e
                binEnergyAccum[bin] += e.toFloat()
                if (freq in 85f..3800f) {
                    vocalEnergy += e
                }
            }
            offset += step
        }

        // Find dominant formant peak inside speech band (300 Hz - 3500 Hz)
        var maxFormantBin = 10
        var maxFormantVal = 0f
        for (bin in 1 until numBins) {
            val freq = (bin.toFloat() / numBins) * nyquist
            if (freq in 300f..3500f && binEnergyAccum[bin] > maxFormantVal) {
                maxFormantVal = binEnergyAccum[bin]
                maxFormantBin = bin
            }
        }

        val vocalRatio = (vocalEnergy / totalEnergy).toFloat().coerceIn(0.05f, 0.95f)
        val dominantHz = (maxFormantBin.toFloat() / numBins) * nyquist
        val snrGainDb = 6.0f + (1.0f - vocalRatio) * 14.0f

        return VoiceSeparationStats(
            vocalEnergyRatio = vocalRatio,
            backgroundNoiseRatio = (1.0f - vocalRatio).coerceIn(0.05f, 0.95f),
            dominantFormantHz = dominantHz,
            estimatedSnrImprovementDb = snrGainDb
        )
    }

    private fun estimateNoiseFloor(samples: FloatArray, numFrames: Int, numBins: Int): FloatArray {
        val noiseProfile = FloatArray(numBins) { Float.MAX_VALUE }
        val accumProfile = FloatArray(numBins)
        val stride = max(numFrames / 30, 1)
        var count = 0

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)

        for (frame in 0 until numFrames step stride) {
            val offset = frame * hopSize
            for (i in 0 until fftSize) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }
            fft.forward(real, imag)

            for (bin in 0 until numBins) {
                val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                if (mag < noiseProfile[bin]) {
                    noiseProfile[bin] = mag
                }
                accumProfile[bin] += mag
            }
            count++
        }

        if (count > 0) {
            for (bin in 0 until numBins) {
                val avg = accumProfile[bin] / count
                // Blend minimum and average for robust stationary noise estimate
                noiseProfile[bin] = 0.7f * noiseProfile[bin] + 0.3f * avg * 0.35f
            }
        }
        return noiseProfile
    }

    private fun computeVocalFormantEnvelope(
        freqHz: Float,
        mode: VoiceSeparationMode,
        formantLinearBoost: Float
    ): Float {
        if (freqHz < mode.fMinHz * 0.7f) {
            // Steep roll-off for sub-bass rumble / 50-60Hz AC hum
            val ratio = (freqHz / (mode.fMinHz * 0.7f)).coerceIn(0f, 1f)
            return ratio.pow(3) * 0.04f
        }
        if (freqHz > mode.fMaxHz * 1.25f) {
            // Roll-off for high-frequency hiss
            val excess = (freqHz - mode.fMaxHz) / (mode.fMaxHz * 0.5f)
            return exp(-excess * 2.5f).coerceIn(0.03f, 1.0f)
        }

        // Gaussian bumps at human speech formant centers:
        // F1 (~500-750 Hz), F2 (~1400-1800 Hz), F3 (~2500-3100 Hz)
        val f1Peak = gaussianBell(freqHz, centerHz = 650f, widthHz = 350f)
        val f2Peak = gaussianBell(freqHz, centerHz = 1650f, widthHz = 550f)
        val f3Peak = gaussianBell(freqHz, centerHz = 2750f, widthHz = 650f)

        val formantFactor = when (mode) {
            VoiceSeparationMode.ISOLATE_WHISPER -> 0.3f * f1Peak + 0.9f * f2Peak + 1.0f * f3Peak
            VoiceSeparationMode.MALE_SPEAKER -> 1.0f * f1Peak + 0.8f * f2Peak + 0.5f * f3Peak
            VoiceSeparationMode.FEMALE_SPEAKER -> 0.6f * f1Peak + 1.0f * f2Peak + 0.9f * f3Peak
            else -> 0.8f * f1Peak + 1.0f * f2Peak + 0.75f * f3Peak
        }

        return 1.0f + (formantLinearBoost - 1.0f) * formantFactor.coerceIn(0f, 1f)
    }

    private fun gaussianBell(freq: Float, centerHz: Float, widthHz: Float): Float {
        val diff = (freq - centerHz) / widthHz
        return exp(-0.5f * diff * diff)
    }
}
