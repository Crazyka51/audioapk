package com.example

import com.example.dsp.BiquadFilter
import com.example.dsp.FftProcessor
import com.example.dsp.FilterType
import com.example.dsp.WhisperCompressor
import org.junit.Assert.assertEquals
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
}
