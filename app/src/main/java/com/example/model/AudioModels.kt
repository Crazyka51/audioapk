package com.example.model

import com.example.dsp.VoiceSeparationMode

enum class AudioStatus(val displayName: String) {
    ORIGINAL("Original"),
    CLEANED("Cleaned"),
    VOICE_SEPARATED("Voice Isolated"),
    HPF_ACTIVE("HPF Active"),
    WHISPER_BOOSTED("Boosted"),
    PROCESSED("Processed")
}

data class AudioFileItem(
    val id: String,
    val title: String,
    val fileName: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeFormatted: String,
    val sampleRate: Int = 44100,
    val bitDepth: Int = 16,
    val channels: Int = 1,
    val status: AudioStatus = AudioStatus.ORIGINAL,
    val createdAt: Long = System.currentTimeMillis(),
    val isSample: Boolean = false,
    val noiseFloorDb: Float = -48.0f,
    val peakDb: Float = -3.2f,
    val rmsDb: Float = -24.5f,
    val speechBandRatio: Float = 0.65f // % energy in 300Hz-3400Hz
) {
    val durationFormatted: String
        get() {
            val totalSeconds = (durationMs / 1000).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val millis = (durationMs % 1000) / 100
            return String.format("%02d:%02d", minutes, seconds)
        }

    val durationWithMsFormatted: String
        get() {
            val totalSeconds = (durationMs / 1000).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val millis = (durationMs % 1000)
            return String.format("%02d:%02d.%03d", minutes, seconds, millis)
        }
}

data class DspConfig(
    val highPassEnabled: Boolean = false,
    val highPassCutoffHz: Float = 300f,
    val lowPassEnabled: Boolean = false,
    val lowPassCutoffHz: Float = 4000f,
    val speechBandpassEnabled: Boolean = false, // Combo 300Hz-4000Hz
    
    val whisperBoostEnabled: Boolean = false,
    val whisperBoostGainDb: Float = 16.0f, // +3 to +30 dB
    val whisperThresholdDb: Float = -32.0f,
    
    val noiseReductionEnabled: Boolean = false,
    val noiseReductionLevel: Float = 0.75f, // 0.0 to 1.0

    val voiceSeparationEnabled: Boolean = false,
    val voiceSeparationMode: VoiceSeparationMode = VoiceSeparationMode.ISOLATE_VOICE,
    val voiceSeparationStrength: Float = 0.85f, // 0.1 to 1.0
    val vocalFormantBoostDb: Float = 8.0f, // 0 to +18 dB

    // Cepstral De-reverberation (Inverse Comb Filter for empty room / bathroom echo)
    val deverbEnabled: Boolean = false,
    val deverbStrength: Float = 0.75f, // 0.0 to 1.0
    val deverbManualDelayMs: Float = 0.0f, // 0.0 = auto-detect via Cepstrum

    // Adaptive LMS Filter (Dynamic Non-Stationary Noise, traffic, barking)
    val adaptiveLmsEnabled: Boolean = false,
    val adaptiveLmsIntensity: Float = 0.80f, // 0.0 to 1.0
    val adaptiveLmsSpeechPreserve: Float = 0.70f, // 0.0 to 1.0
    
    val playbackSpeed: Float = 1.0f, // 0.5x to 1.5x
    val pitchCorrectionEnabled: Boolean = true,
    
    val abLoopEnabled: Boolean = false,
    val loopStartMs: Long = 0L,
    val loopEndMs: Long = 0L
)

data class ForensicReport(
    val id: String,
    val fileTitle: String,
    val timestamp: Long,
    val durationMs: Long,
    val sampleRate: Int,
    val bitDepth: Int,
    val estimatedSnrDb: Float,
    val peakLevelDb: Float,
    val rmsLevelDb: Float,
    val dynamicRangeDb: Float,
    val vocalBandEnergyPct: Float,
    val detectedWhisperSegments: List<Pair<Long, Long>>, // startMs, endMs
    val appliedFilters: List<String>,
    val analystNotes: String = ""
)

enum class AppTab(val title: String) {
    HOME("Home"),
    STUDIO("Studio"),
    LIBRARY("Library"),
    EFFECTS("Effects"),
    REPORT("Forensic Report")
}
