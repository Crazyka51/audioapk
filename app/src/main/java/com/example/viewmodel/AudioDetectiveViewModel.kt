package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioRecorderManager
import com.example.audio.ForensicAudioPlayer
import com.example.audio.PlaybackState
import com.example.audio.RecordingStatus
import com.example.data.AudioDetectiveRepository
import com.example.data.AudioExporter
import com.example.data.ExportResult
import com.example.data.ImportResult
import com.example.dsp.AdaptiveLmsFilter
import com.example.dsp.AudioMemoryBudget
import com.example.dsp.CepstralDeverbProcessor
import com.example.dsp.FftProcessor
import com.example.dsp.LearnedSpectralProfile
import com.example.dsp.LmsFilterMetrics
import com.example.dsp.ReverberationMetrics
import com.example.dsp.SpectrogramResult
import com.example.dsp.VoiceSeparationMode
import com.example.dsp.VoiceSeparationStats
import com.example.dsp.VoiceSeparator
import com.example.dsp.WavHelper
import com.example.model.AppTab
import com.example.model.AudioFileItem
import com.example.model.DspConfig
import com.example.model.ForensicReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class InspectionPoint(
    val frequencyHz: Float,
    val decibels: Float,
    val timeMs: Long
)

class AudioDetectiveViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioDetectiveRepository(application)
    private val player = ForensicAudioPlayer(application)
    private val recorder = AudioRecorderManager(application)
    private val fftProcessor = FftProcessor(fftSize = 1024)

    val playbackState: StateFlow<PlaybackState> = player.playbackState
    val recordingStatus: StateFlow<RecordingStatus> = recorder.recordingStatus

    private val _audioFiles = MutableStateFlow<List<AudioFileItem>>(emptyList())
    val audioFiles: StateFlow<List<AudioFileItem>> = _audioFiles.asStateFlow()

    private val _selectedAudio = MutableStateFlow<AudioFileItem?>(null)
    val selectedAudio: StateFlow<AudioFileItem?> = _selectedAudio.asStateFlow()

    private val _pcmSamples = MutableStateFlow<FloatArray?>(null)
    val pcmSamples: StateFlow<FloatArray?> = _pcmSamples.asStateFlow()

    private val _spectrogram = MutableStateFlow<SpectrogramResult?>(null)
    val spectrogram: StateFlow<SpectrogramResult?> = _spectrogram.asStateFlow()

    private val _voiceSeparationStats = MutableStateFlow<VoiceSeparationStats?>(null)
    val voiceSeparationStats: StateFlow<VoiceSeparationStats?> = _voiceSeparationStats.asStateFlow()

    private val _reverbMetrics = MutableStateFlow<ReverberationMetrics?>(null)
    val reverbMetrics: StateFlow<ReverberationMetrics?> = _reverbMetrics.asStateFlow()

    private val _lmsMetrics = MutableStateFlow<LmsFilterMetrics?>(null)
    val lmsMetrics: StateFlow<LmsFilterMetrics?> = _lmsMetrics.asStateFlow()

    private val _learnedProfile = MutableStateFlow<LearnedSpectralProfile?>(null)
    val learnedProfile: StateFlow<LearnedSpectralProfile?> = _learnedProfile.asStateFlow()

    private val _referenceAttenuation = MutableStateFlow(0.88f)
    val referenceAttenuation: StateFlow<Float> = _referenceAttenuation.asStateFlow()

    private val _referenceMatchGain = MutableStateFlow(1.20f)
    val referenceMatchGain: StateFlow<Float> = _referenceMatchGain.asStateFlow()

    private val _referenceOffsetMs = MutableStateFlow(0L)
    val referenceOffsetMs: StateFlow<Long> = _referenceOffsetMs.asStateFlow()

    private val _protectVoiceFormants = MutableStateFlow(true)
    val protectVoiceFormants: StateFlow<Boolean> = _protectVoiceFormants.asStateFlow()

    private val _dspConfig = MutableStateFlow(DspConfig())
    val dspConfig: StateFlow<DspConfig> = _dspConfig.asStateFlow()

    private val _activeTab = MutableStateFlow(AppTab.HOME)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _inspectionPoint = MutableStateFlow<InspectionPoint?>(null)
    val inspectionPoint: StateFlow<InspectionPoint?> = _inspectionPoint.asStateFlow()

    private val _forensicReport = MutableStateFlow<ForensicReport?>(null)
    val forensicReport: StateFlow<ForensicReport?> = _forensicReport.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _isRecordingDialogOpen = MutableStateFlow(false)
    val isRecordingDialogOpen: StateFlow<Boolean> = _isRecordingDialogOpen.asStateFlow()

    init {
        loadAudioFiles()
        // Surface decode/playback failures from the AudioTrack engine as user-facing snackbars.
        viewModelScope.launch {
            player.errorMessage.collect { message ->
                if (message != null) {
                    _userMessage.value = message
                    player.clearError()
                }
            }
        }
    }

    fun loadAudioFiles(autoSelectFirst: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            val files = repository.getAudioFiles()
            _audioFiles.value = files
            _isLoading.value = false

            if (autoSelectFirst && files.isNotEmpty() && _selectedAudio.value == null) {
                selectAudioFile(files.first())
            }
        }
    }

    fun selectAudioFile(item: AudioFileItem) {
        if (_selectedAudio.value?.id == item.id && _pcmSamples.value != null) return

        _selectedAudio.value = item
        player.stop()

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val file = File(item.filePath)

            // Refuse recordings that cannot fit into the heap before decoding anything.
            val header = WavHelper.readWavHeader(file)
            if (header != null && header.frameCount > AudioMemoryBudget.maxFrames()) {
                _pcmSamples.value = null
                _spectrogram.value = null
                _userMessage.value = "Nahrávka je příliš dlouhá na analýzu: " +
                    "${AudioMemoryBudget.durationMinutesOf(header.frameCount, header.sampleRate)} min, " +
                    "limit je ${AudioMemoryBudget.maxDurationMinutes(header.sampleRate)} min. " +
                    "Zkrať ji prosím na kratší úsek."
                _isLoading.value = false
                return@launch
            }

            val pcmData = repository.loadPcmSamples(file)

            if (pcmData != null) {
                _pcmSamples.value = pcmData.samples
                val spec = fftProcessor.computeSpectrogram(
                    samples = pcmData.samples,
                    sampleRate = pcmData.sampleRate,
                    maxColumns = 320,
                    hopSize = 512
                )
                _spectrogram.value = spec

                val separator = VoiceSeparator(fftSize = 1024, hopSize = 256, sampleRate = pcmData.sampleRate)
                _voiceSeparationStats.value = separator.analyzeVoiceMetrics(pcmData.samples)

                val deverb = CepstralDeverbProcessor(fftSize = 2048, sampleRate = pcmData.sampleRate)
                _reverbMetrics.value = deverb.analyzeReverberation(pcmData.samples)

                val lms = AdaptiveLmsFilter()
                val lmsProcessed = lms.process(pcmData.samples, 0.80f, 0.70f)
                _lmsMetrics.value = lms.evaluateMetrics(pcmData.samples, lmsProcessed)

                // Configure default loop markers to 25% - 75% or first 3 seconds
                val durMs = item.durationMs
                val defaultStart = (durMs * 0.15).toLong()
                val defaultEnd = (durMs * 0.65).toLong().coerceAtLeast(defaultStart + 1000L).coerceAtMost(durMs)

                _dspConfig.value = _dspConfig.value.copy(
                    loopStartMs = defaultStart,
                    loopEndMs = defaultEnd
                )
            } else {
                _pcmSamples.value = null
                _spectrogram.value = null
                _voiceSeparationStats.value = null
            }

            player.loadAudio(file, _dspConfig.value)
            _isLoading.value = false
        }
    }

    fun setTab(tab: AppTab) {
        _activeTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePlayPause() {
        player.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        _dspConfig.value = _dspConfig.value.copy(playbackSpeed = speed)
        player.setSpeed(speed)
    }

    fun toggleSpeechBandpass(enabled: Boolean) {
        val updated = _dspConfig.value.copy(
            speechBandpassEnabled = enabled,
            highPassEnabled = enabled,
            lowPassEnabled = enabled,
            highPassCutoffHz = 300f,
            lowPassCutoffHz = 4000f
        )
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun toggleHighPass(enabled: Boolean) {
        val updated = _dspConfig.value.copy(highPassEnabled = enabled)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setHighPassCutoff(hz: Float) {
        val updated = _dspConfig.value.copy(highPassCutoffHz = hz)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun toggleLowPass(enabled: Boolean) {
        val updated = _dspConfig.value.copy(lowPassEnabled = enabled)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setLowPassCutoff(hz: Float) {
        val updated = _dspConfig.value.copy(lowPassCutoffHz = hz)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun toggleWhisperBoost(enabled: Boolean) {
        val updated = _dspConfig.value.copy(whisperBoostEnabled = enabled)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setWhisperBoostGain(gainDb: Float) {
        val updated = _dspConfig.value.copy(whisperBoostGainDb = gainDb)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun toggleNoiseReduction(enabled: Boolean) {
        val updated = _dspConfig.value.copy(noiseReductionEnabled = enabled)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setNoiseReductionLevel(level: Float) {
        val updated = _dspConfig.value.copy(noiseReductionLevel = level)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun toggleVoiceSeparation(enabled: Boolean) {
        val updated = _dspConfig.value.copy(voiceSeparationEnabled = enabled)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setVoiceSeparationMode(mode: VoiceSeparationMode) {
        val updated = _dspConfig.value.copy(
            voiceSeparationEnabled = true,
            voiceSeparationMode = mode
        )
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setVoiceSeparationStrength(strength: Float) {
        val updated = _dspConfig.value.copy(voiceSeparationStrength = strength)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setVocalFormantBoost(boostDb: Float) {
        val updated = _dspConfig.value.copy(vocalFormantBoostDb = boostDb)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    // --- Cepstral De-reverberation (Inverse Comb Filter) ---

    fun toggleDeverb(enabled: Boolean) {
        val updated = _dspConfig.value.copy(deverbEnabled = enabled)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setDeverbStrength(strength: Float) {
        val updated = _dspConfig.value.copy(deverbStrength = strength)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setDeverbManualDelay(delayMs: Float) {
        val updated = _dspConfig.value.copy(deverbManualDelayMs = delayMs)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun applyDeverbAndSave() {
        val selected = _selectedAudio.value ?: return
        val activeConfig = _dspConfig.value.copy(deverbEnabled = true)
        _dspConfig.value = activeConfig
        viewModelScope.launch {
            _isLoading.value = true
            val sourceFile = File(selected.filePath)
            val newTrack = repository.processAndSaveFilteredWav(sourceFile, activeConfig, "Deverbed")
            _isLoading.value = false
            if (newTrack != null) {
                val note = publishToDevice(newTrack)
                _userMessage.value = "Ozvěna odstraněna: ${newTrack.fileName}$note"
                loadAudioFiles(autoSelectFirst = false)
                selectAudioFile(newTrack)
            } else {
                _userMessage.value = "Chyba při de-reverberaci"
            }
        }
    }

    // --- Adaptive LMS Filter (Dynamic Noise / Traffic / Dogs) ---

    fun toggleAdaptiveLms(enabled: Boolean) {
        val updated = _dspConfig.value.copy(adaptiveLmsEnabled = enabled)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setAdaptiveLmsIntensity(intensity: Float) {
        val updated = _dspConfig.value.copy(adaptiveLmsIntensity = intensity)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setAdaptiveLmsSpeechPreserve(preserve: Float) {
        val updated = _dspConfig.value.copy(adaptiveLmsSpeechPreserve = preserve)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun applyAdaptiveLmsAndSave() {
        val selected = _selectedAudio.value ?: return
        val activeConfig = _dspConfig.value.copy(adaptiveLmsEnabled = true)
        _dspConfig.value = activeConfig
        viewModelScope.launch {
            _isLoading.value = true
            val sourceFile = File(selected.filePath)
            val newTrack = repository.processAndSaveFilteredWav(sourceFile, activeConfig, "LmsCleaned")
            _isLoading.value = false
            if (newTrack != null) {
                val note = publishToDevice(newTrack)
                _userMessage.value = "Dynamický šum potlačen: ${newTrack.fileName}$note"
                loadAudioFiles(autoSelectFirst = false)
                selectAudioFile(newTrack)
            } else {
                _userMessage.value = "Chyba při adaptivní LMS filtraci"
            }
        }
    }

    fun separateVoiceAndSave() {
        val selected = _selectedAudio.value ?: return
        val activeConfig = _dspConfig.value.copy(voiceSeparationEnabled = true)
        _dspConfig.value = activeConfig
        viewModelScope.launch {
            _isLoading.value = true
            val sourceFile = File(selected.filePath)
            val suffix = when (activeConfig.voiceSeparationMode) {
                VoiceSeparationMode.ISOLATE_WHISPER -> "WhisperSep"
                VoiceSeparationMode.BACKGROUND_RESIDUAL -> "BgResidual"
                else -> "VoiceSep"
            }
            val newTrack = repository.processAndSaveFilteredWav(sourceFile, activeConfig, suffix)
            _isLoading.value = false
            if (newTrack != null) {
                val note = publishToDevice(newTrack)
                _userMessage.value = "Hlas separován: ${newTrack.fileName}$note"
                loadAudioFiles(autoSelectFirst = false)
                selectAudioFile(newTrack)
            } else {
                _userMessage.value = "Chyba při separaci hlasu"
            }
        }
    }

    // --- 2-Step Spectral Sample & Reference Song Subtraction ---

    fun learnProfileFromAbLoop() {
        val selected = _selectedAudio.value ?: return
        val cfg = _dspConfig.value
        val startMs = cfg.loopStartMs
        val endMs = if (cfg.loopEndMs > startMs + 300L) cfg.loopEndMs else (startMs + 2500L).coerceAtMost(selected.durationMs)

        viewModelScope.launch {
            _isLoading.value = true
            val profile = repository.learnProfileFromInterval(
                sourceFile = File(selected.filePath),
                startMs = startMs,
                endMs = endMs,
                trackTitle = selected.title
            )
            _isLoading.value = false
            if (profile != null) {
                _learnedProfile.value = profile
                _referenceOffsetMs.value = 0L
                _userMessage.value = "Vzorek získán z úseku A–B (${String.format("%.1f", (endMs - startMs) / 1000f)} s) • Dom. ${profile.dominantFreqHz.toInt()} Hz"
            } else {
                _userMessage.value = "Nepodařilo se získat vzorek (úsek je příliš krátký)"
            }
        }
    }

    fun learnProfileFromLibraryTrack(referenceItem: AudioFileItem) {
        viewModelScope.launch {
            _isLoading.value = true
            val profile = repository.learnProfileFromReferenceFile(
                referenceFile = File(referenceItem.filePath),
                targetSamples = _pcmSamples.value
            )
            _isLoading.value = false
            if (profile != null) {
                _learnedProfile.value = profile
                _referenceOffsetMs.value = profile.detectedOffsetMs
                _userMessage.value = "Načten referenční vzor: ${referenceItem.title} (synchronizace: ${profile.detectedOffsetMs} ms)"
            } else {
                _userMessage.value = "Chyba při analýze referenční skladby"
            }
        }
    }

    fun learnProfileFromExternalUri(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            val profile = repository.learnProfileFromReferenceUri(
                uri = uri,
                targetSamples = _pcmSamples.value
            )
            _isLoading.value = false
            if (profile != null) {
                _learnedProfile.value = profile
                _referenceOffsetMs.value = profile.detectedOffsetMs
                _userMessage.value = "Načtena referenční skladba: ${profile.sourceName} (offset: ${profile.detectedOffsetMs} ms)"
            } else {
                _userMessage.value = "Nepodařilo se dekódovat referenční soubor"
            }
        }
    }

    fun autoLearnProfileFromDetectedMusic() {
        val selected = _selectedAudio.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.autoLearnProfileFromDetectedMusic(
                sourceFile = File(selected.filePath),
                trackTitle = selected.title
            )
            _isLoading.value = false
            _learnedProfile.value = result?.profile
            _userMessage.value = when {
                result == null -> "Nahrávku se nepodařilo analyzovat"
                result.profile != null -> {
                    val segment = result.analysis.bestLearningSegment()
                    "Hudba nalezena${segment?.let { " v úseku ${it.first}-${it.second} ms" } ?: ""}; profil je připraven"
                }
                else -> "Hudbu v nahrávce se nepodařilo spolehlivě najít"
            }
        }
    }

    fun clearLearnedProfile() {
        _learnedProfile.value = null
    }

    fun setReferenceAttenuation(value: Float) {
        _referenceAttenuation.value = value.coerceIn(0.1f, 1.0f)
    }

    fun setReferenceMatchGain(value: Float) {
        _referenceMatchGain.value = value.coerceIn(0.5f, 2.5f)
    }

    fun setReferenceOffsetMs(offsetMs: Long) {
        _referenceOffsetMs.value = offsetMs.coerceAtLeast(0L)
    }

    fun setProtectVoiceFormants(enabled: Boolean) {
        _protectVoiceFormants.value = enabled
    }

    fun applyLearnedProfileToWholeTrack() {
        val selected = _selectedAudio.value ?: return
        val profile = _learnedProfile.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            val cleanedTrack = repository.subtractLearnedProfileAndSave(
                sourceFile = File(selected.filePath),
                profile = profile,
                attenuationStrength = _referenceAttenuation.value,
                spectralMatchGain = _referenceMatchGain.value,
                timeOffsetMs = _referenceOffsetMs.value,
                protectVoiceFormants = _protectVoiceFormants.value
            )
            _isLoading.value = false
            if (cleanedTrack != null) {
                val note = publishToDevice(cleanedTrack)
                _userMessage.value = "Nahrávka vyčištěna od vzorku/hudby: ${cleanedTrack.fileName}$note"
                loadAudioFiles(autoSelectFirst = false)
                selectAudioFile(cleanedTrack)
            } else {
                _userMessage.value = "Chyba při odečítání spektrálního vzorku"
            }
        }
    }

    fun toggleAbLoop(enabled: Boolean) {
        val updated = _dspConfig.value.copy(abLoopEnabled = enabled)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setLoopStartToCurrent() {
        val current = playbackState.value.currentPositionMs
        val updated = _dspConfig.value.copy(
            loopStartMs = current,
            loopEndMs = if (current >= _dspConfig.value.loopEndMs) current + 2000L else _dspConfig.value.loopEndMs
        )
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun setLoopEndToCurrent() {
        val current = playbackState.value.currentPositionMs
        val updated = _dspConfig.value.copy(
            loopEndMs = current.coerceAtLeast(_dspConfig.value.loopStartMs + 500L)
        )
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun nudgeLoopStart(deltaMs: Long) {
        val newStart = (_dspConfig.value.loopStartMs + deltaMs).coerceAtLeast(0L)
        val updated = _dspConfig.value.copy(loopStartMs = newStart)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun nudgeLoopEnd(deltaMs: Long) {
        val dur = playbackState.value.durationMs
        val newEnd = (_dspConfig.value.loopEndMs + deltaMs).coerceIn(_dspConfig.value.loopStartMs + 100L, dur)
        val updated = _dspConfig.value.copy(loopEndMs = newEnd)
        _dspConfig.value = updated
        player.updateDspConfig(updated)
    }

    fun inspectPoint(freqHz: Float, db: Float, timeMs: Long) {
        _inspectionPoint.value = InspectionPoint(freqHz, db, timeMs)
    }

    fun clearInspectionPoint() {
        _inspectionPoint.value = null
    }

    fun processAndSaveCopy(suffix: String = "Cleaned") {
        val selected = _selectedAudio.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val sourceFile = File(selected.filePath)
            val newTrack = repository.processAndSaveFilteredWav(sourceFile, _dspConfig.value, suffix)
            _isLoading.value = false
            if (newTrack != null) {
                val note = publishToDevice(newTrack)
                _userMessage.value = "Saved cleaned copy: ${newTrack.title}.wav$note"
                loadAudioFiles(autoSelectFirst = false)
                selectAudioFile(newTrack)
            } else {
                _userMessage.value = "Failed to process audio copy"
            }
        }
    }

    /**
     * Copies a freshly produced working copy into the shared `Music/AudioDetective` folder and
     * reports where it landed, so export destinations are visible in the confirmation message.
     */
    private suspend fun publishToDevice(item: AudioFileItem): String =
        when (val result = repository.exportToDevice(File(item.filePath), item.fileName)) {
            is ExportResult.Success -> " → ${result.location}"
            ExportResult.PermissionRequired ->
                " (soubor zůstal jen v aplikaci – pro uložení do ${AudioExporter.displayLocation} povol přístup k úložišti)"
            ExportResult.Failed ->
                " (uložení do ${AudioExporter.displayLocation} se nezdařilo)"
        }

    fun generateForensicReport() {
        val selected = _selectedAudio.value ?: return
        val pcm = _pcmSamples.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val report = repository.generateForensicReport(selected, pcm, _dspConfig.value)
            _forensicReport.value = report
            _activeTab.value = AppTab.REPORT
            _isLoading.value = false
        }
    }

    fun openRecordingDialog() {
        _isRecordingDialogOpen.value = true
    }

    fun closeRecordingDialog() {
        if (recordingStatus.value.isRecording) {
            recorder.stopRecording()
        }
        _isRecordingDialogOpen.value = false
    }

    fun startRecording(customName: String = "Forensic_Live_Record"): Boolean {
        val outputFile = repository.getNewRecordingOutputFile(customName)
        return recorder.startRecording(outputFile)
    }

    fun stopRecordingAndSave() {
        val savedFile = recorder.stopRecording()
        _isRecordingDialogOpen.value = false
        if (savedFile != null && savedFile.exists()) {
            _userMessage.value = "Recording saved: ${savedFile.name}"
            loadAudioFiles(autoSelectFirst = false)
            viewModelScope.launch {
                val files = repository.getAudioFiles()
                val newlyAdded = files.find { it.filePath == savedFile.absolutePath }
                if (newlyAdded != null) {
                    selectAudioFile(newlyAdded)
                }
            }
        }
    }

    fun importAudioFile(uri: Uri, name: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.importAudio(uri, name)) {
                is ImportResult.Success -> {
                    _userMessage.value = "Audio imported successfully"
                    loadAudioFiles(autoSelectFirst = false)
                    selectAudioFile(result.item)
                }
                is ImportResult.TooLarge -> {
                    _userMessage.value = "Nahrávka je příliš dlouhá (~${result.requiredMinutes} min), " +
                        "do paměti se vejde nejvýše ~${result.limitMinutes} min. " +
                        "Zkrať ji prosím a import zopakuj."
                }
                ImportResult.Failed -> _userMessage.value = "Failed to decode audio file format"
            }
            _isLoading.value = false
        }
    }

    fun deleteAudioFile(item: AudioFileItem) {
        viewModelScope.launch {
            val deleted = repository.deleteAudioFile(item)
            if (!deleted) {
                _userMessage.value = "Nahrávku se nepodařilo smazat: ${item.fileName}"
                return@launch
            }

            val files = repository.getAudioFiles()
            _audioFiles.value = files
            if (_selectedAudio.value?.id == item.id) {
                if (files.isNotEmpty()) {
                    selectAudioFile(files.first())
                } else {
                    _selectedAudio.value = null
                    _pcmSamples.value = null
                    _spectrogram.value = null
                    player.stop()
                }
            }
            _userMessage.value = "Smazáno: ${item.fileName}"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
