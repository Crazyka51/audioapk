package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.dsp.WavHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

data class RecordingStatus(
    val isRecording: Boolean = false,
    val durationMs: Long = 0L,
    val currentAmplitude: Float = 0f, // 0.0 to 1.0
    val currentDb: Float = -90f,
    val outputFile: File? = null
)

class AudioRecorderManager(private val context: Context) {

    private val _recordingStatus = MutableStateFlow(RecordingStatus())
    val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @SuppressLint("MissingPermission")
    fun startRecording(outputWavFile: File): Boolean {
        if (_recordingStatus.value.isRecording) return false

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) return false
        val bufferSize = max(minBufferSize * 2, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _recordingStatus.value = RecordingStatus(isRecording = true, outputFile = outputWavFile)

            recordingJob = scope.launch {
                val tempRawPcm = File(outputWavFile.parentFile, "temp_rec.raw")
                val pcmOutputStream = FileOutputStream(tempRawPcm)
                val buffer = ShortArray(bufferSize / 2)
                var totalShortsRead = 0L
                val startTime = System.currentTimeMillis()

                try {
                    while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (read > 0) {
                            var maxAmp = 0
                            val byteBuffer = ByteArray(read * 2)
                            for (i in 0 until read) {
                                val s = buffer[i]
                                val absVal = abs(s.toInt())
                                if (absVal > maxAmp) maxAmp = absVal

                                byteBuffer[i * 2] = (s.toInt() and 0xFF).toByte()
                                byteBuffer[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                            }
                            pcmOutputStream.write(byteBuffer)
                            totalShortsRead += read

                            val normalizedAmp = (maxAmp / 32768f).coerceIn(0f, 1f)
                            val db = if (normalizedAmp > 1e-4f) {
                                (20.0 * log10(normalizedAmp.toDouble())).toFloat().coerceIn(-90f, 0f)
                            } else -90f

                            val elapsed = System.currentTimeMillis() - startTime
                            _recordingStatus.value = RecordingStatus(
                                isRecording = true,
                                durationMs = elapsed,
                                currentAmplitude = normalizedAmp,
                                currentDb = db,
                                outputFile = outputWavFile
                            )
                        }
                    }
                } finally {
                    pcmOutputStream.close()

                    // Convert raw PCM to proper WAV file
                    if (tempRawPcm.exists()) {
                        val rawBytes = tempRawPcm.readBytes()
                        val numShorts = rawBytes.size / 2
                        val floatSamples = FloatArray(numShorts)
                        for (i in 0 until numShorts) {
                            val b1 = rawBytes[i * 2].toInt() and 0xFF
                            val b2 = rawBytes[i * 2 + 1].toInt()
                            val s = (b2 shl 8) or b1
                            floatSamples[i] = (s.toShort() / 32768.0f).coerceIn(-1.0f, 1.0f)
                        }
                        WavHelper.writeWav(outputWavFile, floatSamples, sampleRate, 1)
                        tempRawPcm.delete()
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            return false
        }
    }

    fun stopRecording(): File? {
        val file = _recordingStatus.value.outputFile
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
            recordingJob?.cancel()
            recordingJob = null
            _recordingStatus.value = RecordingStatus(isRecording = false, outputFile = file)
        }
        return file
    }
}
