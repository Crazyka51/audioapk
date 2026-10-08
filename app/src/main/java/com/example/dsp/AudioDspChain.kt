package com.example.dsp

import com.example.model.DspConfig

/**
 * Shared, stateless DSP processing chain.
 *
 * This is the single source of truth for how the forensic effect rack is applied to a PCM
 * buffer. It is used both by:
 *  - [com.example.data.AudioDetectiveRepository.processAndSaveFilteredWav] for non-destructive
 *    export of a cleaned working copy, and
 *  - [com.example.audio.ForensicAudioPlayer] for the live in-app effect preview during playback.
 *
 * Keeping a single implementation guarantees that what the analyst hears during preview is
 * exactly what gets written to disk on export (critical for forensic reproducibility).
 *
 * Processing order (must stay identical to the historical repository order):
 *  0. STFT Voice Separation
 *  1. High-Pass filter
 *  2. Low-Pass filter
 *  3. Spectral Noise Gate
 *  3B. Adaptive LMS Filter
 *  3C. Cepstral De-reverberation
 *  4. Whisper Dynamic Boost
 */
object AudioDspChain {

    /**
     * Result of applying the DSP chain.
     *
     * @param samples normalized PCM output in [-1.0, 1.0].
     * @param sampleRate sample rate of the input (unchanged by the chain).
     * @param appliedFilters human readable labels of every filter that was actually applied.
     */
    data class ChainResult(
        val samples: FloatArray,
        val sampleRate: Int,
        val appliedFilters: List<String>
    )

    /**
     * Applies the full forensic DSP rack to [input] according to [config].
     *
     * The [input] array is never mutated; a working copy is created internally.
     */
    fun apply(input: FloatArray, sampleRate: Int, config: DspConfig): ChainResult {
        var samples = input.clone()
        val sampleRateF = sampleRate.toFloat()
        val appliedFilters = mutableListOf<String>()

        // 0. STFT Voice Separation (Formant & Wiener Spectral Masking)
        if (config.voiceSeparationEnabled) {
            val separator = VoiceSeparator(fftSize = 1024, hopSize = 256, sampleRate = sampleRate)
            samples = separator.separate(
                samples = samples,
                mode = config.voiceSeparationMode,
                separationStrength = config.voiceSeparationStrength,
                formantBoostDb = config.vocalFormantBoostDb
            )
            appliedFilters.add(
                "Voice Separation: ${config.voiceSeparationMode.titleCz} " +
                    "(${(config.voiceSeparationStrength * 100).toInt()}%)"
            )
        }

        // 1. High-Pass Filter (Voice isolation)
        if (config.highPassEnabled || config.speechBandpassEnabled) {
            val cutoff = if (config.speechBandpassEnabled) 300f else config.highPassCutoffHz
            val hpf = BiquadFilter(FilterType.HIGH_PASS, cutoff, sampleRateF)
            samples = hpf.processBuffer(samples)
            appliedFilters.add("High-Pass Filter (${cutoff.toInt()} Hz)")
        }

        // 2. Low-Pass Filter
        if (config.lowPassEnabled || config.speechBandpassEnabled) {
            val cutoff = if (config.speechBandpassEnabled) 4000f else config.lowPassCutoffHz
            val lpf = BiquadFilter(FilterType.LOW_PASS, cutoff, sampleRateF)
            samples = lpf.processBuffer(samples)
            appliedFilters.add("Low-Pass Filter (${cutoff.toInt()} Hz)")
        }

        // 3. Spectral Noise Reduction (Fan / Hum gate)
        if (config.noiseReductionEnabled) {
            val noiseGate = SpectralNoiseGate(fftSize = 1024, hopSize = 256, sampleRate = sampleRate)
            samples = noiseGate.process(samples, config.noiseReductionLevel)
            appliedFilters.add("Spectral Noise Gate (${(config.noiseReductionLevel * 100).toInt()}%)")
        }

        // 3B. Adaptive LMS Filter (Dynamic Non-Stationary Noise, traffic, barking)
        if (config.adaptiveLmsEnabled) {
            val lmsFilter = AdaptiveLmsFilter()
            samples = lmsFilter.process(
                samples = samples,
                filterIntensity = config.adaptiveLmsIntensity,
                speechPreservation = config.adaptiveLmsSpeechPreserve
            )
            appliedFilters.add("Adaptive LMS Filter (${(config.adaptiveLmsIntensity * 100).toInt()}%)")
        }

        // 3C. Cepstral De-reverberation (Inverse Comb Filter for empty room / bathroom echo)
        if (config.deverbEnabled) {
            val deverbProcessor = CepstralDeverbProcessor(fftSize = 2048, sampleRate = sampleRate)
            samples = deverbProcessor.process(
                samples = samples,
                deverbStrength = config.deverbStrength,
                manualDelayMs = config.deverbManualDelayMs
            )
            appliedFilters.add("Cepstral De-reverberation (${(config.deverbStrength * 100).toInt()}%)")
        }

        // 4. Whisper Dynamic Boost
        if (config.whisperBoostEnabled) {
            val booster = WhisperCompressor(
                boostGainDb = config.whisperBoostGainDb,
                thresholdDb = config.whisperThresholdDb,
                sampleRate = sampleRateF
            )
            samples = booster.process(samples)
            appliedFilters.add("Whisper Dynamic Boost (+${config.whisperBoostGainDb.toInt()} dB)")
        }

        return ChainResult(samples = samples, sampleRate = sampleRate, appliedFilters = appliedFilters)
    }
}
