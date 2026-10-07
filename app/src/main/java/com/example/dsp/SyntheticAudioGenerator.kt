package com.example.dsp

import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object SyntheticAudioGenerator {

    /**
     * Generates a realistic forensic scenario: Hidden whisper buried under heavy low-frequency motor hum.
     */
    fun generateWhisperCase(file: File, sampleRate: Int = 44100, durationSec: Float = 12.5f) {
        val totalSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(totalSamples)
        val random = Random(42) // Fixed seed for reproducibility

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate

            // 1. Heavy low-frequency motor/AC hum (60Hz, 120Hz, 180Hz)
            val hum60 = 0.35f * sin(2.0 * PI * 60.0 * t).toFloat()
            val hum120 = 0.22f * sin(2.0 * PI * 120.0 * t).toFloat()
            val hum180 = 0.12f * sin(2.0 * PI * 180.0 * t).toFloat()

            // 2. High frequency CRT/sensor whine (4500Hz)
            val whine = 0.05f * sin(2.0 * PI * 4500.0 * t).toFloat()

            // 3. Pinkish broadband ambient room noise
            val noise = (random.nextFloat() * 2f - 1f) * 0.06f

            // 4. Hidden Whisper Speech pattern (Formants in 500-2500Hz band at time windows 2.0s-5.0s, 6.5s-9.5s, 10.2s-11.8s)
            var speech = 0.0f
            val inWhisperWindow = (t in 2.0..5.0) || (t in 6.5..9.5) || (t in 10.2..11.8)

            if (inWhisperWindow) {
                // Syllable rhythm modulation (~4 Hz envelope)
                val syllableEnv = (0.5f + 0.5f * sin(2.0 * PI * 3.8 * t).toFloat()).coerceIn(0f, 1f)

                // Vocal formants (whispered speech consists of unvoiced noise shaped by vocal tract resonances)
                val formantF1 = sin(2.0 * PI * 620.0 * t).toFloat() * 0.4f
                val formantF2 = sin(2.0 * PI * 1450.0 * t).toFloat() * 0.35f
                val formantF3 = sin(2.0 * PI * 2400.0 * t).toFloat() * 0.25f

                // Whisper aspiration noise
                val aspiration = (random.nextFloat() * 2f - 1f) * 0.5f

                // Faint whisper amplitude: -30 dB (approx 0.035 amplitude)
                speech = (formantF1 + formantF2 + formantF3 + aspiration) * syllableEnv * 0.045f
            }

            samples[i] = (hum60 + hum120 + hum180 + whine + noise + speech).coerceIn(-1.0f, 1.0f)
        }

        WavHelper.writeWav(file, samples, sampleRate, 1)
    }

    /**
     * Generates a forensic scenario with steady ventilator drone and muffled speech.
     */
    fun generateVentilatorDroneCase(file: File, sampleRate: Int = 44100, durationSec: Float = 10.0f) {
        val totalSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(totalSamples)
        val random = Random(1337)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate

            // Constant 220Hz ventilator drone with harmonics
            val drone220 = 0.30f * sin(2.0 * PI * 220.0 * t).toFloat()
            val drone440 = 0.15f * sin(2.0 * PI * 440.0 * t).toFloat()
            val subRumble = 0.20f * sin(2.0 * PI * 45.0 * t).toFloat()

            // Room ambient noise
            val noise = (random.nextFloat() * 2f - 1f) * 0.05f

            // Conversational voice in the 800-3000Hz band (active between 1.5s - 8.5s)
            var speech = 0.0f
            if (t in 1.5..8.5) {
                val mod = (0.5f + 0.5f * sin(2.0 * PI * 2.5 * t).toFloat())
                val voice1 = sin(2.0 * PI * 850.0 * t).toFloat() * 0.4f
                val voice2 = sin(2.0 * PI * 1750.0 * t).toFloat() * 0.3f
                val voice3 = sin(2.0 * PI * 2800.0 * t).toFloat() * 0.2f
                speech = (voice1 + voice2 + voice3) * mod * 0.10f
            }

            samples[i] = (drone220 + drone440 + subRumble + noise + speech).coerceIn(-1.0f, 1.0f)
        }

        WavHelper.writeWav(file, samples, sampleRate, 1)
    }

    /**
     * Generates standard interview recording with uneven audio dynamics.
     */
    fun generateInterviewCase(file: File, sampleRate: Int = 44100, durationSec: Float = 14.0f) {
        val totalSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(totalSamples)
        val random = Random(2026)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate

            // Light room HVAC noise (<100Hz)
            val hvac = 0.10f * sin(2.0 * PI * 50.0 * t).toFloat()
            val hiss = (random.nextFloat() * 2f - 1f) * 0.02f

            // Speaker 1: Loud investigator voice (0.0s - 4.5s)
            var speaker1 = 0f
            if (t in 0.5..4.2) {
                val env = (0.5f + 0.5f * sin(2.0 * PI * 3.0 * t).toFloat())
                speaker1 = (sin(2.0 * PI * 400.0 * t).toFloat() + sin(2.0 * PI * 1100.0 * t).toFloat()) * env * 0.45f
            }

            // Speaker 2: Very faint suspect whisper / mumbling (5.0s - 12.5s)
            var speaker2 = 0f
            if (t in 5.2..12.5) {
                val env = (0.5f + 0.5f * sin(2.0 * PI * 3.5 * t).toFloat())
                val whisper = (random.nextFloat() * 2f - 1f) * 0.4f
                val tones = (sin(2.0 * PI * 750.0 * t).toFloat() + sin(2.0 * PI * 1600.0 * t).toFloat()) * 0.3f
                speaker2 = (whisper + tones) * env * 0.035f // Very low amplitude (-29 dB)
            }

            samples[i] = (hvac + hiss + speaker1 + speaker2).coerceIn(-1.0f, 1.0f)
        }

        WavHelper.writeWav(file, samples, sampleRate, 1)
    }
}
