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

    /**
     * Synthesizes a deterministic background radio song signal at time t (seconds).
     */
    private fun sampleBackgroundSongAt(t: Float): Float {
        // 4-bar chord progression changing every 2.0 seconds
        val bar = ((t / 2.0f).toInt()) % 4
        val rootHz = when (bar) {
            0 -> 110.0f // A2
            1 -> 87.31f // F2
            2 -> 130.81f // C3
            else -> 98.0f // G2
        }
        val beatEnv = (0.55f + 0.45f * sin(2.0 * PI * 2.0 * t).toFloat())
        val bass = 0.26f * sin(2.0 * PI * rootHz * t).toFloat()
        val fifth = 0.18f * sin(2.0 * PI * (rootHz * 1.5f) * t).toFloat()
        val octave = 0.14f * sin(2.0 * PI * (rootHz * 2.0f) * t).toFloat()
        val melody = 0.15f * sin(2.0 * PI * (rootHz * 4.0f) * t).toFloat() * beatEnv
        return (bass + fifth + octave + melody) * 0.75f
    }

    /**
     * Generates the clean reference audio file of the known song ("Known_Radio_Song_Reference.wav").
     */
    fun generateKnownSongReference(file: File, sampleRate: Int = 44100, durationSec: Float = 12.0f) {
        val totalSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate
            samples[i] = sampleBackgroundSongAt(t).coerceIn(-1.0f, 1.0f)
        }
        WavHelper.writeWav(file, samples, sampleRate, 1)
    }

    /**
     * Generates a forensic wiretap recording where the known song plays loudly in the room,
     * masking covert human speech underneath ("Cafe_Wiretap_With_Song.wav").
     */
    fun generateCafeWiretapWithSong(file: File, sampleRate: Int = 44100, durationSec: Float = 12.0f) {
        val totalSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(totalSamples)
        val random = Random(9001)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate
            val songInRoom = sampleBackgroundSongAt(t) * 0.68f
            val roomAmbience = (random.nextFloat() * 2f - 1f) * 0.02f

            var covertSpeech = 0f
            if ((t in 2.2..5.8) || (t in 7.2..10.8)) {
                val sylEnv = (0.5f + 0.5f * sin(2.0 * PI * 3.6 * t).toFloat())
                val f1 = sin(2.0 * PI * 680.0 * t).toFloat() * 0.40f
                val f2 = sin(2.0 * PI * 1620.0 * t).toFloat() * 0.38f
                val f3 = sin(2.0 * PI * 2720.0 * t).toFloat() * 0.25f
                covertSpeech = (f1 + f2 + f3) * sylEnv * 0.14f
            }

            samples[i] = (songInRoom + roomAmbience + covertSpeech).coerceIn(-1.0f, 1.0f)
        }
        WavHelper.writeWav(file, samples, sampleRate, 1)
    }

    /**
     * Generates a forensic scenario of an interrogation or wiretap inside an empty, tiled room / bathroom
     * with heavy reverberation (flutter echo and smearing of vocal sibilants).
     * Ideal test case for CepstralDeverbProcessor.
     */
    fun generateBathroomEchoCase(file: File, sampleRate: Int = 44100, durationSec: Float = 11.0f) {
        val totalSamples = (sampleRate * durationSec).toInt()
        val drySamples = FloatArray(totalSamples)
        val wetSamples = FloatArray(totalSamples)
        val random = Random(777)

        // 1. Dry speech synthesis with sharp sibilants and clear syllables
        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate
            if ((t in 1.2..4.8) || (t in 6.0..10.0)) {
                val syllableEnv = (0.5f + 0.5f * sin(2.0 * PI * 3.8 * t).toFloat())
                val f0 = sin(2.0 * PI * 135.0 * t).toFloat() * 0.35f
                val f1 = sin(2.0 * PI * 720.0 * t).toFloat() * 0.40f
                val f2 = sin(2.0 * PI * 1750.0 * t).toFloat() * 0.30f
                val f3 = sin(2.0 * PI * 2850.0 * t).toFloat() * 0.20f
                // Sibilant hiss ("s", "sh", "ch")
                val sibilantBurst = if (((t * 4f).toInt() % 3) == 0) (random.nextFloat() * 2f - 1f) * 0.35f else 0f
                drySamples[i] = (f0 + f1 + f2 + f3 + sibilantBurst) * syllableEnv * 0.28f
            }
        }

        // 2. Convolution with strong acoustic multipath reflections (tiled bathroom echo at ~28ms and ~56ms)
        val delay1 = (0.028f * sampleRate).toInt()
        val delay2 = (0.056f * sampleRate).toInt()
        val delay3 = (0.092f * sampleRate).toInt()
        val g1 = 0.62f
        val g2 = 0.38f
        val g3 = 0.22f

        for (i in 0 until totalSamples) {
            val dry = drySamples[i]
            val echo1 = if (i >= delay1) drySamples[i - delay1] * g1 else 0f
            val echo2 = if (i >= delay2) drySamples[i - delay2] * g2 else 0f
            val echo3 = if (i >= delay3) drySamples[i - delay3] * g3 else 0f
            val roomNoise = (random.nextFloat() * 2f - 1f) * 0.015f
            wetSamples[i] = (dry + echo1 + echo2 + echo3 + roomNoise).coerceIn(-1.0f, 1.0f)
        }

        WavHelper.writeWav(file, wetSamples, sampleRate, 1)
    }

    /**
     * Generates a forensic street / vehicle wiretap where non-stationary dynamic noise
     * (passing cars, doppler pitch sweep, honking, and sudden barking) masks speech.
     * Ideal test case for AdaptiveLmsFilter.
     */
    fun generateDynamicTrafficNoiseCase(file: File, sampleRate: Int = 44100, durationSec: Float = 12.0f) {
        val totalSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(totalSamples)
        val random = Random(888)

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate

            // 1. Dynamic non-stationary traffic whoosh with accelerating pitch sweep
            val speedMod = 1.0f + 0.6f * sin(2.0 * PI * 0.25 * t).toFloat()
            val engineFreq = 85.0f * speedMod
            val engineDrone = sin(2.0 * PI * engineFreq * t).toFloat() * 0.28f
            val exhaustRumble = sin(2.0 * PI * (engineFreq * 1.5f) * t).toFloat() * 0.18f

            // Dynamic tire friction whoosh modulated in volume
            val whooshVolume = (0.4f + 0.4f * sin(2.0 * PI * 0.3 * t).toFloat()).coerceIn(0.1f, 0.8f)
            val tireNoise = (random.nextFloat() * 2f - 1f) * whooshVolume * 0.16f

            // Sudden transient horn/bark at intervals
            val horn = if (t in 4.5..5.2) sin(2.0 * PI * 440.0 * t).toFloat() * 0.32f else 0f

            // 2. Target human speech buried under fluctuating street noise
            var targetSpeech = 0f
            if ((t in 1.8..4.2) || (t in 5.8..10.5)) {
                val syllableEnv = (0.5f + 0.5f * sin(2.0 * PI * 3.4 * t).toFloat())
                val f1 = sin(2.0 * PI * 650.0 * t).toFloat() * 0.38f
                val f2 = sin(2.0 * PI * 1550.0 * t).toFloat() * 0.32f
                val f3 = sin(2.0 * PI * 2600.0 * t).toFloat() * 0.22f
                targetSpeech = (f1 + f2 + f3) * syllableEnv * 0.22f
            }

            samples[i] = (engineDrone + exhaustRumble + tireNoise + horn + targetSpeech).coerceIn(-1.0f, 1.0f)
        }

        WavHelper.writeWav(file, samples, sampleRate, 1)
    }
}
