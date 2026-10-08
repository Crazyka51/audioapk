package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.example.audio.AudioDecoder
import com.example.dsp.AudioDspChain
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavAudioData
import com.example.dsp.WavHelper
import com.example.model.DspConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLooping: Boolean = false,
    val speed: Float = 1.0f,
    /** True while the DSP effect rack is being (re)computed for the live preview. */
    val isProcessing: Boolean = false,
    /** True once the audio has been decoded and is ready to play. */
    val isReady: Boolean = false
)

/**
 * Low-latency forensic audio player.
 *
 * Unlike the previous [android.media.MediaPlayer] based implementation (which could not apply any
 * DSP during playback), this player decodes the track to normalized PCM, runs it through the
 * shared [AudioDspChain] (the exact same chain used for non-destructive export) and streams the
 * result via [AudioTrack]. This makes the whole effect rack audible live during playback, which is
 * the core forensic "preview before you export" workflow.
 *
 * Behaviour:
 *  - Every DSP toggle/slider change triggers a debounced recompute of the processed PCM. Playback
 *    position is preserved and the writer restarts at the same spot, so the change is heard
 *    immediately.
 *  - Playback speed (0.5x - 1.5x) is applied with pitch lock via [AudioTrack.setPlaybackParams].
 *  - A-B loop is enforced sample-accurately in the streaming writer.
 *  - Decode/initialization errors are surfaced through [errorMessage] instead of being swallowed.
 */
class ForensicAudioPlayer(private val context: Context) {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var audioTrack: AudioTrack? = null
    private var writerJob: Job? = null
    private var progressJob: Job? = null
    private var prepareJob: Job? = null
    private var recomputeJob: Job? = null

    private var currentFile: File? = null

    @Volatile
    private var dspConfig: DspConfig = DspConfig()

    @Volatile
    private var sampleRate: Int = 44100

    @Volatile
    private var sourceSamples: FloatArray? = null

    @Volatile
    private var processedSamples: FloatArray? = null

    // Wall-clock playback position model (speed adjusted, independent of AudioTrack internals).
    @Volatile
    private var basePositionMs: Long = 0L

    @Volatile
    private var tickStartElapsed: Long = 0L

    @Volatile
    private var speed: Float = 1.0f

    @Volatile
    private var writerGeneration: Int = 0

    @Volatile
    private var loadGeneration: Int = 0

    private var lastProcessingKey: List<Any>? = null

    private val blockFrames = 2048

    // ---------------------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------------------

    fun loadAudio(file: File, initialConfig: DspConfig = DspConfig()) {
        stop()
        currentFile = file
        dspConfig = initialConfig
        speed = initialConfig.playbackSpeed
        basePositionMs = 0L
        lastProcessingKey = processingKey(initialConfig)
        _playbackState.value = PlaybackState(
            speed = speed,
            isLooping = initialConfig.abLoopEnabled,
            isProcessing = true
        )

        val gen = ++loadGeneration
        prepareJob = engineScope.launch {
            val decoded = decodeFile(file)
            if (gen != loadGeneration) return@launch
            if (decoded == null) {
                _playbackState.value = PlaybackState(speed = speed)
                _errorMessage.value = "Nepodařilo se načíst audio soubor: ${file.name}"
                return@launch
            }

            sampleRate = decoded.sampleRate
            sourceSamples = decoded.samples

            val processed = try {
                AudioDspChain.apply(decoded.samples, decoded.sampleRate, dspConfig).samples
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                decoded.samples
            } catch (e: Exception) {
                e.printStackTrace()
                decoded.samples
            }
            if (gen != loadGeneration) return@launch
            processedSamples = processed

            ensureTrack(decoded.sampleRate)
            if (gen != loadGeneration) return@launch

            val durMs = (decoded.samples.size.toDouble() / decoded.sampleRate * 1000.0).toLong()
            _playbackState.value = PlaybackState(
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = durMs,
                isLooping = dspConfig.abLoopEnabled,
                speed = speed,
                isProcessing = false,
                isReady = true
            )
            applyPlaybackParams()
        }
    }

    private fun decodeFile(file: File): WavAudioData? {
        if (!file.exists()) return null
        return try {
            if (file.name.endsWith(".wav", ignoreCase = true)) {
                WavHelper.readWav(file)
            } else {
                AudioDecoder.decodeToPcm(context, Uri.fromFile(file))
            }
        } catch (tooLarge: AudioTooLargeException) {
            _errorMessage.value = "Nahrávka je příliš dlouhá na analýzu. Zkrať ji na kratší úsek."
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun ensureTrack(sr: Int) {
        releaseTrack()
        val channelMask = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val minBufBytes = AudioTrack.getMinBufferSize(sr, channelMask, encoding)
        val desiredBytes = max(minBufBytes, blockFrames * 4) * 2
        val bufferBytes = if (desiredBytes > 0) desiredBytes else blockFrames * 8

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sr)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        audioTrack = track
        if (track == null) {
            _errorMessage.value = "Nepodařilo se inicializovat audio výstup zařízení"
        }
    }

    private fun releaseTrack() {
        audioTrack?.let { track ->
            try {
                if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            } catch (ignored: Exception) {
            }
            try {
                track.release()
            } catch (ignored: Exception) {
            }
        }
        audioTrack = null
    }

    // ---------------------------------------------------------------------------------------------
    // Transport
    // ---------------------------------------------------------------------------------------------

    fun play() {
        if (_playbackState.value.isPlaying) return
        if (processedSamples == null) return
        resumeFrom(_playbackState.value.currentPositionMs)
    }

    private fun resumeFrom(positionMs: Long) {
        if (audioTrack == null || processedSamples == null) return
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
        restartWriterAt(positionMs)
        startProgress()
    }

    fun pause() {
        if (!_playbackState.value.isPlaying) return
        val pos = computePositionNow()
        progressJob?.cancel()
        writerJob?.cancel()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (ignored: Exception) {
        }
        basePositionMs = pos
        _playbackState.value = _playbackState.value.copy(isPlaying = false, currentPositionMs = pos)
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val dur = _playbackState.value.durationMs
        val upper = if (dur > 0L) dur else positionMs.coerceAtLeast(0L)
        val clamped = positionMs.coerceIn(0L, upper)
        val wasPlaying = _playbackState.value.isPlaying

        progressJob?.cancel()
        writerJob?.cancel()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (ignored: Exception) {
        }

        basePositionMs = clamped
        _playbackState.value = _playbackState.value.copy(currentPositionMs = clamped)

        if (wasPlaying) {
            restartWriterAt(clamped)
            startProgress()
        }
    }

    fun setSpeed(newSpeed: Float) {
        val clamped = newSpeed.coerceIn(0.5f, 1.5f)
        if (_playbackState.value.isPlaying) {
            // Re-anchor the wall-clock model before changing the rate.
            basePositionMs = computePositionNow()
            tickStartElapsed = SystemClock.elapsedRealtime()
        }
        speed = clamped
        dspConfig = dspConfig.copy(playbackSpeed = clamped)
        applyPlaybackParams()
        _playbackState.value = _playbackState.value.copy(speed = clamped)
    }

    fun updateDspConfig(config: DspConfig) {
        val needsRecompute = processingKey(config) != lastProcessingKey
        lastProcessingKey = processingKey(config)
        dspConfig = config
        _playbackState.value = _playbackState.value.copy(isLooping = config.abLoopEnabled)

        if (config.playbackSpeed != speed) {
            setSpeed(config.playbackSpeed)
        }
        if (needsRecompute) {
            schedulePreviewRecompute()
        }
    }

    /**
     * Restarts the streaming writer at [positionMs] using the current processed PCM.
     * The AudioTrack is flushed so the new (recomputed) audio starts immediately.
     */
    private fun restartWriterAt(positionMs: Long) {
        val track = audioTrack ?: return
        val dur = _playbackState.value.durationMs
        val clamped = positionMs.coerceIn(0L, if (dur > 0L) dur else positionMs.coerceAtLeast(0L))
        basePositionMs = clamped
        tickStartElapsed = SystemClock.elapsedRealtime()
        try {
            track.pause()
            track.flush()
            applyPlaybackParams()
            track.play()
        } catch (ignored: Exception) {
        }
        startWriter(msToFrame(clamped))
    }

    private fun startWriter(fromFrame: Int) {
        val track = audioTrack ?: return
        val samples = processedSamples ?: return

        writerJob?.cancel()
        writerGeneration += 1
        val generation = writerGeneration
        val total = samples.size

        writerJob = engineScope.launch {
            var idx = fromFrame.coerceIn(0, total)
            val block = FloatArray(blockFrames)

            while (isActive && generation == writerGeneration) {
                val cfg = dspConfig
                val rawLooping = cfg.abLoopEnabled && cfg.loopEndMs > cfg.loopStartMs
                val loopStart = if (rawLooping) msToFrame(cfg.loopStartMs) else 0
                val loopEnd = if (rawLooping) {
                    msToFrame(cfg.loopEndMs).coerceAtLeast(loopStart + 1).coerceAtMost(total)
                } else {
                    total
                }
                val looping = rawLooping && loopStart < loopEnd && loopStart < total

                if (looping && (idx < loopStart || idx >= loopEnd)) {
                    // Seamless gapless A-B loop: keep writing from A without flushing the track
                    // (flushing would truncate the ~buffer-latency worth of audio still queued).
                    idx = loopStart
                }

                val end = if (looping) min(idx + blockFrames, loopEnd) else min(idx + blockFrames, total)
                if (end <= idx) {
                    if (!looping) break
                    idx = loopStart
                    continue
                }

                val count = end - idx
                System.arraycopy(samples, idx, block, 0, count)

                val written = try {
                    track.write(block, 0, count, AudioTrack.WRITE_BLOCKING)
                } catch (e: Exception) {
                    -1
                }
                if (written < 0) break

                idx = end
            }
        }
    }

    private fun startProgress() {
        progressJob?.cancel()
        progressJob = uiScope.launch {
            while (isActive) {
                val snapshot = _playbackState.value
                if (!snapshot.isPlaying) break

                val cfg = dspConfig
                val looping = cfg.abLoopEnabled && cfg.loopEndMs > cfg.loopStartMs
                var pos = basePositionMs +
                    ((SystemClock.elapsedRealtime() - tickStartElapsed) * speed).toLong()

                if (looping && pos >= cfg.loopEndMs) {
                    // Keep the displayed playhead in sync with the audio loop wrap.
                    basePositionMs = cfg.loopStartMs
                    tickStartElapsed = SystemClock.elapsedRealtime()
                    pos = cfg.loopStartMs
                } else if (!looping && snapshot.durationMs > 0L && pos >= snapshot.durationMs) {
                    delay(150) // allow the queued AudioTrack buffer to drain
                    finishPlayback()
                    break
                }

                _playbackState.value = snapshot.copy(
                    currentPositionMs = pos.coerceAtLeast(0L),
                    isLooping = looping,
                    speed = speed
                )
                delay(30) // ~30 FPS UI updates for a smooth playhead
            }
        }
    }

    private fun finishPlayback() {
        writerJob?.cancel()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (ignored: Exception) {
        }
        val dur = _playbackState.value.durationMs
        basePositionMs = dur
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            currentPositionMs = dur
        )
    }

    private fun computePositionNow(): Long {
        if (!_playbackState.value.isPlaying) return _playbackState.value.currentPositionMs
        val pos = basePositionMs + ((SystemClock.elapsedRealtime() - tickStartElapsed) * speed).toLong()
        return pos.coerceIn(0L, _playbackState.value.durationMs)
    }

    private fun msToFrame(ms: Long): Int {
        val total = processedSamples?.size ?: sourceSamples?.size ?: 0
        return ((ms / 1000.0) * sampleRate).toInt().coerceIn(0, total)
    }

    private fun applyPlaybackParams() {
        val track = audioTrack ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = PlaybackParams().setSpeed(speed).setPitch(1.0f)
                track.playbackParams = params
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Live DSP preview
    // ---------------------------------------------------------------------------------------------

    /**
     * Debounced recompute of the DSP-processed PCM buffer. Runs on a background dispatcher and, if
     * audio is currently playing, seamlessly restarts the writer at the same position.
     */
    private fun schedulePreviewRecompute() {
        val source = sourceSamples ?: return
        val gen = loadGeneration
        recomputeJob?.cancel()
        recomputeJob = engineScope.launch {
            delay(150) // debounce rapid slider movements
            if (gen != loadGeneration) return@launch
            val cfg = dspConfig
            _playbackState.value = _playbackState.value.copy(isProcessing = true)

            val result = try {
                AudioDspChain.apply(source, sampleRate, cfg)
            } catch (e: OutOfMemoryError) {
                // A very long recording may not fit the heap with the full effect chain applied.
                // Report it instead of letting the process die.
                e.printStackTrace()
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            if (gen != loadGeneration) return@launch

            if (result == null) {
                _playbackState.value = _playbackState.value.copy(isProcessing = false)
                _errorMessage.value = "Efekt nelze aplikovat: nahrávka je pro živý náhled příliš dlouhá"
                return@launch
            }

            processedSamples = result.samples

            if (_playbackState.value.isPlaying) {
                val pos = computePositionNow()
                restartWriterAt(pos)
            }

            _playbackState.value = _playbackState.value.copy(isProcessing = false)
        }
    }

    /**
     * Extracts only the DSP-relevant fields of [config]. Loop bounds, playback speed and other
     * transport-only parameters are intentionally excluded so that A-B nudging or speed changes do
     * not trigger an expensive recompute.
     */
    private fun processingKey(c: DspConfig): List<Any> = listOf(
        c.highPassEnabled, c.highPassCutoffHz,
        c.lowPassEnabled, c.lowPassCutoffHz,
        c.speechBandpassEnabled,
        c.whisperBoostEnabled, c.whisperBoostGainDb, c.whisperThresholdDb,
        c.noiseReductionEnabled, c.noiseReductionLevel,
        c.voiceSeparationEnabled, c.voiceSeparationMode, c.voiceSeparationStrength, c.vocalFormantBoostDb,
        c.deverbEnabled, c.deverbStrength, c.deverbManualDelayMs,
        c.adaptiveLmsEnabled, c.adaptiveLmsIntensity, c.adaptiveLmsSpeechPreserve
    )

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    fun clearError() {
        _errorMessage.value = null
    }

    fun stop() {
        loadGeneration += 1
        prepareJob?.cancel()
        recomputeJob?.cancel()
        progressJob?.cancel()
        writerJob?.cancel()
        releaseTrack()
        processedSamples = null
        sourceSamples = null
        basePositionMs = 0L
        _playbackState.value = PlaybackState(speed = speed)
    }

    fun release() {
        stop()
        engineScope.cancel()
        uiScope.cancel()
    }
}
