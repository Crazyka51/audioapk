package com.example.desktop

import com.example.dsp.AudioDspChain
import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavAudioData
import com.example.model.DspConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.abs

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isReady: Boolean = false,
    val isProcessing: Boolean = false,
    val isLooping: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f
)

/**
 * Desktop counterpart of the Android `ForensicAudioPlayer`.
 *
 * Android streams into an `AudioTrack`; on the JVM a `SourceDataLine` takes that role. The effect
 * rack is the shared [AudioDspChain], so the preview here is acoustically identical to what the
 * Android app plays and to what gets written on export.
 *
 * Difference to Android: playback speed is implemented by resampling the processed buffer, which
 * shifts pitch like a tape machine. Android uses `AudioTrack.setPlaybackParams` with a pitch lock.
 */
class DesktopAudioPlayer {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentFile: File? = null
    private var dspConfig = DspConfig()

    @Volatile
    private var sourceSamples: FloatArray? = null

    @Volatile
    private var processedSamples: FloatArray? = null

    @Volatile
    private var sampleRate: Int = 44100

    @Volatile
    private var framePosition: Long = 0L

    @Volatile
    private var writerGeneration: Int = 0

    private var line: SourceDataLine? = null
    private var lineSampleRate: Int = -1
    private var writerJob: Job? = null
    private var prepareJob: Job? = null

    private val blockFrames = 2048

    fun load(file: File, initialConfig: DspConfig = DspConfig()) {
        stop()
        currentFile = file
        dspConfig = initialConfig
        framePosition = 0L
        _playbackState.value = PlaybackState(isProcessing = true, speed = initialConfig.playbackSpeed)

        prepareJob = scope.launch {
            val decoded = try {
                DesktopAudioDecoder.decode(file)
            } catch (tooLarge: AudioTooLargeException) {
                _errorMessage.value = "Nahrávka je příliš dlouhá na analýzu. Zkrať ji na kratší úsek."
                null
            }

            if (decoded == null) {
                _playbackState.value = PlaybackState()
                if (_errorMessage.value == null) {
                    _errorMessage.value = "Nepodařilo se načíst audio soubor: ${file.name}"
                }
                return@launch
            }

            sampleRate = decoded.sampleRate
            sourceSamples = decoded.samples
            processedSamples = applyChain(decoded.samples, decoded.sampleRate, dspConfig)

            val durMs = (decoded.samples.size.toDouble() / decoded.sampleRate * 1000.0).toLong()
            _playbackState.value = PlaybackState(
                isReady = true,
                durationMs = durMs,
                isLooping = dspConfig.abLoopEnabled,
                speed = dspConfig.playbackSpeed
            )
        }
    }

    /** Applies the shared DSP rack, surviving an OutOfMemoryError on very long recordings. */
    private fun applyChain(samples: FloatArray, rate: Int, config: DspConfig): FloatArray = try {
        AudioDspChain.apply(samples, rate, config).samples
    } catch (e: OutOfMemoryError) {
        _errorMessage.value = "Efekt nelze aplikovat: nahrávka je pro náhled příliš dlouhá"
        samples
    } catch (e: Exception) {
        e.printStackTrace()
        samples
    }

    fun updateDspConfig(config: DspConfig) {
        dspConfig = config
        val source = sourceSamples ?: return
        val generation = writerGeneration
        prepareJob?.cancel()
        prepareJob = scope.launch {
            delay(150) // debounce rapid slider movements
            _playbackState.value = _playbackState.value.copy(isProcessing = true)
            val processed = applyChain(source, sampleRate, config)
            if (generation != writerGeneration) return@launch
            processedSamples = processed
            _playbackState.value = _playbackState.value.copy(
                isProcessing = false,
                isLooping = config.abLoopEnabled,
                speed = config.playbackSpeed
            )
            if (_playbackState.value.isPlaying) {
                restartWriter()
            }
        }
    }

    fun play() {
        if (_playbackState.value.isPlaying) return
        if (processedSamples == null) return
        writerGeneration++
        startWriter(framePosition)
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
    }

    fun pause() {
        if (!_playbackState.value.isPlaying) return
        stopWriter()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val duration = _playbackState.value.durationMs
        val target = positionMs.coerceIn(0L, duration)
        val frame = (target / 1000.0 * sampleRate).toLong()
        framePosition = frame
        _playbackState.value = _playbackState.value.copy(currentPositionMs = target)
        if (_playbackState.value.isPlaying) {
            writerGeneration++
            startWriter(frame)
        }
    }

    fun stop() {
        stopWriter()
        framePosition = 0L
        _playbackState.value = _playbackState.value.copy(isPlaying = false, currentPositionMs = 0L)
    }

    fun release() {
        stop()
        prepareJob?.cancel()
        closeLine()
        scope.launch { }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun startWriter(fromFrame: Long) {
        val previousJob = writerJob
        stopWriter()
        val samples = processedSamples ?: return
        val rate = sampleRate
        val generation = writerGeneration

        writerJob = scope.launch {
            // A previous writer (from the play/pause/seek just replaced) may still be finishing a
            // blocking line.write() call on another thread; SourceDataLine is not safe to touch from
            // two threads at once, so wait for it to fully exit before this one opens/restarts the
            // line. Without this, rapid seeking (dragging the position slider) or a quick
            // pause-then-play could race on the same line and leave it stuck.
            previousJob?.join()
            val output = ensureLine(rate) ?: return@launch
            var frame = fromFrame.coerceIn(0L, samples.size.toLong())
            val bytes = ByteArray(blockFrames * 2)

            try {
                while (isActive && generation == writerGeneration) {
                    val config = dspConfig
                    if (config.abLoopEnabled && config.loopEndMs > config.loopStartMs) {
                        val loopStart = (config.loopStartMs / 1000.0 * rate).toLong()
                        val loopEnd = (config.loopEndMs / 1000.0 * rate).toLong()
                        if (frame >= loopEnd || frame < loopStart) frame = loopStart
                    }

                    if (frame >= samples.size) {
                        framePosition = samples.size.toLong()
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            currentPositionMs = _playbackState.value.durationMs
                        )
                        break
                    }

                    val speed = config.playbackSpeed.coerceIn(0.5f, 1.5f)
                    // Speed is applied by reading more or fewer source frames per output block
                    // (tape style pitch shift), which keeps the writer loop simple and exact.
                    val framesToRead = (blockFrames * speed).toInt().coerceAtLeast(1)
                    val available = (samples.size - frame).coerceAtMost(framesToRead.toLong()).toInt()

                    if (speed == 1.0f) {
                        for (i in 0 until available) {
                            val v = (samples[(frame + i).toInt()].coerceIn(-1f, 1f) * 32767f).toInt()
                            bytes[i * 2] = (v and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                        }
                        output.write(bytes, 0, available * 2)
                        frame += available
                    } else {
                        for (i in 0 until blockFrames) {
                            val srcIndex = frame + (i * speed).toInt()
                            val value = if (srcIndex < samples.size) samples[srcIndex.toInt()] else 0f
                            val v = (value.coerceIn(-1f, 1f) * 32767f).toInt()
                            bytes[i * 2] = (v and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                        }
                        output.write(bytes, 0, blockFrames * 2)
                        frame += (blockFrames * speed).toLong()
                    }

                    framePosition = frame
                    val positionMs = (frame.toDouble() / rate * 1000.0).toLong()
                    if (abs(positionMs - _playbackState.value.currentPositionMs) >= 50) {
                        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
                    }
                }
            } catch (e: Exception) {
                if (generation == writerGeneration) {
                    _errorMessage.value = "Chyba přehrávání: ${e.message}"
                    _playbackState.value = _playbackState.value.copy(isPlaying = false)
                }
            }
        }
    }

    private fun stopWriter() {
        // Deliberately not clearing writerJob here: it may still be finishing a blocking line write
        // on another thread, and the next startWriter() call needs this same reference so it can
        // wait for that write to actually finish before touching the line again (see startWriter).
        writerJob?.cancel()
        line?.let { active ->
            try {
                active.stop()
                active.flush()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun restartWriter() {
        val position = framePosition
        writerGeneration++
        startWriter(position)
    }

    private fun ensureLine(rate: Int): SourceDataLine? {
        line?.let {
            if (it.isOpen && lineSampleRate == rate) {
                // pause()/seek() stop (but do not close) the line to flush stale buffered audio; if
                // it is not restarted here, writes just queue up until the never-draining buffer
                // fills and the writer coroutine blocks on write() forever - which is what used to
                // make playback silently freeze a fraction of a second after resuming.
                if (!it.isRunning) it.start()
                return it
            }
        }
        closeLine()
        return try {
            val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val opened = AudioSystem.getLine(info) as SourceDataLine
            opened.open(format, blockFrames * 8)
            opened.start()
            line = opened
            lineSampleRate = rate
            opened
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "Nepodařilo se inicializovat audio výstup: ${e.message}"
            null
        }
    }

    private fun closeLine() {
        line?.let {
            try {
                it.close()
            } catch (ignored: Exception) {
            }
        }
        line = null
        lineSampleRate = -1
    }
}
