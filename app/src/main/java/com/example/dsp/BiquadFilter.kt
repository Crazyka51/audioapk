package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType {
    HIGH_PASS,
    LOW_PASS,
    BAND_PASS,
    NOTCH
}

/**
 * 2nd-order IIR Biquad filter using Robert Bristow-Johnson's Audio EQ Cookbook formulas.
 * Direct Form II Transposed structure for numerical stability and zero clicks.
 */
class BiquadFilter(
    private var type: FilterType = FilterType.HIGH_PASS,
    private var cutoffHz: Float = 300f,
    private var sampleRate: Float = 44100f,
    private var q: Float = 0.7071f // Butterworth Q
) {
    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // Filter state variables (Transposed Direct Form II)
    private var z1 = 0.0f
    private var z2 = 0.0f

    init {
        recalculateCoefficients()
    }

    fun configure(type: FilterType, cutoffHz: Float, sampleRate: Float = 44100f, q: Float = 0.7071f) {
        this.type = type
        this.cutoffHz = cutoffHz.coerceIn(20f, sampleRate * 0.49f)
        this.sampleRate = sampleRate
        this.q = q.coerceAtLeast(0.1f)
        recalculateCoefficients()
    }

    fun resetState() {
        z1 = 0.0f
        z2 = 0.0f
    }

    private fun recalculateCoefficients() {
        val w0 = 2.0 * PI * cutoffHz / sampleRate
        val cosW0 = cos(w0).toFloat()
        val sinW0 = sin(w0).toFloat()
        val alpha = sinW0 / (2.0f * q)

        var b0Temp = 0f
        var b1Temp = 0f
        var b2Temp = 0f
        var a0Temp = 1f
        var a1Temp = 0f
        var a2Temp = 0f

        when (type) {
            FilterType.HIGH_PASS -> {
                b0Temp = (1.0f + cosW0) / 2.0f
                b1Temp = -(1.0f + cosW0)
                b2Temp = (1.0f + cosW0) / 2.0f
                a0Temp = 1.0f + alpha
                a1Temp = -2.0f * cosW0
                a2Temp = 1.0f - alpha
            }
            FilterType.LOW_PASS -> {
                b0Temp = (1.0f - cosW0) / 2.0f
                b1Temp = 1.0f - cosW0
                b2Temp = (1.0f - cosW0) / 2.0f
                a0Temp = 1.0f + alpha
                a1Temp = -2.0f * cosW0
                a2Temp = 1.0f - alpha
            }
            FilterType.BAND_PASS -> {
                b0Temp = alpha
                b1Temp = 0.0f
                b2Temp = -alpha
                a0Temp = 1.0f + alpha
                a1Temp = -2.0f * cosW0
                a2Temp = 1.0f - alpha
            }
            FilterType.NOTCH -> {
                b0Temp = 1.0f
                b1Temp = -2.0f * cosW0
                b2Temp = 1.0f
                a0Temp = 1.0f + alpha
                a1Temp = -2.0f * cosW0
                a2Temp = 1.0f - alpha
            }
        }

        // Normalize by a0
        b0 = b0Temp / a0Temp
        b1 = b1Temp / a0Temp
        b2 = b2Temp / a0Temp
        a1 = a1Temp / a0Temp
        a2 = a2Temp / a0Temp
    }

    /**
     * Process a single audio sample through the filter.
     */
    fun processSample(input: Float): Float {
        val output = b0 * input + z1
        z1 = b1 * input - a1 * output + z2
        z2 = b2 * input - a2 * output
        return output
    }

    /**
     * Process an entire buffer in-place or returning a new buffer.
     */
    fun processBuffer(samples: FloatArray): FloatArray {
        val result = FloatArray(samples.size)
        resetState()
        for (i in samples.indices) {
            result[i] = processSample(samples[i])
        }
        return result
    }

    companion object {
        /**
         * Cascade High-Pass (300Hz) and Low-Pass (4000Hz) to create a sharp speech band-pass filter.
         */
        fun applySpeechBandpass(
            samples: FloatArray,
            highPassHz: Float = 300f,
            lowPassHz: Float = 4000f,
            sampleRate: Float = 44100f
        ): FloatArray {
            val hpf = BiquadFilter(FilterType.HIGH_PASS, highPassHz, sampleRate, 0.7071f)
            val lpf = BiquadFilter(FilterType.LOW_PASS, lowPassHz, sampleRate, 0.7071f)

            val hpFiltered = hpf.processBuffer(samples)
            return lpf.processBuffer(hpFiltered)
        }
    }
}
