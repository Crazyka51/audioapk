package com.example.desktop

import com.example.dsp.AudioMemoryBudget
import com.example.dsp.WavHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.abs
import kotlin.math.log10

data class RecordingStatus(
    val isRecording: Boolean = false,
    val durationMs: Long = 0L,
    val currentAmplitude: Float = 0f,
    val currentDb: Float = -90f
)

/**
 * Desktop counterpart of the Android `AudioRecorderManager`: records the default microphone into
 * raw PCM and writes a WAV on stop, so a live session ends up in the library exactly like a file
 * import does.
 */
class DesktopAudioRecorder {

    private val _status = MutableStateFlow(RecordingStatus())
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var line: TargetDataLine? = null

    @Volatile
    private var outputFile: File? = null

    private val sampleRate = 44100
    private val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)

    fun start(target: File): Boolean {
        if (_status.value.isRecording) return false

        val microphone = try {
            val info = DataLine.Info(TargetDataLine::class.java, format)
            (AudioSystem.getLine(info) as TargetDataLine).also {
                it.open(format, sampleRate / 4)
                it.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _error.value = "Mikrofon není dostupný: ${e.message}"
            return false
        }

        line = microphone
        outputFile = target
        _status.value = RecordingStatus(isRecording = true, currentDb = -90f)

        job = scope.launch {
            val pcm = ByteArrayOutputStream()
            val buffer = ByteArray(sampleRate / 10 * 2) // 100 ms blocks
            val startedAt = System.currentTimeMillis()
            val maxBytes = AudioMemoryBudget.maxFrames() * 2

            try {
                while (isActive && microphone.isOpen) {
                    val read = microphone.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    pcm.write(buffer, 0, read)

                    var peak = 0
                    var i = 0
                    while (i + 1 < read) {
                        val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()
                        val magnitude = abs(sample)
                        if (magnitude > peak) peak = magnitude
                        i += 2
                    }
                    val amplitude = (peak / 32768f).coerceIn(0f, 1f)
                    val db = if (amplitude > 1e-4f) {
                        (20 * log10(amplitude.toDouble())).toFloat().coerceIn(-90f, 0f)
                    } else -90f

                    _status.value = RecordingStatus(
                        isRecording = true,
                        durationMs = System.currentTimeMillis() - startedAt,
                        currentAmplitude = amplitude,
                        currentDb = db
                    )

                    if (pcm.size() > maxBytes) {
                        _error.value = "Nahrávka dosáhla limitu délky pro zpracování"
                        break
                    }
                }
            } finally {
                val bytes = pcm.toByteArray()
                val frames = bytes.size / 2
                val samples = FloatArray(frames)
                for (index in 0 until frames) {
                    val low = bytes[index * 2].toInt() and 0xFF
                    val high = bytes[index * 2 + 1].toInt()
                    samples[index] = (((high shl 8) or low).toShort() / 32768.0f).coerceIn(-1f, 1f)
                }
                if (frames > 0) {
                    WavHelper.writeWav(target, samples, sampleRate, 1)
                }
            }
        }
        return true
    }

    /**
     * Stops the microphone, waits for the WAV to be written and returns the produced file.
     * Suspending because the writer coroutine has to finish before the file is usable.
     */
    suspend fun stop(): File? {
        val microphone = line ?: return null
        line = null
        try {
            microphone.stop()
            microphone.close()
        } catch (ignored: Exception) {
        }
        job?.join()
        job = null
        _status.value = RecordingStatus()
        return outputFile
    }

    fun clearError() {
        _error.value = null
    }
}
