package com.example

import com.example.dsp.BiquadFilter
import com.example.dsp.FftProcessor
import com.example.dsp.FilterType
import com.example.dsp.ReferenceAudioCanceller
import com.example.dsp.VoiceSeparationMode
import com.example.dsp.VoiceSeparator
import com.example.dsp.WhisperCompressor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DspUnitTest {

    @Test
    fun testFftProcessor_detectsSinePeak() {
        val fftSize = 1024
        val sampleRate = 44100
        val fft = FftProcessor(fftSize)

        // Generate 1000 Hz pure sine wave
        val samples = FloatArray(fftSize) { i ->
            val t = i.toFloat() / sampleRate
            sin(2.0 * PI * 1000.0 * t).toFloat()
        }

        val dbSpectrum = fft.computeMagnitudesDb(samples)
        assertEquals(fftSize / 2, dbSpectrum.size)

        // Target bin for 1000 Hz: (1000 / 22050) * 512 ~ 23
        var maxBin = 0
        var maxDb = -100f
        for (i in dbSpectrum.indices) {
            if (dbSpectrum[i] > maxDb) {
                maxDb = dbSpectrum[i]
                maxBin = i
            }
        }

        val detectedFreq = (maxBin.toFloat() / (fftSize / 2)) * (sampleRate / 2.0f)
        assertTrue("Detected frequency should be near 1000 Hz (got $detectedFreq Hz)", Math.abs(detectedFreq - 1000f) < 60f)
    }

    @Test
    fun testHighPassFilter_attenuatesLowFrequencies() {
        val sampleRate = 44100f
        val hpf = BiquadFilter(FilterType.HIGH_PASS, 300f, sampleRate)

        // 60 Hz low frequency hum
        val hum60Hz = FloatArray(1000) { i ->
            val t = i.toFloat() / sampleRate
            sin(2.0 * PI * 60.0 * t).toFloat()
        }

        val filtered = hpf.processBuffer(hum60Hz)

        // Check steady state attenuation
        var maxRaw = 0f
        var maxFiltered = 0f
        for (i in 500 until 1000) {
            maxRaw = maxOf(maxRaw, Math.abs(hum60Hz[i]))
            maxFiltered = maxOf(maxFiltered, Math.abs(filtered[i]))
        }

        assertTrue("60 Hz hum should be heavily attenuated by 300 Hz HPF (raw: $maxRaw, filtered: $maxFiltered)", maxFiltered < maxRaw * 0.35f)
    }

    @Test
    fun testWhisperCompressor_boostsLowLevelSignals() {
        val compressor = WhisperCompressor(boostGainDb = 14f, thresholdDb = -32f)

        // Faint signal at -40 dB (amplitude ~ 0.01)
        val faintSamples = FloatArray(500) { 0.01f }
        val boosted = compressor.process(faintSamples)

        assertTrue("Quiet whisper signal should be boosted (input: 0.01, output: ${boosted.last()})", boosted.last() > 0.025f)
    }

    @Test
    fun testVoiceSeparator_isolatesVocalFormants() {
        val sampleRate = 44100
        val separator = VoiceSeparator(fftSize = 1024, hopSize = 256, sampleRate = sampleRate)

        // Generate 4096 samples with 50 Hz low hum + 1600 Hz vocal formant F2
        val mixed = FloatArray(4096) { i ->
            val t = i.toFloat() / sampleRate
            val hum50Hz = 0.45f * sin(2.0 * PI * 50.0 * t).toFloat()
            val formant1600Hz = 0.25f * sin(2.0 * PI * 1600.0 * t).toFloat()
            hum50Hz + formant1600Hz
        }

        val statsBefore = separator.analyzeVoiceMetrics(mixed)
        val separated = separator.separate(
            samples = mixed,
            mode = VoiceSeparationMode.ISOLATE_VOICE,
            separationStrength = 0.90f,
            formantBoostDb = 10.0f
        )
        val statsAfter = separator.analyzeVoiceMetrics(separated)

        assertTrue(
            "Vocal energy ratio should increase after STFT voice separation (before: ${statsBefore.vocalEnergyRatio}, after: ${statsAfter.vocalEnergyRatio})",
            statsAfter.vocalEnergyRatio > statsBefore.vocalEnergyRatio
        )
    }

    @Test
    fun testReferenceAudioCanceller_subtractsKnownSongAndPreservesSpeech() {
        val sampleRate = 44100
        val fftSize = 1024
        val canceller = ReferenceAudioCanceller(fftSize = fftSize, hopSize = 256, sampleRate = sampleRate)
        val fft = FftProcessor(fftSize)

        // Known background song: 110 Hz bass + 220 Hz harmony
        val referenceSong = FloatArray(8192) { i ->
            val t = i.toFloat() / sampleRate
            0.45f * sin(2.0 * PI * 110.0 * t).toFloat() + 0.30f * sin(2.0 * PI * 220.0 * t).toFloat()
        }

        // Mixed recording: known song + covert human voice formant at 1650 Hz
        val mixedRecording = FloatArray(8192) { i ->
            val t = i.toFloat() / sampleRate
            val song = referenceSong[i] * 0.8f
            val voice = 0.22f * sin(2.0 * PI * 1650.0 * t).toFloat()
            song + voice
        }

        val profile = canceller.learnFromReferenceTrack(
            referenceSamples = referenceSong,
            referenceSampleRate = sampleRate,
            referenceTitle = "Known_Song",
            targetSamples = mixedRecording
        )
        assertNotNull("Learned spectral profile should not be null", profile)

        val cleaned = canceller.applyProfileSubtraction(
            targetSamples = mixedRecording,
            profile = profile!!,
            attenuationStrength = 0.90f,
            spectralMatchGain = 1.0f,
            timeOffsetMs = 0L,
            protectVoiceFormants = true
        )

        // Measure spectrum before and after at 2048 sample offset (steady state)
        val beforeDb = fft.computeMagnitudesDb(mixedRecording, offset = 2048)
        val afterDb = fft.computeMagnitudesDb(cleaned, offset = 2048)

        val nyquist = sampleRate / 2.0f
        val numBins = fftSize / 2
        val bin110 = ((110f / nyquist) * numBins).toInt().coerceIn(0, numBins - 1)
        val bin1650 = ((1650f / nyquist) * numBins).toInt().coerceIn(0, numBins - 1)

        val songDbBefore = beforeDb[bin110]
        val songDbAfter = afterDb[bin110]
        val voiceDbAfter = afterDb[bin1650]

        // 1. The 110 Hz song component should be noticeably attenuated
        assertTrue(
            "Known song frequency (110 Hz) should be attenuated (before: $songDbBefore dB, after: $songDbAfter dB)",
            songDbAfter < songDbBefore - 3.0f
        )

        // 2. The 1650 Hz voice formant should be preserved at an audible level (> -50 dBFS)
        assertTrue(
            "Voice formant (1650 Hz) should remain preserved and audible (got: $voiceDbAfter dB)",
            voiceDbAfter > -50.0f
        )
    }
}
