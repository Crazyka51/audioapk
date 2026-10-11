package com.example.desktop

import com.example.dsp.CepstralDeverbProcessor
import com.example.dsp.FftProcessor
import com.example.dsp.LearnedSpectralProfile
import com.example.dsp.LmsFilterMetrics
import com.example.dsp.ReverberationMetrics
import com.example.dsp.SpectrogramResult
import com.example.dsp.VoiceSeparationMode
import com.example.dsp.VoiceSeparationStats
import com.example.dsp.VoiceSeparator
import com.example.dsp.AdaptiveLmsFilter
import com.example.model.AudioFileItem
import com.example.model.AppTab
import com.example.model.DspConfig
import com.example.model.ForensicReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Point read off the spectrogram, mirroring the Android inspection HUD. */
data class InspectionPoint(
    val frequencyHz: Float,
    val decibels: Float,
    val timeMs: Long
)

/**
 * Desktop counterpart of `AudioDetectiveViewModel`. It drives the same shared DSP core and exposes
 * the same kind of state, minus everything that only exists on Android (recording permission,
 * SAF import, MediaStore export, share intents).
 */
class DesktopViewModel {

    private val repository = DesktopAudioRepository()
    private val player = DesktopAudioPlayer()
    private val recorder = DesktopAudioRecorder()
    private val fftProcessor = FftProcessor(fftSize = 1024)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val playbackState = player.playbackState
    val recordingStatus = recorder.status
    val libraryDir: File get() = repository.libraryDir
    val reportsDir: File get() = repository.reportsDir

    private val _tab = MutableStateFlow(AppTab.HOME)
    val tab: StateFlow<AppTab> = _tab.asStateFlow()

    private val _samples = MutableStateFlow<FloatArray?>(null)

    /** PCM of the selected recording, used by the waveform and spectrogram panels. */
    val samples: StateFlow<FloatArray?> = _samples.asStateFlow()

    private val _files = MutableStateFlow<List<AudioFileItem>>(emptyList())
    val files: StateFlow<List<AudioFileItem>> = _files.asStateFlow()

    private val _selected = MutableStateFlow<AudioFileItem?>(null)
    val selected: StateFlow<AudioFileItem?> = _selected.asStateFlow()

    private val _spectrogram = MutableStateFlow<SpectrogramResult?>(null)
    val spectrogram: StateFlow<SpectrogramResult?> = _spectrogram.asStateFlow()

    private val _voiceStats = MutableStateFlow<VoiceSeparationStats?>(null)
    val voiceStats: StateFlow<VoiceSeparationStats?> = _voiceStats.asStateFlow()

    private val _reverbMetrics = MutableStateFlow<ReverberationMetrics?>(null)
    val reverbMetrics: StateFlow<ReverberationMetrics?> = _reverbMetrics.asStateFlow()

    private val _lmsMetrics = MutableStateFlow<LmsFilterMetrics?>(null)
    val lmsMetrics: StateFlow<LmsFilterMetrics?> = _lmsMetrics.asStateFlow()

    private val _profile = MutableStateFlow<LearnedSpectralProfile?>(null)
    val profile: StateFlow<LearnedSpectralProfile?> = _profile.asStateFlow()

    private val _config = MutableStateFlow(DspConfig())
    val config: StateFlow<DspConfig> = _config.asStateFlow()

    private val _report = MutableStateFlow<ForensicReport?>(null)
    val report: StateFlow<ForensicReport?> = _report.asStateFlow()

    private val _inspection = MutableStateFlow<InspectionPoint?>(null)
    val inspection: StateFlow<InspectionPoint?> = _inspection.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val attenuation = MutableStateFlow(0.88f)
    val matchGain = MutableStateFlow(1.20f)
    val offsetMs = MutableStateFlow(0L)
    val protectFormants = MutableStateFlow(true)

    init {
        refresh()
        scope.launch {
            player.errorMessage.collect { error ->
                if (error != null) {
                    _message.value = error
                    player.clearError()
                }
            }
        }
        scope.launch {
            recorder.error.collect { error ->
                if (error != null) {
                    _message.value = error
                    recorder.clearError()
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        _tab.value = tab
    }

    fun refresh(autoSelectFirst: Boolean = true) {
        scope.launch {
            val list = repository.listFiles()
            _files.value = list
            if (autoSelectFirst && _selected.value == null && list.isNotEmpty()) {
                select(list.first())
            }
        }
    }

    fun select(item: AudioFileItem) {
        _selected.value = item
        _samples.value = null
        player.stop()
        player.load(File(item.filePath), _config.value)
        _report.value = null
        _inspection.value = null

        scope.launch {
            _busy.value = true
            val pcm = repository.loadPcmSamples(File(item.filePath))
            if (pcm == null) {
                _spectrogram.value = null
                _voiceStats.value = null
                _message.value = "Nahrávku nelze načíst (příliš dlouhá nebo nepodporovaný formát)"
                _busy.value = false
                return@launch
            }

            _samples.value = pcm.samples
            _spectrogram.value = fftProcessor.computeSpectrogram(
                samples = pcm.samples,
                sampleRate = pcm.sampleRate,
                maxColumns = 320,
                hopSize = 512
            )
            _voiceStats.value = VoiceSeparator(1024, 256, pcm.sampleRate).analyzeVoiceMetrics(pcm.samples)
            _reverbMetrics.value = CepstralDeverbProcessor(2048, pcm.sampleRate).analyzeReverberation(pcm.samples)
            val lms = AdaptiveLmsFilter()
            _lmsMetrics.value = lms.evaluateMetrics(pcm.samples, lms.process(pcm.samples, 0.8f, 0.7f))

            val duration = item.durationMs
            val start = (duration * 0.15).toLong()
            val end = (duration * 0.65).toLong().coerceAtLeast(start + 1000L).coerceAtMost(duration)
            update { it.copy(loopStartMs = start, loopEndMs = end) }
            _busy.value = false
        }
    }

    private fun update(transform: (DspConfig) -> DspConfig) {
        val updated = transform(_config.value)
        _config.value = updated
        player.updateDspConfig(updated)
    }

    // --- Transport -------------------------------------------------------------------------

    fun togglePlayPause() = player.togglePlayPause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun setSpeed(speed: Float) = update { it.copy(playbackSpeed = speed) }
    fun setLoopStart(ms: Long) = update { it.copy(loopStartMs = ms.coerceAtLeast(0L)) }
    fun setLoopEnd(ms: Long) = update { it.copy(loopEndMs = ms.coerceAtLeast(0L)) }
    fun toggleLoop(enabled: Boolean) = update { it.copy(abLoopEnabled = enabled) }

    // --- Effect rack -----------------------------------------------------------------------

    fun setSpeechBandpass(enabled: Boolean) = update { it.copy(speechBandpassEnabled = enabled) }
    fun setHighPass(enabled: Boolean, cutoff: Float = _config.value.highPassCutoffHz) =
        update { it.copy(highPassEnabled = enabled, highPassCutoffHz = cutoff) }
    fun setHighPassCutoff(hz: Float) = update { it.copy(highPassCutoffHz = hz) }
    fun setLowPass(enabled: Boolean, cutoff: Float = _config.value.lowPassCutoffHz) =
        update { it.copy(lowPassEnabled = enabled, lowPassCutoffHz = cutoff) }
    fun setLowPassCutoff(hz: Float) = update { it.copy(lowPassCutoffHz = hz) }
    fun setWhisperBoost(enabled: Boolean) = update { it.copy(whisperBoostEnabled = enabled) }
    fun setWhisperGain(db: Float) = update { it.copy(whisperBoostGainDb = db) }
    fun setWhisperThreshold(db: Float) = update { it.copy(whisperThresholdDb = db) }
    fun setNoiseReduction(enabled: Boolean) = update { it.copy(noiseReductionEnabled = enabled) }
    fun setNoiseLevel(level: Float) = update { it.copy(noiseReductionLevel = level) }
    fun setVoiceSeparation(enabled: Boolean) = update { it.copy(voiceSeparationEnabled = enabled) }
    fun setVoiceMode(mode: VoiceSeparationMode) = update { it.copy(voiceSeparationMode = mode) }
    fun setVoiceStrength(strength: Float) = update { it.copy(voiceSeparationStrength = strength) }
    fun setFormantBoost(db: Float) = update { it.copy(vocalFormantBoostDb = db) }
    fun setDeverb(enabled: Boolean) = update { it.copy(deverbEnabled = enabled) }
    fun setDeverbStrength(strength: Float) = update { it.copy(deverbStrength = strength) }
    fun setDeverbDelay(ms: Float) = update { it.copy(deverbManualDelayMs = ms) }
    fun setAdaptiveLms(enabled: Boolean) = update { it.copy(adaptiveLmsEnabled = enabled) }
    fun setLmsIntensity(value: Float) = update { it.copy(adaptiveLmsIntensity = value) }
    fun setLmsPreserve(value: Float) = update { it.copy(adaptiveLmsSpeechPreserve = value) }

    // --- Reference/known-song subtraction tuning --------------------------------------------

    fun setAttenuation(value: Float) {
        attenuation.value = value.coerceIn(0.3f, 1f)
    }

    fun setMatchGain(value: Float) {
        matchGain.value = value.coerceIn(0.5f, 2.5f)
    }

    fun setOffsetMs(value: Long) {
        offsetMs.value = value.coerceIn(-5000L, 5000L)
    }

    fun setProtectFormants(value: Boolean) {
        protectFormants.value = value
    }

    // --- Import / export -------------------------------------------------------------------

    fun importFile(source: File) {
        scope.launch {
            _busy.value = true
            when (val outcome = repository.importFile(source)) {
                is ImportOutcome.Success -> {
                    _message.value = "Importováno do knihovny: ${outcome.item.fileName}"
                    refresh(autoSelectFirst = false)
                    select(outcome.item)
                }
                is ImportOutcome.TooLarge -> _message.value =
                    "Nahrávka je příliš dlouhá (~${outcome.requiredMinutes} min), " +
                        "limit je ~${outcome.limitMinutes} min. Zkrať ji prosím."
                ImportOutcome.Failed -> {
                    val extension = source.extension.lowercase()
                    _message.value = if (extension in DesktopAudioDecoder.UNSUPPORTED_EXTENSIONS) {
                        "Formát .$extension aplikace neumí dekódovat. Použij prosím " +
                            "${DesktopAudioDecoder.SUPPORTED_LABEL}."
                    } else {
                        "Soubor \"${source.name}\" se nepodařilo dekódovat. " +
                            "Podporované formáty: ${DesktopAudioDecoder.SUPPORTED_LABEL}."
                    }
                }
            }
            _busy.value = false
        }
    }

    fun applyAndSave(suffix: String = "Cleaned", mutate: (DspConfig) -> DspConfig = { it }) {
        val item = _selected.value ?: return
        val active = mutate(_config.value)
        _config.value = active
        player.updateDspConfig(active)

        scope.launch {
            _busy.value = true
            val created = repository.processAndSave(File(item.filePath), active, suffix)
            _busy.value = false
            if (created != null) {
                _message.value = "Uloženo: ${created.fileName} → ${repository.libraryDir.absolutePath}"
                refresh(autoSelectFirst = false)
                select(created)
            } else {
                _message.value = "Zpracování se nezdařilo"
            }
        }
    }

    fun delete(item: AudioFileItem) {
        scope.launch {
            if (!repository.delete(item)) {
                _message.value = "Soubor nelze smazat: ${item.fileName}"
                return@launch
            }
            if (_selected.value?.id == item.id) {
                _selected.value = null
                _spectrogram.value = null
                player.stop()
            }
            _message.value = "Smazáno: ${item.fileName}"
            refresh(autoSelectFirst = _selected.value == null)
        }
    }

    // --- Reference / sample subtraction ----------------------------------------------------

    fun learnFromAbInterval() {
        val item = _selected.value ?: return
        val cfg = _config.value
        if (cfg.loopEndMs - cfg.loopStartMs < 500) {
            _message.value = "Označ v časové ose delší úsek A-B (alespoň 0,5 s)"
            return
        }
        scope.launch {
            _busy.value = true
            val learned = repository.learnProfileFromInterval(File(item.filePath), cfg.loopStartMs, cfg.loopEndMs)
            _profile.value = learned
            _busy.value = false
            _message.value = if (learned != null) {
                "Profil naučen z úseku A-B: ${learned.durationMs} ms, " +
                    "dominantní frekvence ${learned.dominantFreqHz.toInt()} Hz"
            } else {
                "Profil se nepodařilo naučit"
            }
        }
    }

    fun learnFromReferenceFile(file: File) {
        val item = _selected.value ?: return
        scope.launch {
            _busy.value = true
            val pcm = repository.loadPcmSamples(File(item.filePath))
            val learned = repository.learnProfileFromReferenceFile(file, pcm?.samples)
            _profile.value = learned
            _busy.value = false
            _message.value = if (learned != null) {
                "Profil naučen z referenčního souboru ${file.name}"
            } else {
                "Referenční soubor se nepodařilo načíst"
            }
        }
    }

    fun autoLearnFromDetectedMusic() {
        val item = _selected.value ?: return
        scope.launch {
            _busy.value = true
            val result = repository.autoLearnProfileFromDetectedMusic(File(item.filePath), item.title)
            _profile.value = result?.profile
            _busy.value = false
            _message.value = when {
                result == null -> "Nahrávku se nepodařilo analyzovat"
                result.profile != null -> {
                    val segment = result.analysis.bestLearningSegment()
                    "Hudba nalezena${segment?.let { " v úseku ${it.first}-${it.second} ms" } ?: ""}; profil je připraven"
                }
                else -> "Hudbu v nahrávce se nepodařilo spolehlivě najít"
            }
        }
    }

    fun applyProfile() {
        val item = _selected.value ?: return
        val learned = _profile.value ?: return
        scope.launch {
            _busy.value = true
            val created = repository.subtractLearnedProfile(
                sourceFile = File(item.filePath),
                profile = learned,
                attenuation = attenuation.value,
                matchGain = matchGain.value,
                offsetMs = offsetMs.value,
                protectFormants = protectFormants.value
            )
            _busy.value = false
            if (created != null) {
                _message.value = "Vyčištěno odečtením profilu: ${created.fileName}"
                refresh(autoSelectFirst = false)
                select(created)
            } else {
                _message.value = "Odečtení profilu se nezdařilo"
            }
        }
    }

    fun clearProfile() {
        _profile.value = null
    }

    // --- Report ----------------------------------------------------------------------------

    fun generateReport() {
        val item = _selected.value ?: return
        scope.launch {
            _busy.value = true
            val pcm = repository.loadPcmSamples(File(item.filePath))
            if (pcm == null) {
                _message.value = "Report nelze vytvořit, nahrávku se nepodařilo načíst"
                _busy.value = false
                return@launch
            }
            _report.value = repository.generateReport(item, pcm.samples, _config.value)
            _busy.value = false
        }
    }

    fun saveReport() {
        val report = _report.value ?: return
        val item = _selected.value ?: return
        scope.launch {
            val file = repository.saveReport(report, item)
            _message.value = "Report uložen: ${file.absolutePath}"
        }
    }

    fun inspect(frequencyHz: Float, decibels: Float, timeMs: Long) {
        _inspection.value = InspectionPoint(frequencyHz, decibels, timeMs)
    }

    /**
     * Loads the PCM buffer for waveform drawing. Kept separate from [select] because the waveform is
     * only needed by the studio view and would otherwise double the decoding work on selection.
     */
    suspend fun loadSamplesFor(item: AudioFileItem): FloatArray? =
        withContext(Dispatchers.Default) {
            repository.loadPcmSamples(File(item.filePath))?.samples
        }

    // --- Recording ---------------------------------------------------------------------------

    fun startRecording(): Boolean {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val output = File(repository.libraryDir, "Recording_$stamp.wav")
        val started = recorder.start(output)
        if (!started) {
            _message.value = "Nahrávání se nepodařilo spustit"
        }
        return started
    }

    fun stopRecording() {
        scope.launch {
            val written = recorder.stop()
            if (written == null || !written.exists() || written.length() < 44) {
                _message.value = "Nahrávka je prázdná"
                return@launch
            }
            _message.value = "Nahrávka uložena: ${written.name}"
            refresh(autoSelectFirst = false)
            repository.listFiles().find { it.filePath == written.absolutePath }?.let { select(it) }
        }
    }

    // --- Quick actions from the dashboard ----------------------------------------------------

    fun quickNoiseReduction() = applyAndSave("NoiseReduced") { it.copy(noiseReductionEnabled = true) }

    fun quickWhisperBoost() = applyAndSave("WhisperBoost") { it.copy(whisperBoostEnabled = true) }

    fun exportCopy() = applyAndSave("Exported")

    fun clearInspection() {
        _inspection.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun dispose() {
        player.release()
    }
}
