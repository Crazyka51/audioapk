package com.example.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Forensic Whisper Enhancer and Dynamic Range Expander.
 * Performs upward expansion of quiet signals (whispers) and soft peak limiting
 * for loud bursts without destructive clipping.
 */
class WhisperCompressor(
    var boostGainDb: Float = 14.0f,        // Low-level boost amount (+3dB to +30dB)
    var thresholdDb: Float = -32.0f,       // Whisper threshold (below this level, gain is applied)
    var attackMs: Float = 5.0f,
    var releaseMs: Float = 120.0f,
    var sampleRate: Float = 44100f
) {
    private var envelope = 0.0f
    private val attackCoef: Float
    private val releaseCoef: Float

    init {
        attackCoef = exp(-1.0f / (sampleRate * (attackMs / 1000f)))
        releaseCoef = exp(-1.0f / (sampleRate * (releaseMs / 1000f)))
    }

    fun reset() {
        envelope = 0.0f
    }

    /**
     * Process audio samples to bring out faint whispers while limiting transients.
     */
    fun process(samples: FloatArray): FloatArray {
        val result = FloatArray(samples.size)
        reset()

        val maxBoostLinear = 10.0f.pow(boostGainDb / 20.0f)
        val threshLinear = 10.0f.pow(thresholdDb / 20.0f)

        for (i in samples.indices) {
            val input = samples[i]
            val absInput = abs(input)

            // Envelope follower
            envelope = if (absInput > envelope) {
                attackCoef * envelope + (1.0f - attackCoef) * absInput
            } else {
                releaseCoef * envelope + (1.0f - releaseCoef) * absInput
            }

            // Upward gain calculation:
            // When envelope is small (whisper), we apply high gain.
            // When envelope is large, gain smoothly tapers to 1.0 (0 dB).
            val normalizedLevel = (envelope / threshLinear).coerceIn(0.001f, 1.0f)
            // Logarithmic taper
            val gainFactor = 1.0f + (maxBoostLinear - 1.0f) * (1.0f - normalizedLevel).pow(1.5f)

            val amplified = input * gainFactor

            // Soft-knee limiting via hyperbolic tangent to preserve warmth and avoid harsh digital clipping
            result[i] = if (abs(amplified) > 0.85f) {
                0.85f + 0.15f * tanh((amplified - 0.85f) / 0.15f)
            } else if (abs(amplified) < -0.85f) {
                -0.85f + 0.15f * tanh((amplified + 0.85f) / 0.15f)
            } else {
                amplified
            }.coerceIn(-1.0f, 1.0f)
        }

        return result
    }
}
