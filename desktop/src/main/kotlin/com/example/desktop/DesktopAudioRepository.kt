package com.example.desktop

import com.example.dsp.AudioDspChain
import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.LearnedSpectralProfile
import com.example.dsp.LearnedSourceType
import com.example.dsp.ReferenceAudioCanceller
import com.example.dsp.VoiceSeparator
import com.example.dsp.WavAudioData
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
 * Desktop counterpart of the Android repository.
 *
 * On Windows there is no app sandbox to hide files in, so the library *is* the shared folder
 * `%USERPROFILE%\Music\AudioDetective`: imported recordings, cleaned copies and exports all live
 * side by side, immediately visible in Explorer. Reports are written to
 * `%USERPROFILE%\Documents\AudioDetective\reports`.
 */
class DesktopAudioRepository {

    val libraryDir: File = File(System.getProperty("user.home"), "Music/AudioDetective").apply { mkdirs() }
    val reportsDir: File = File(System.getProperty("user.home"), "Documents/AudioDetective/reports").apply { mkdirs() }

    /** Lists the library without decoding anything - only RIFF headers are read. */
    suspend fun listFiles(): List<AudioFileItem> = withContext(Dispatchers.IO) {
        libraryDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in DesktopAudioDecoder.SUPPORTED_EXTENSIONS }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { toItem(it) }
            ?: emptyList()
    }

    private fun toItem(file: File): AudioFileItem? {
        val sizeMb = file.length() / (1024.0 * 1024.0)
        val sizeFormatted = if (sizeMb >= 1.0) {
            String.format(Locale.US, "%.1f MB", sizeMb)
        } else {
            String.format(Locale.US, "%d KB", file.length() / 1024)
        }

        val probe = probeFormat(file)
        val decoded = decodeCached(file)

        // The decoded frame count is the only reliable length source. MP3SPI reports a frameLength
        // that does not match the real stream, so trusting it showed "00:00" for a one minute file.
        val durationMs = decoded?.let { (it.samples.size.toDouble() / it.sampleRate * 1000).toLong() }
            ?: probe.durationMs
        val metrics = decoded?.let { sampleMetrics(file, it) } ?: SampleMetrics(-48f, -3.2f, -24.5f, 0.65f)

        return AudioFileItem(
            id = file.absolutePath.hashCode().toString(),
            title = file.nameWithoutExtension,
            fileName = file.name,
            filePath = file.absolutePath,
            durationMs = durationMs,
            fileSizeFormatted = sizeFormatted,
            sampleRate = decoded?.sampleRate ?: probe.sampleRate,
            bitDepth = probe.bitsPerSample,
            channels = probe.channels,
            status = statusOf(file.name),
            createdAt = file.lastModified(),
            noiseFloorDb = metrics.noiseFloorDb,
            peakDb = metrics.peakDb,
            rmsDb = metrics.rmsDb,
            speechBandRatio = metrics.speechBandRatio
        )
    }

    private data class FormatProbe(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val durationMs: Long
    )

    /**
     * Reads format and length without decoding the audio. WAV exposes both in its header; MP3, FLAC
     * and OGG are probed through the service providers' *file format* readers, which parse only the
     * headers. (Their PCM stream readers are useless here because `AudioSystem` no longer discovers
     * classpath providers, but the format readers can be instantiated directly.)
     */
    private fun probeFormat(file: File): FormatProbe {
        val header = WavHelper.readWavHeader(file)
        if (header != null) {
            return FormatProbe(
                sampleRate = header.sampleRate,
                channels = header.channels,
                bitsPerSample = header.bitsPerSample,
                durationMs = if (header.sampleRate > 0) {
                    (header.frameCount.toDouble() / header.sampleRate * 1000).toLong()
                } else 0L
            )
        }

        val fileFormat = try {
            when (file.extension.lowercase()) {
                "mp3" -> javazoom.spi.mpeg.sampled.file.MpegAudioFileReader().getAudioFileFormat(file)
                "flac" -> org.jflac.sound.spi.FlacAudioFileReader().getAudioFileFormat(file)
                "ogg", "oga" -> javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader().getAudioFileFormat(file)
                else -> null
            }
        } catch (e: Exception) {
            null
        }

        if (fileFormat == null) {
            return FormatProbe(44100, 1, 16, 0L)
        }

        val format = fileFormat.format
        val rate = format.sampleRate.toInt().takeIf { it > 0 } ?: 44100
        val frames = fileFormat.frameLength.toLong()
        return FormatProbe(
            sampleRate = rate,
            channels = format.channels.coerceAtLeast(1),
            bitsPerSample = format.sampleSizeInBits.takeIf { it > 0 } ?: 16,
            durationMs = if (frames > 0) (frames.toDouble() / rate * 1000).toLong() else 0L
        )
    }

    private data class SampleMetrics(
        val noiseFloorDb: Float,
        val peakDb: Float,
        val rmsDb: Float,
        val speechBandRatio: Float
    )

    /** Metrics per file path, invalidated when the file changes, so listing stays cheap. */
    private val metricsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, SampleMetrics>>()

    /** Decoded PCM per file path, invalidated when the file changes. */
    private val decodeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, WavAudioData>>()

    private fun decodeCached(file: File): WavAudioData? {
        val modified = file.lastModified()
        val cached = decodeCache[file.absolutePath]
        if (cached != null && cached.first == modified) return cached.second

        val decoded = try {
            DesktopAudioDecoder.decode(file, AudioMemoryBudget.maxFrames())
        } catch (e: Exception) {
            null
        } ?: return null

        decodeCache[file.absolutePath] = modified to decoded
        return decoded
    }

    /** Measures peak, RMS, noise floor (dBFS) and the share of energy inside the speech band. */
    private fun sampleMetrics(file: File, decoded: WavAudioData): SampleMetrics {
        val fallback = SampleMetrics(-48f, -3.2f, -24.5f, 0.65f)
        val cached = metricsCache[file.absolutePath]
        if (cached != null && cached.first == file.lastModified()) return cached.second

        val samples = decoded.samples
        if (samples.isEmpty()) return fallback
        val rate = decoded.sampleRate.coerceAtLeast(1)

        var sumSquares = 0.0
        var peak = 0f
        for (sample in samples) {
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
            sumSquares += sample.toDouble() * sample
        }
        val rms = sqrt(sumSquares / samples.size)
        val peakDb = if (peak > 1e-4f) (20 * log10(peak.toDouble())).toFloat() else -90f
        val rmsDb = if (rms > 1e-4f) (20 * log10(rms)).toFloat() else -90f

        // Noise floor: average level of the quietest 10 % of 50 ms blocks.
        val block = (rate / 20).coerceAtLeast(1)
        val blockLevels = ArrayList<Float>(samples.size / block + 1)
        var index = 0
        while (index < samples.size) {
            val end = (index + block).coerceAtMost(samples.size)
            var sum = 0.0
            for (i in index until end) sum += abs(samples[i])
            val average = (sum / (end - index)).toFloat()
            blockLevels.add(if (average > 1e-4f) (20 * log10(average.toDouble())).toFloat() else -90f)
            index += block
        }
        blockLevels.sort()
        val quietCount = (blockLevels.size * 0.1f).toInt().coerceAtLeast(1)
        val noiseFloor = blockLevels.take(quietCount).average().toFloat()

        val bandRatio = VoiceSeparator(1024, 256, rate).analyzeVoiceMetrics(samples).vocalEnergyRatio
        val metrics = SampleMetrics(noiseFloor, peakDb, rmsDb, bandRatio)
        metricsCache[file.absolutePath] = file.lastModified() to metrics
        return metrics
    }

    private fun statusOf(name: String): AudioStatus = when {
        name.contains("VoiceSep", true) || name.contains("Isolated", true) || name.contains("WhisperSep", true) ->
            AudioStatus.VOICE_SEPARATED
        name.contains("Cleaned", true) || name.contains("Processed", true) || name.contains("Lms", true) ->
            AudioStatus.CLEANED
        name.contains("HPF", true) -> AudioStatus.HPF_ACTIVE
        name.contains("Boost", true) -> AudioStatus.WHISPER_BOOSTED
        name.contains("Deverb", true) || name.contains("SongRemoved", true) -> AudioStatus.CLEANED
        else -> AudioStatus.ORIGINAL
    }

    suspend fun loadPcmSamples(
        file: File,
        maxFrames: Long = AudioMemoryBudget.maxFrames()
    ): WavAudioData? = withContext(Dispatchers.IO) {
        try {
            DesktopAudioDecoder.decode(file, maxFrames)
        } catch (tooLarge: AudioTooLargeException) {
            null
        }
    }

    /** Copies an external recording into the library as mono WAV, matching the Android import. */
    suspend fun importFile(source: File): ImportOutcome = withContext(Dispatchers.IO) {
        val decoded = try {
            DesktopAudioDecoder.decode(source)
        } catch (tooLarge: AudioTooLargeException) {
            val sampleRate = WavHelper.readWavHeader(source)?.sampleRate ?: 44100
            return@withContext ImportOutcome.TooLarge(
                requiredMinutes = AudioMemoryBudget.durationMinutesOf(
                    tooLarge.requiredPcmBytes / Float.SIZE_BYTES,
                    sampleRate
                ),
                limitMinutes = AudioMemoryBudget.maxDurationMinutes(sampleRate)
            )
        } ?: return@withContext ImportOutcome.Failed

        val safeName = source.nameWithoutExtension
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
            .trim()
            .ifBlank { "Imported_${System.currentTimeMillis()}" }
        val target = uniqueFile("$safeName.wav")

        WavHelper.writeWav(target, decoded.samples, decoded.sampleRate, 1)
        ImportOutcome.Success(toItem(target) ?: return@withContext ImportOutcome.Failed)
    }

    /** Applies the shared DSP rack and writes a new working copy next to the source. */
    suspend fun processAndSave(
        sourceFile: File,
        config: DspConfig,
        suffix: String = "Cleaned"
    ): AudioFileItem? = withContext(Dispatchers.IO) {
        val raw = loadPcmSamples(sourceFile) ?: return@withContext null
        val processed = runHeavyStep { AudioDspChain.apply(raw.samples, raw.sampleRate, config).samples }
            ?: return@withContext null

        val baseName = sourceFile.nameWithoutExtension
            .substringBefore("_Cleaned")
            .substringBefore("_VoiceSep")
            .substringBefore("_Deverbed")
            .substringBefore("_Lms")
            .substringBefore("_Exported")
        val target = uniqueFile("${baseName}_$suffix.wav")
        WavHelper.writeWav(target, processed, raw.sampleRate, 1)
        toItem(target)
    }

    suspend fun subtractLearnedProfile(
        sourceFile: File,
        profile: LearnedSpectralProfile,
        attenuation: Float,
        matchGain: Float,
        offsetMs: Long,
        protectFormants: Boolean
    ): AudioFileItem? = withContext(Dispatchers.Default) {
        val raw = loadPcmSamples(sourceFile) ?: return@withContext null
        val canceller = ReferenceAudioCanceller(fftSize = 1024, hopSize = 256, sampleRate = raw.sampleRate)
        val cleaned = runHeavyStep {
            canceller.applyProfileSubtraction(
                targetSamples = raw.samples,
                profile = profile,
                attenuationStrength = attenuation,
                spectralMatchGain = matchGain,
                timeOffsetMs = offsetMs,
                protectVoiceFormants = protectFormants
            )
        } ?: return@withContext null

        val suffix = if (profile.sourceType == LearnedSourceType.REFERENCE_AUDIO_FILE) "SongRemoved" else "ProfileCleaned"
        val baseName = sourceFile.nameWithoutExtension.substringBefore("_Cleaned")
        val target = uniqueFile("${baseName}_$suffix.wav")
        withContext(Dispatchers.IO) { WavHelper.writeWav(target, cleaned, raw.sampleRate, 1) }
        toItem(target)
    }

    suspend fun learnProfileFromInterval(
        sourceFile: File,
        startMs: Long,
        endMs: Long
    ): LearnedSpectralProfile? = withContext(Dispatchers.Default) {
        val raw = loadPcmSamples(sourceFile) ?: return@withContext null
        val rate = raw.sampleRate
        val from = (startMs / 1000.0 * rate).toLong().coerceIn(0L, raw.samples.size.toLong())
        val to = (endMs / 1000.0 * rate).toLong().coerceIn(from, raw.samples.size.toLong())
        if (to - from < rate / 2) return@withContext null

        val slice = raw.samples.copyOfRange(from.toInt(), to.toInt())
        val canceller = ReferenceAudioCanceller(fftSize = 1024, hopSize = 256, sampleRate = rate)
        canceller.learnFromReferenceTrack(
            referenceSamples = slice,
            referenceSampleRate = rate,
            referenceTitle = "AB_${startMs}_${endMs}",
            targetSamples = raw.samples
        )
    }

    suspend fun learnProfileFromReferenceFile(
        referenceFile: File,
        targetSamples: FloatArray?
    ): LearnedSpectralProfile? = withContext(Dispatchers.Default) {
        val ref = loadPcmSamples(referenceFile) ?: return@withContext null
        val canceller = ReferenceAudioCanceller(fftSize = 1024, hopSize = 256, sampleRate = ref.sampleRate)
        runHeavyStep {
            canceller.learnFromReferenceTrack(
                referenceSamples = ref.samples,
                referenceSampleRate = ref.sampleRate,
                referenceTitle = referenceFile.nameWithoutExtension,
                targetSamples = targetSamples
            )
        }
    }

    suspend fun autoLearnProfileFromDetectedMusic(
        sourceFile: File,
        trackTitle: String
    ): ReferenceAudioCanceller.AutoLearnResult? = withContext(Dispatchers.Default) {
        val raw = loadPcmSamples(sourceFile) ?: return@withContext null
        val canceller = ReferenceAudioCanceller(fftSize = 1024, hopSize = 256, sampleRate = raw.sampleRate)
        canceller.learnFromDetectedMusic(
            samples = raw.samples,
            sampleRate = raw.sampleRate,
            trackTitle = trackTitle
        )
    }

    suspend fun generateReport(
        item: AudioFileItem,
        samples: FloatArray,
        config: DspConfig
    ): ForensicReport = withContext(Dispatchers.Default) {
        val rate = item.sampleRate.coerceAtLeast(1)

        var sumSquares = 0.0
        var peak = 0.0f
        for (sample in samples) {
            val value = abs(sample)
            if (value > peak) peak = value
            sumSquares += sample.toDouble() * sample
        }
        val rms = if (samples.isNotEmpty()) sqrt(sumSquares / samples.size) else 0.0
        val peakDb = if (peak > 1e-4f) (20 * log10(peak.toDouble())).toFloat() else -90f
        val rmsDb = if (rms > 1e-4f) (20 * log10(rms)).toFloat() else -90f

        // Vocal band energy share (300 Hz - 3400 Hz) via the FFT processor
        val separator = VoiceSeparator(fftSize = 1024, hopSize = 256, sampleRate = rate)
        val voiceStats = separator.analyzeVoiceMetrics(samples)

        // Whisper / low level speech segments, 0.5 s blocks, derived from the file's own rate
        val windowSamples = (rate / 2).coerceAtLeast(1)
        val segments = mutableListOf<Pair<Long, Long>>()
        var inWhisper = false
        var startMs = 0L
        var i = 0
        while (i < samples.size) {
            val end = (i + windowSamples).coerceAtMost(samples.size)
            var sum = 0.0
            for (j in i until end) sum += abs(samples[j])
            val average = (sum / (end - i)).toFloat()
            val blockDb = if (average > 1e-4f) (20 * log10(average.toDouble())).toFloat() else -90f
            val currentMs = (i.toDouble() / rate * 1000).toLong()
            if (blockDb in -48f..-22f) {
                if (!inWhisper) {
                    inWhisper = true
                    startMs = currentMs
                }
            } else if (inWhisper) {
                inWhisper = false
                if (currentMs - startMs >= 500) segments.add(startMs to currentMs)
            }
            i += windowSamples
        }
        if (inWhisper) {
            segments.add(startMs to (samples.size.toDouble() / rate * 1000).toLong())
        }

        ForensicReport(
            id = UUID.randomUUID().toString(),
            fileTitle = item.title,
            timestamp = System.currentTimeMillis(),
            durationMs = item.durationMs,
            sampleRate = rate,
            bitDepth = item.bitDepth,
            estimatedSnrDb = (peakDb - (rmsDb - 6f)).coerceIn(0f, 96f),
            peakLevelDb = peakDb,
            rmsLevelDb = rmsDb,
            dynamicRangeDb = (peakDb - (-90f)).coerceIn(0f, 96f),
            vocalBandEnergyPct = voiceStats.vocalEnergyRatio * 100f,
            detectedWhisperSegments = segments,
            appliedFilters = describeFilters(config),
            analystNotes = ""
        )
    }

    private fun describeFilters(config: DspConfig): List<String> {
        val applied = mutableListOf<String>()
        if (config.voiceSeparationEnabled) {
            applied.add("STFT Voice Separation: ${config.voiceSeparationMode.titleCz} (${(config.voiceSeparationStrength * 100).toInt()}%)")
        }
        if (config.speechBandpassEnabled) applied.add("Speech Band Pass (300-4000 Hz)")
        if (config.highPassEnabled) applied.add("High-Pass (${config.highPassCutoffHz.toInt()} Hz)")
        if (config.lowPassEnabled) applied.add("Low-Pass (${config.lowPassCutoffHz.toInt()} Hz)")
        if (config.noiseReductionEnabled) applied.add("Spectral Noise Gate (${(config.noiseReductionLevel * 100).toInt()}%)")
        if (config.adaptiveLmsEnabled) applied.add("Adaptive LMS (${(config.adaptiveLmsIntensity * 100).toInt()}%)")
        if (config.deverbEnabled) applied.add("Cepstral De-reverberation (${(config.deverbStrength * 100).toInt()}%)")
        if (config.whisperBoostEnabled) applied.add("Whisper Boost (+${config.whisperBoostGainDb.toInt()} dB)")
        return applied
    }

    /** Writes the forensic protocol as a readable text file into the reports folder. */
    suspend fun saveReport(report: ForensicReport, item: AudioFileItem): File = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(report.timestamp))
        val target = File(reportsDir, "ForensicReport_${item.title}_$stamp.txt")
        target.writeText(buildReportText(report, item), Charsets.UTF_8)
        target
    }

    private fun buildReportText(report: ForensicReport, item: AudioFileItem): String = buildString {
        appendLine("FORENSIC AUDIO ANALYSIS REPORT")
        appendLine("==============================")
        appendLine()
        appendLine("File          : ${item.fileName}")
        appendLine("Path          : ${item.filePath}")
        appendLine("Duration      : ${item.durationFormatted} (${item.durationWithMsFormatted})")
        appendLine("Format        : ${report.sampleRate} Hz / ${report.bitDepth}-bit PCM / mono")
        appendLine("Analyzed at   : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))}")
        appendLine()
        appendLine("MEASUREMENTS")
        appendLine("------------")
        appendLine("Peak level        : %.2f dBFS".format(report.peakLevelDb))
        appendLine("RMS level         : %.2f dBFS".format(report.rmsLevelDb))
        appendLine("Estimated SNR     : %.2f dB".format(report.estimatedSnrDb))
        appendLine("Dynamic range     : %.2f dB".format(report.dynamicRangeDb))
        appendLine("Voice band share  : %.1f %%".format(report.vocalBandEnergyPct))
        appendLine()
        appendLine("DETECTED LOW LEVEL SPEECH SEGMENTS")
        appendLine("----------------------------------")
        if (report.detectedWhisperSegments.isEmpty()) {
            appendLine("(none)")
        } else {
            report.detectedWhisperSegments.forEach { (from, to) ->
                appendLine("%s - %s".format(formatMs(from), formatMs(to)))
            }
        }
        appendLine()
        appendLine("APPLIED FILTERS")
        appendLine("---------------")
        if (report.appliedFilters.isEmpty()) {
            appendLine("(none - file analyzed as imported)")
        } else {
            report.appliedFilters.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("INTEGRITY")
        appendLine("---------")
        appendLine("Original recording is never modified. All cleaning steps produce a new working copy")
        appendLine("in the same folder; the source file stays read-only for the whole session.")
        appendLine()
        appendLine("Report id: ${report.id}")
    }

    private fun formatMs(ms: Long): String {
        val totalSeconds = ms / 1000
        return String.format(Locale.US, "%02d:%02d.%03d", totalSeconds / 60, totalSeconds % 60, ms % 1000)
    }

    /** Picks a free file name so an export never overwrites an earlier one. */
    private fun uniqueFile(displayName: String): File {
        val base = displayName.substringBeforeLast(".")
        val extension = displayName.substringAfterLast(".", "wav")
        var candidate = File(libraryDir, "$base.$extension")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(libraryDir, "${base}_$counter.$extension")
            counter++
        }
        return candidate
    }

    /** Turns an OutOfMemoryError from a heavy DSP stage into null instead of killing the process. */
    private fun <T> runHeavyStep(block: () -> T): T? = try {
        block()
    } catch (e: OutOfMemoryError) {
        e.printStackTrace()
        null
    }

    fun delete(item: AudioFileItem): Boolean {
        val file = File(item.filePath)
        return if (!file.exists()) true else file.delete()
    }
}

/** Outcome of importing a file into the library. */
sealed interface ImportOutcome {
    data class Success(val item: AudioFileItem) : ImportOutcome

    data class TooLarge(val requiredMinutes: Int, val limitMinutes: Int) : ImportOutcome

    data object Failed : ImportOutcome
}
