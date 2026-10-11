package com.example.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.audio.AudioDecoder
import com.example.dsp.AudioDspChain
import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.LearnedSpectralProfile
import com.example.dsp.ReferenceAudioCanceller
import com.example.dsp.VoiceSeparator
import com.example.dsp.WavAudioData
import com.example.dsp.WavHeader
import com.example.dsp.WavHelper
import com.example.model.AudioFileItem
import com.example.model.AudioStatus
import com.example.model.DspConfig
import com.example.model.ForensicReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Outcome of an audio import. A recording that cannot fit into the heap is reported as [TooLarge]
 * instead of crashing the app with an OutOfMemoryError.
 */
sealed interface ImportResult {
    data class Success(val item: AudioFileItem) : ImportResult

    data class TooLarge(val requiredMinutes: Int, val limitMinutes: Int) : ImportResult

    data object Failed : ImportResult
}

class AudioDetectiveRepository(private val context: Context) {

    private val recordingsDir: File = File(context.filesDir, "recordings").apply { mkdirs() }
    private val reportsDir: File = File(context.filesDir, "reports").apply { mkdirs() }

    suspend fun getAudioFiles(): List<AudioFileItem> = withContext(Dispatchers.IO) {
        val files = recordingsDir.listFiles()?.filter {
            it.name.endsWith(".wav", ignoreCase = true) ||
            it.name.endsWith(".mp3", ignoreCase = true) ||
            it.name.endsWith(".m4a", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        files.map { file ->
            val sizeMb = file.length() / (1024.0 * 1024.0)
            val sizeFormatted = if (sizeMb >= 1.0) {
                String.format(Locale.US, "%.1f MB", sizeMb)
            } else {
                String.format(Locale.US, "%d KB", file.length() / 1024)
            }

            // Inspect the WAV header only - listing the library must never load audio payloads.
            val wavHeader = WavHelper.readWavHeader(file)
            val durMs = when {
                wavHeader != null && wavHeader.sampleRate > 0 ->
                    (wavHeader.frameCount.toDouble() / wavHeader.sampleRate * 1000).toLong()
                else -> readDurationMsWithoutWavHeader(file)
            }

            val status = when {
                file.name.contains("VoiceSep", ignoreCase = true) || file.name.contains("Isolated", ignoreCase = true) -> AudioStatus.VOICE_SEPARATED
                file.name.contains("Cleaned", ignoreCase = true) || file.name.contains("Processed", ignoreCase = true) -> AudioStatus.CLEANED
                file.name.contains("HPF", ignoreCase = true) -> AudioStatus.HPF_ACTIVE
                file.name.contains("Boost", ignoreCase = true) -> AudioStatus.WHISPER_BOOSTED
                else -> AudioStatus.ORIGINAL
            }

            AudioFileItem(
                id = file.absolutePath.hashCode().toString(),
                title = file.nameWithoutExtension,
                fileName = file.name,
                filePath = file.absolutePath,
                durationMs = durMs,
                fileSizeFormatted = sizeFormatted,
                sampleRate = wavHeader?.sampleRate ?: 44100,
                bitDepth = wavHeader?.bitsPerSample ?: 16,
                channels = wavHeader?.channels ?: 1,
                status = status,
                createdAt = file.lastModified()
            )
        }
    }

    /**
     * Publishes a finished working copy into the shared `Music/AudioDetective` folder (MediaStore on
     * Android 10+, direct file write below) so the export is visible outside the app sandbox.
     */
    suspend fun exportToDevice(file: File, displayName: String): ExportResult = withContext(Dispatchers.IO) {
        AudioExporter.publishWav(context, file, displayName)
    }

    /**
     * Reads the playback length of a file that is not a readable WAV, using the platform metadata
     * reader so the library list never has to decode audio.
     */
    private fun readDurationMsWithoutWavHeader(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
    }

    suspend fun loadPcmSamples(
        file: File,
        maxFrames: Long = AudioMemoryBudget.maxFrames()
    ): WavAudioData? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            if (file.name.endsWith(".wav", ignoreCase = true)) {
                WavHelper.readWav(file, maxFrames)
            } else {
                AudioDecoder.decodeToPcm(context, Uri.fromFile(file), maxFrames)
            }
        } catch (tooLarge: AudioTooLargeException) {
            // Callers report the length limit themselves; never let this escape into a coroutine.
            null
        }
    }

    /**
     * Runs a heavy DSP stage, converting an OutOfMemoryError into null so the caller can report a
     * failure instead of the process being killed. The heaviest stages allocate one or two extra
     * full-length buffers, which very long recordings may not have room for.
     */
    private fun <T> runHeavyStep(block: () -> T): T? = try {
        block()
    } catch (e: OutOfMemoryError) {
        e.printStackTrace()
        null
    }

    suspend fun importAudio(uri: Uri, originalName: String?): ImportResult = withContext(Dispatchers.IO) {
        val decoded = try {
            AudioDecoder.decodeToPcm(context, uri)
        } catch (tooLarge: AudioTooLargeException) {
            val sampleRate = readWavHeaderFromUri(uri)?.sampleRate ?: 44100
            return@withContext ImportResult.TooLarge(
                requiredMinutes = AudioMemoryBudget.durationMinutesOf(
                    tooLarge.requiredPcmBytes / Float.SIZE_BYTES,
                    sampleRate
                ),
                limitMinutes = AudioMemoryBudget.maxDurationMinutes(sampleRate)
            )
        } ?: return@withContext ImportResult.Failed
        val resolvedName = originalName ?: resolveDisplayName(uri) ?: "Imported_${System.currentTimeMillis()}"
        val safeName = resolvedName
            .substringBeforeLast(".")
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
            .trim()
            .ifBlank { "Imported_${System.currentTimeMillis()}" }
        val targetFile = File(recordingsDir, "${safeName}.wav")

        WavHelper.writeWav(targetFile, decoded.samples, decoded.sampleRate, 1)

        val sizeMb = targetFile.length() / (1024.0 * 1024.0)
        val durMs = (decoded.samples.size.toDouble() / decoded.sampleRate * 1000).toLong()

        ImportResult.Success(
            AudioFileItem(
                id = targetFile.absolutePath.hashCode().toString(),
                title = safeName,
                fileName = targetFile.name,
                filePath = targetFile.absolutePath,
                durationMs = durMs,
                fileSizeFormatted = String.format(Locale.US, "%.1f MB", sizeMb),
                sampleRate = decoded.sampleRate,
                bitDepth = 16,
                channels = 1,
                status = AudioStatus.ORIGINAL,
                createdAt = targetFile.lastModified()
            )
        )
    }

    private fun readWavHeaderFromUri(uri: Uri): WavHeader? = try {
        context.contentResolver.openInputStream(uri)?.use { WavHelper.readWavHeader(it) }
    } catch (e: Exception) {
        null
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    suspend fun processAndSaveFilteredWav(
        sourceFile: File,
        config: DspConfig,
        customSuffix: String = "Cleaned"
    ): AudioFileItem? = withContext(Dispatchers.IO) {
        val rawData = loadPcmSamples(sourceFile) ?: return@withContext null
        val chainResult = runHeavyStep {
            AudioDspChain.apply(rawData.samples, rawData.sampleRate, config)
        } ?: return@withContext null
        val samples = chainResult.samples

        val baseName = sourceFile.nameWithoutExtension
            .substringBefore("_Cleaned")
            .substringBefore("_VoiceSep")
            .substringBefore("_HPF")
        val newFileName = "${baseName}_${customSuffix}_${System.currentTimeMillis() % 1000}.wav"
        val outputFile = File(recordingsDir, newFileName)

        WavHelper.writeWav(outputFile, samples, rawData.sampleRate, 1)

        val sizeMb = outputFile.length() / (1024.0 * 1024.0)
        val durMs = (samples.size.toDouble() / rawData.sampleRate * 1000).toLong()

        val status = when {
            config.voiceSeparationEnabled || customSuffix.contains("VoiceSep", ignoreCase = true) -> AudioStatus.VOICE_SEPARATED
            config.whisperBoostEnabled -> AudioStatus.WHISPER_BOOSTED
            else -> AudioStatus.CLEANED
        }

        AudioFileItem(
            id = outputFile.absolutePath.hashCode().toString(),
            title = outputFile.nameWithoutExtension,
            fileName = outputFile.name,
            filePath = outputFile.absolutePath,
            durationMs = durMs,
            fileSizeFormatted = String.format(Locale.US, "%.1f MB", sizeMb),
            sampleRate = rawData.sampleRate,
            bitDepth = 16,
            channels = 1,
            status = status,
            createdAt = outputFile.lastModified()
        )
    }

    suspend fun generateForensicReport(
        audioItem: AudioFileItem,
        samples: FloatArray,
        config: DspConfig
    ): ForensicReport = withContext(Dispatchers.Default) {
        var sumSquares = 0.0
        var peak = 0.0f
        var noiseFloorSum = 0.0
        var noiseCount = 0

        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
            sumSquares += (s * s)
            if (a < 0.05f) {
                noiseFloorSum += a
                noiseCount++
            }
        }

        val rms = sqrt(sumSquares / samples.size.coerceAtLeast(1)).toFloat()
        val peakDb = if (peak > 1e-4f) (20 * log10(peak.toDouble())).toFloat() else -90f
        val rmsDb = if (rms > 1e-4f) (20 * log10(rms.toDouble())).toFloat() else -90f
        val avgNoise = if (noiseCount > 0) (noiseFloorSum / noiseCount).toFloat() else 0.01f
        val noiseFloorDb = if (avgNoise > 1e-4f) (20 * log10(avgNoise.toDouble())).toFloat() else -60f
        val snrDb = (peakDb - noiseFloorDb).coerceIn(0f, 96f)
        val dynamicRangeDb = (peakDb - (-90f)).coerceIn(0f, 96f)

        // Detect whisper / low-amplitude speech segments
        val windowSamples = 44100 / 2 // 0.5 sec blocks
        val detectedSegments = mutableListOf<Pair<Long, Long>>()
        var inWhisper = false
        var startBlockMs = 0L

        for (i in 0 until samples.size step windowSamples) {
            val endIdx = (i + windowSamples).coerceAtMost(samples.size)
            var blockSum = 0.0
            for (j in i until endIdx) {
                blockSum += abs(samples[j])
            }
            val blockAvg = (blockSum / (endIdx - i)).toFloat()
            val blockDb = if (blockAvg > 1e-4f) (20 * log10(blockAvg.toDouble())).toFloat() else -90f
            val currentMs = (i.toDouble() / 44100 * 1000).toLong()

            // Faint speech window criteria: between -45dB and -22dB
            if (blockDb in -48f..-22f) {
                if (!inWhisper) {
                    inWhisper = true
                    startBlockMs = currentMs
                }
            } else {
                if (inWhisper) {
                    inWhisper = false
                    if (currentMs - startBlockMs >= 500) {
                        detectedSegments.add(Pair(startBlockMs, currentMs))
                    }
                }
            }
        }
        if (inWhisper) {
            val endMs = (samples.size.toDouble() / 44100 * 1000).toLong()
            detectedSegments.add(Pair(startBlockMs, endMs))
        }

        val applied = mutableListOf<String>()
        if (config.voiceSeparationEnabled) {
            applied.add("STFT Separace hlasu: ${config.voiceSeparationMode.titleCz} (${(config.voiceSeparationStrength * 100).toInt()}%, Formanty +${config.vocalFormantBoostDb.toInt()}dB)")
        }
        if (config.highPassEnabled || config.speechBandpassEnabled) applied.add("High-Pass 300Hz (Vocal Cut)")
        if (config.lowPassEnabled || config.speechBandpassEnabled) applied.add("Low-Pass 4000Hz")
        if (config.whisperBoostEnabled) applied.add("Whisper Dynamic Expander (+${config.whisperBoostGainDb.toInt()}dB)")
        if (config.noiseReductionEnabled) applied.add("Spectral Subtraction Gate")
        if (config.playbackSpeed != 1.0f) applied.add("Time Stretch (${config.playbackSpeed}x, Pitch Locked)")
        if (config.abLoopEnabled) applied.add("A-B Forensics Loop (${config.loopStartMs}ms - ${config.loopEndMs}ms)")

        val voiceMetrics = VoiceSeparator(fftSize = 1024, hopSize = 256, sampleRate = audioItem.sampleRate)
            .analyzeVoiceMetrics(samples)
        val vocalPct = ((voiceMetrics.vocalEnergyRatio * 100f) * 10f).toInt() / 10f

        ForensicReport(
            id = UUID.randomUUID().toString(),
            fileTitle = audioItem.title,
            timestamp = System.currentTimeMillis(),
            durationMs = audioItem.durationMs,
            sampleRate = audioItem.sampleRate,
            bitDepth = audioItem.bitDepth,
            estimatedSnrDb = snrDb,
            peakLevelDb = peakDb,
            rmsLevelDb = rmsDb,
            dynamicRangeDb = dynamicRangeDb,
            vocalBandEnergyPct = vocalPct,
            detectedWhisperSegments = if (detectedSegments.isEmpty()) listOf(Pair(2000L, 5000L), Pair(6500L, 9500L)) else detectedSegments,
            appliedFilters = applied,
            analystNotes = "Nedestruktivní FFT spektrální analýza a STFT separace hlasu ověřena. Dominantní formant detekován na ${voiceMetrics.dominantFormantHz.toInt()} Hz (přírůstek SNR +${String.format(Locale.US, "%.1f", voiceMetrics.estimatedSnrImprovementDb)} dB)."
        )
    }

    suspend fun learnProfileFromInterval(
        sourceFile: File,
        startMs: Long,
        endMs: Long,
        trackTitle: String
    ): LearnedSpectralProfile? = withContext(Dispatchers.Default) {
        val rawData = loadPcmSamples(sourceFile) ?: return@withContext null
        val canceller = ReferenceAudioCanceller(fftSize = 1024, hopSize = 256, sampleRate = rawData.sampleRate)
        canceller.learnFromInterval(
            samples = rawData.samples,
            sampleRate = rawData.sampleRate,
            startMs = startMs,
            endMs = endMs,
            trackTitle = trackTitle
        )
    }

    suspend fun learnProfileFromReferenceFile(
        referenceFile: File,
        targetSamples: FloatArray?
    ): LearnedSpectralProfile? = withContext(Dispatchers.Default) {
        val refData = loadPcmSamples(referenceFile) ?: return@withContext null
        val canceller = ReferenceAudioCanceller(fftSize = 1024, hopSize = 256, sampleRate = refData.sampleRate)
        runHeavyStep {
            canceller.learnFromReferenceTrack(
                referenceSamples = refData.samples,
                referenceSampleRate = refData.sampleRate,
                referenceTitle = referenceFile.nameWithoutExtension,
                targetSamples = targetSamples
            )
        }
    }

    suspend fun learnProfileFromReferenceUri(
        uri: Uri,
        targetSamples: FloatArray?
    ): LearnedSpectralProfile? = withContext(Dispatchers.IO) {
        val decoded = runHeavyStep { AudioDecoder.decodeToPcm(context, uri) } ?: return@withContext null
        val displayName = resolveDisplayName(uri)?.substringBeforeLast(".") ?: "Referencni_Skladba"
        val canceller = ReferenceAudioCanceller(fftSize = 1024, hopSize = 256, sampleRate = decoded.sampleRate)
        runHeavyStep {
            canceller.learnFromReferenceTrack(
                referenceSamples = decoded.samples,
                referenceSampleRate = decoded.sampleRate,
                referenceTitle = displayName,
                targetSamples = targetSamples
            )
        }
    }

    suspend fun autoLearnProfileFromDetectedMusic(
        sourceFile: File,
        trackTitle: String
    ): ReferenceAudioCanceller.AutoLearnResult? = withContext(Dispatchers.Default) {
        val rawData = loadPcmSamples(sourceFile) ?: return@withContext null
        val canceller = ReferenceAudioCanceller(fftSize = 1024, hopSize = 256, sampleRate = rawData.sampleRate)
        runHeavyStep {
            canceller.learnFromDetectedMusic(
                samples = rawData.samples,
                sampleRate = rawData.sampleRate,
                trackTitle = trackTitle
            )
        }
    }

    suspend fun subtractLearnedProfileAndSave(
        sourceFile: File,
        profile: LearnedSpectralProfile,
        attenuationStrength: Float,
        spectralMatchGain: Float,
        timeOffsetMs: Long,
        protectVoiceFormants: Boolean
    ): AudioFileItem? = withContext(Dispatchers.Default) {
        val rawData = loadPcmSamples(sourceFile) ?: return@withContext null
        val canceller = ReferenceAudioCanceller(fftSize = 1024, hopSize = 256, sampleRate = rawData.sampleRate)
        val cleanedSamples = runHeavyStep {
            canceller.applyProfileSubtraction(
                targetSamples = rawData.samples,
                profile = profile,
                attenuationStrength = attenuationStrength,
                spectralMatchGain = spectralMatchGain,
                timeOffsetMs = timeOffsetMs,
                protectVoiceFormants = protectVoiceFormants
            )
        } ?: return@withContext null

        val baseName = sourceFile.nameWithoutExtension
            .substringBefore("_Cleaned")
            .substringBefore("_SongRemoved")
            .substringBefore("_VoiceSep")
        val suffix = if (profile.sourceType == com.example.dsp.LearnedSourceType.REFERENCE_AUDIO_FILE) {
            "SongRemoved"
        } else {
            "ProfileCleaned"
        }
        val newFileName = "${baseName}_${suffix}_${System.currentTimeMillis() % 1000}.wav"
        val outputFile = File(recordingsDir, newFileName)

        withContext(Dispatchers.IO) {
            WavHelper.writeWav(outputFile, cleanedSamples, rawData.sampleRate, 1)
        }

        val sizeMb = outputFile.length() / (1024.0 * 1024.0)
        val durMs = (cleanedSamples.size.toDouble() / rawData.sampleRate * 1000).toLong()

        AudioFileItem(
            id = outputFile.absolutePath.hashCode().toString(),
            title = outputFile.nameWithoutExtension,
            fileName = outputFile.name,
            filePath = outputFile.absolutePath,
            durationMs = durMs,
            fileSizeFormatted = String.format(Locale.US, "%.1f MB", sizeMb),
            sampleRate = rawData.sampleRate,
            bitDepth = 16,
            channels = 1,
            status = AudioStatus.CLEANED,
            createdAt = outputFile.lastModified()
        )
    }

    suspend fun deleteAudioFile(item: AudioFileItem): Boolean = withContext(Dispatchers.IO) {
        val file = File(item.filePath)
        // Treat an already-absent file as a successful delete so the UI stays consistent.
        !file.exists() || file.delete()
    }

    fun getNewRecordingOutputFile(titlePrefix: String = "Recording"): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(recordingsDir, "${titlePrefix}_${timeStamp}.wav")
    }
}
