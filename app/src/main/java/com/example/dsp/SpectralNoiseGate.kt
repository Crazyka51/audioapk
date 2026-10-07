package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Frequency-domain / spectral gate noise reducer for stationary motor, fan, and room hum.
 */
class SpectralNoiseGate(
    private val fftSize: Int = 1024,
    private val hopSize: Int = 256,
    private val sampleRate: Int = 44100
) {
    private val fft = FftProcessor(fftSize)
    private val window = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    /**
     * Applies spectral subtraction noise reduction.
     * @param reductionLevel 0.0f (no reduction) to 1.0f (maximum noise attenuation).
     */
    fun process(samples: FloatArray, reductionLevel: Float = 0.75f): FloatArray {
        if (samples.size < fftSize || reductionLevel <= 0.01f) return samples.clone()

        val output = FloatArray(samples.size)
        val numBins = fftSize / 2

        // 1. Estimate initial noise floor profile from lowest energy frames
        val noiseProfile = FloatArray(numBins) { 0.005f }
        val numFrames = (samples.size - fftSize) / hopSize

        // Accumulate running spectrum
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val synthReal = FloatArray(fftSize)
        val synthImag = FloatArray(fftSize)

        // Overlap-Add Synthesis Buffer
        val synthesisWindowSum = FloatArray(samples.size)

        for (frame in 0 until numFrames) {
            val offset = frame * hopSize

            for (i in 0 until fftSize) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }

            fft.forward(real, imag)

            // Spectral attenuation
            for (bin in 0 until numBins) {
                val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                val noiseEst = noiseProfile[bin] * (1.0f + reductionLevel * 4.0f)
                val gain = if (mag > noiseEst) {
                    val snr = (mag - noiseEst) / mag
                    snr.coerceIn(0.05f, 1.0f)
                } else {
                    0.05f * (1.0f - reductionLevel)
                }

                real[bin] *= gain
                imag[bin] *= gain

                // Conjugate symmetric upper bins
                if (bin > 0) {
                    val mirrorBin = fftSize - bin
                    real[mirrorBin] = real[bin]
                    imag[mirrorBin] = -imag[bin]
                }
            }

            // Inverse FFT (using forward with swapped real/imag and 1/N scaling)
            for (i in 0 until fftSize) {
                synthReal[i] = imag[i]
                synthImag[i] = real[i]
            }
            fft.forward(synthReal, synthImag)

            val invScale = 1.0f / fftSize
            for (i in 0 until fftSize) {
                val reconstructed = (synthImag[i] * invScale) * window[i]
                output[offset + i] += reconstructed
                synthesisWindowSum[offset + i] += window[i] * window[i]
            }
        }

        // Normalize overlap-add
        for (i in output.indices) {
            val winSum = synthesisWindowSum[i]
            if (winSum > 0.001f) {
                output[i] = (output[i] / winSum).coerceIn(-1.0f, 1.0f)
            } else if (i in samples.indices) {
                output[i] = samples[i]
            }
        }

        return output
    }
}
