package com.example.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class LearnedSourceType(val labelCz: String) {
    AB_INTERVAL_SAMPLE("Vzorek z A-B úseku nahrávky"),
    REFERENCE_AUDIO_FILE("Referenční soubor (známá písnička / ruch)")
}

/**
 * Holds a learned spectral fingerprint & dynamic STFT reference matrix
 * (either captured from an A-B time slice or loaded from a known song file).
 */
data class LearnedSpectralProfile(
    val id: String,
    val sourceName: String,
    val sourceType: LearnedSourceType,
    val durationMs: Long,
    val sampleRate: Int,
    val fftSize: Int,
    val meanMagnitudeBins: FloatArray,         // Stationary 512-bin spectral print
    val peakMagnitudeBins: FloatArray,         // Peak envelope per bin
    val dynamicStftColumns: List<FloatArray>,  // Time-varying STFT magnitude frames (for known song cancellation)
    val spectrogramPreview: SpectrogramResult, // Visual spectrogram of the reference/sample
    val dominantFreqHz: Float,
    val averageDb: Float,
    val detectedOffsetMs: Long = 0L            // Auto-correlated time offset in target recording
)

/**
 * Forensic Reference Audio Canceller & Spectral Noise Print Subtractor.
 *
 * Supports two workflows requested by forensic analysts:
 * 1. "Získat vzorek z úseku (A-B) a aplikovat na celou nahrávku" (2-step Noise/Music Print learn & subtract).
 * 2. "Odečtení známé písničky (Reference Track Cancellation)": Takes an external/library audio file
 *    of a known song playing in the background, synchronizes it via energy-envelope cross-correlation,
 *    models the room transfer function in STFT domain, and subtracts the song's time-varying spectrogram
 *    from the forensic recording.
 */
class ReferenceAudioCanceller(
    private val fftSize: Int = 1024,
    private val hopSize: Int = 256,
    private val sampleRate: Int = 44100
) {
    private val fft = FftProcessor(fftSize)
    private val window = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    /**
     * Step 1A: Learns a spectral profile from a selected [startMs..endMs] interval of the current recording.
     */
    fun learnFromInterval(
        samples: FloatArray,
        sampleRate: Int,
        startMs: Long,
        endMs: Long,
        trackTitle: String
    ): LearnedSpectralProfile? {
        if (samples.isEmpty() || endMs <= startMs) return null

        val startIdx = ((startMs / 1000.0) * sampleRate).toInt().coerceIn(0, samples.size - 1)
        val endIdx = ((endMs / 1000.0) * sampleRate).toInt().coerceIn(startIdx + 1, samples.size)
        if (endIdx - startIdx < fftSize) return null

        val slice = samples.copyOfRange(startIdx, endIdx)
        val durationSec = String.format("%.1fs – %.1fs", startMs / 1000f, endMs / 1000f)

        return buildProfileFromSamples(
            slice = slice,
            sampleRate = sampleRate,
            sourceName = "$trackTitle ($durationSec)",
            sourceType = LearnedSourceType.AB_INTERVAL_SAMPLE,
            keepDynamicFrames = true,
            targetSamplesForAlignment = null
        )
    }

    /**
     * Step 1B: Learns a full time-varying STFT reference profile from a known song / reference audio file,
     * and automatically calculates the best time alignment offset against the target recording.
     */
    fun learnFromReferenceTrack(
        referenceSamples: FloatArray,
        referenceSampleRate: Int,
        referenceTitle: String,
        targetSamples: FloatArray? = null
    ): LearnedSpectralProfile? {
        if (referenceSamples.size < fftSize) return null

        return buildProfileFromSamples(
            slice = referenceSamples,
            sampleRate = referenceSampleRate,
            sourceName = referenceTitle,
            sourceType = LearnedSourceType.REFERENCE_AUDIO_FILE,
            keepDynamicFrames = true,
            targetSamplesForAlignment = targetSamples
        )
    }

    private fun buildProfileFromSamples(
        slice: FloatArray,
        sampleRate: Int,
        sourceName: String,
        sourceType: LearnedSourceType,
        keepDynamicFrames: Boolean,
        targetSamplesForAlignment: FloatArray?
    ): LearnedSpectralProfile {
        val numBins = fftSize / 2
        val meanBins = FloatArray(numBins)
        val peakBins = FloatArray(numBins)
        val dynamicColumns = ArrayList<FloatArray>()

        // Limit stored dynamic frames so even very long songs stay memory-light
        // (we store at hop = 512 for dynamic matching, up to ~8000 frames)
        val refHop = 512
        val totalFrames = (slice.size - fftSize).coerceAtLeast(0) / refHop + 1
        val maxDynamicFrames = 6000

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        var frameCount = 0

        var frameIdx = 0
        while (frameIdx * refHop + fftSize <= slice.size) {
            val offset = frameIdx * refHop
            for (i in 0 until fftSize) {
                real[i] = slice[offset + i] * window[i]
                imag[i] = 0f
            }
            fft.forward(real, imag)

            val magCol = FloatArray(numBins)
            for (b in 0 until numBins) {
                val mag = sqrt(real[b] * real[b] + imag[b] * imag[b])
                magCol[b] = mag
                meanBins[b] += mag
                if (mag > peakBins[b]) {
                    peakBins[b] = mag
                }
            }

            if (keepDynamicFrames && dynamicColumns.size < maxDynamicFrames) {
                dynamicColumns.add(magCol)
            }

            frameCount++
            frameIdx++
        }

        if (frameCount > 0) {
            for (b in 0 until numBins) {
                meanBins[b] = meanBins[b] / frameCount
            }
        }

        // Find dominant frequency & average dB
        var maxBin = 1
        var maxVal = 0f
        var sumEnergy = 0.0
        for (b in 1 until numBins) {
            sumEnergy += meanBins[b]
            if (meanBins[b] > maxVal) {
                maxVal = meanBins[b]
                maxBin = b
            }
        }
        val nyquist = sampleRate / 2.0f
        val dominantHz = (maxBin.toFloat() / numBins) * nyquist
        val avgMag = (sumEnergy / numBins.coerceAtLeast(1)).toFloat() / (fftSize / 2)
        val avgDb = if (avgMag > 1e-5f) (20.0 * log10(avgMag.toDouble())).toFloat().coerceIn(-90f, 0f) else -75f

        val previewSpec = fft.computeSpectrogram(
            samples = slice,
            sampleRate = sampleRate,
            maxColumns = 180,
            hopSize = 512
        )

        val bestOffsetMs = if (targetSamplesForAlignment != null && sourceType == LearnedSourceType.REFERENCE_AUDIO_FILE) {
            findBestEnvelopeAlignmentMs(targetSamplesForAlignment, slice, sampleRate)
        } else 0L

        val durationMs = ((slice.size.toDouble() / sampleRate) * 1000.0).toLong()

        return LearnedSpectralProfile(
            id = "${sourceName}_${System.currentTimeMillis()}",
            sourceName = sourceName,
            sourceType = sourceType,
            durationMs = durationMs,
            sampleRate = sampleRate,
            fftSize = fftSize,
            meanMagnitudeBins = meanBins,
            peakMagnitudeBins = peakBins,
            dynamicStftColumns = dynamicColumns,
            spectrogramPreview = previewSpec,
            dominantFreqHz = dominantHz,
            averageDb = avgDb,
            detectedOffsetMs = bestOffsetMs
        )
    }

    /**
     * Step 2: Applies the learned spectral profile / reference song cancellation to the entire recording.
     *
     * @param targetSamples Full recording PCM samples
     * @param profile Learned spectral fingerprint / reference song STFT profile
     * @param attenuationStrength 0.1f to 1.0f (how aggressively to remove the song/noise)
     * @param spectralMatchGain 0.5f to 2.5f (scales the reference profile to match room volume)
     * @param timeOffsetMs Offset in ms where the reference song starts in the recording
     * @param protectVoiceFormants If true, prevents over-hollowing of speech formants (300–3400 Hz)
     */
    fun applyProfileSubtraction(
        targetSamples: FloatArray,
        profile: LearnedSpectralProfile,
        attenuationStrength: Float = 0.88f,
        spectralMatchGain: Float = 1.2f,
        timeOffsetMs: Long = profile.detectedOffsetMs,
        protectVoiceFormants: Boolean = true
    ): FloatArray {
        if (targetSamples.size < fftSize) return targetSamples.clone()

        val numBins = fftSize / 2
        val numFrames = (targetSamples.size - fftSize) / hopSize
        if (numFrames <= 0) return targetSamples.clone()

        val output = FloatArray(targetSamples.size)
        val windowSum = FloatArray(targetSamples.size)

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val synthReal = FloatArray(fftSize)
        val synthImag = FloatArray(fftSize)

        val strength = attenuationStrength.coerceIn(0.1f, 1.0f)
        val overSubtractionFactor = (1.0f + strength * 2.4f) * spectralMatchGain
        val spectralFloor = (1.0f - strength * 0.94f).coerceIn(0.03f, 0.5f)

        val nyquist = sampleRate / 2.0f
        val smoothedGain = FloatArray(numBins) { 1.0f }
        val refHopSamples = 512
        val offsetSamples = ((timeOffsetMs / 1000.0) * sampleRate).toInt()

        // Estimate automatic volume ratio between target recording and reference song
        val autoScale = estimateReferenceToTargetScale(targetSamples, profile)

        for (frame in 0 until numFrames) {
            val samplePos = frame * hopSize

            for (i in 0 until fftSize) {
                real[i] = targetSamples[samplePos + i] * window[i]
                imag[i] = 0f
            }
            fft.forward(real, imag)

            // Determine corresponding frame in dynamic reference song (if active)
            val relativeSample = samplePos - offsetSamples
            val refCol: FloatArray? = if (
                profile.sourceType == LearnedSourceType.REFERENCE_AUDIO_FILE &&
                profile.dynamicStftColumns.isNotEmpty() &&
                relativeSample >= 0
            ) {
                // Loop reference song if recording is longer than the song (or match direct position)
                val refFrameIdx = (relativeSample / refHopSamples) % profile.dynamicStftColumns.size
                profile.dynamicStftColumns[refFrameIdx]
            } else null

            for (bin in 0 until numBins) {
                val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin]) + 1e-9f

                // Reference magnitude at this time-frequency bin:
                // Combine dynamic time-aligned song frame (70%) + learned mean/peak print (30%)
                val refMag = if (refCol != null) {
                    (0.72f * refCol[bin] + 0.28f * profile.meanMagnitudeBins[bin]) * autoScale
                } else {
                    (0.65f * profile.meanMagnitudeBins[bin] + 0.35f * profile.peakMagnitudeBins[bin]) * autoScale
                }

                val subtractedMag = mag - refMag * overSubtractionFactor
                var gain = if (subtractedMag > mag * spectralFloor) {
                    (subtractedMag / mag).coerceIn(spectralFloor, 1.0f)
                } else {
                    spectralFloor
                }

                // Optional Vocal Formant Protection: preserve sudden speech transients in 300–3400 Hz
                if (protectVoiceFormants) {
                    val freqHz = (bin.toFloat() / numBins) * nyquist
                    if (freqHz in 300f..3400f && mag > refMag * 1.65f) {
                        gain = max(gain, 0.65f)
                    }
                }

                // Temporal smoothing to prevent musical noise
                smoothedGain[bin] = 0.55f * smoothedGain[bin] + 0.45f * gain
                val finalGain = smoothedGain[bin]

                real[bin] *= finalGain
                imag[bin] *= finalGain

                if (bin > 0) {
                    val mirror = fftSize - bin
                    real[mirror] = real[bin]
                    imag[mirror] = -imag[bin]
                }
            }

            // Inverse FFT
            for (i in 0 until fftSize) {
                synthReal[i] = imag[i]
                synthImag[i] = real[i]
            }
            fft.forward(synthReal, synthImag)

            val invScale = 1.0f / fftSize
            for (i in 0 until fftSize) {
                output[samplePos + i] += (synthImag[i] * invScale) * window[i]
                windowSum[samplePos + i] += window[i] * window[i]
            }
        }

        // Normalize overlap-add
        var maxPeak = 0.01f
        for (i in output.indices) {
            val w = windowSum[i]
            if (w > 1e-4f) {
                output[i] = (output[i] / w).coerceIn(-1.0f, 1.0f)
            } else {
                output[i] = targetSamples[i]
            }
            val a = abs(output[i])
            if (a > maxPeak) maxPeak = a
        }

        // Gentle level makeup if cleaned signal became quiet
        if (maxPeak in 0.01f..0.65f) {
            val makeup = (0.75f / maxPeak).coerceAtMost(3.0f)
            for (i in output.indices) {
                output[i] = (output[i] * makeup).coerceIn(-1.0f, 1.0f)
            }
        }

        return output
    }

    /**
     * Cross-correlates downsampled energy envelopes (100 Hz envelope rate) to find where
     * the reference song aligns inside the target recording.
     */
    private fun findBestEnvelopeAlignmentMs(
        targetSamples: FloatArray,
        referenceSamples: FloatArray,
        sampleRate: Int
    ): Long {
        val envBlock = (sampleRate / 50).coerceAtLeast(256) // 20 ms blocks
        val targetBlocks = targetSamples.size / envBlock
        val refBlocks = (referenceSamples.size / envBlock).coerceAtMost(250) // first 5 seconds of song
        if (targetBlocks <= 4 || refBlocks <= 4) return 0L

        val targetEnv = FloatArray(targetBlocks) { b ->
            var sum = 0f
            val base = b * envBlock
            for (i in 0 until envBlock) sum += abs(targetSamples[base + i])
            sum / envBlock
        }
        val refEnv = FloatArray(refBlocks) { b ->
            var sum = 0f
            val base = b * envBlock
            for (i in 0 until envBlock) sum += abs(referenceSamples[base + i])
            sum / envBlock
        }

        val maxLag = (targetBlocks - refBlocks).coerceIn(0, 500) // Search up to 10s offset rapidly
        var bestLag = 0
        var bestCorr = -1f

        for (lag in 0..maxLag) {
            var corr = 0f
            for (j in 0 until min(refBlocks, targetBlocks - lag)) {
                corr += targetEnv[lag + j] * refEnv[j]
            }
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }

        return ((bestLag.toDouble() * envBlock / sampleRate) * 1000.0).toLong()
    }

    private fun estimateReferenceToTargetScale(
        targetSamples: FloatArray,
        profile: LearnedSpectralProfile
    ): Float {
        var refAvg = 0f
        for (m in profile.meanMagnitudeBins) refAvg += m
        refAvg /= profile.meanMagnitudeBins.size.coerceAtLeast(1)
        if (refAvg <= 1e-6f) return 1.0f

        // Sample a few frames from target to match energy order of magnitude
        val step = max(targetSamples.size / 12, fftSize)
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        var targetAvg = 0f
        var count = 0

        var idx = 0
        while (idx + fftSize <= targetSamples.size && count < 12) {
            for (i in 0 until fftSize) {
                real[i] = targetSamples[idx + i] * window[i]
                imag[i] = 0f
            }
            fft.forward(real, imag)
            var frameSum = 0f
            val numBins = fftSize / 2
            for (b in 0 until numBins) {
                frameSum += sqrt(real[b] * real[b] + imag[b] * imag[b])
            }
            targetAvg += frameSum / numBins
            count++
            idx += step
        }

        if (count == 0) return 1.0f
        targetAvg /= count
        return (targetAvg / refAvg).coerceIn(0.25f, 3.0f)
    }
}
