package com.example.dsp

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavAudioData(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val samples: FloatArray // Normalized -1.0f to 1.0f
)

object WavHelper {

    /**
     * Reads a 16-bit PCM WAV file and returns normalized FloatArray [-1.0f, 1.0f].
     */
    fun readWav(file: File): WavAudioData? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            // Check RIFF header
            val riff = String(bytes, 0, 4)
            if (riff != "RIFF") return null

            val wave = String(bytes, 8, 4)
            if (wave != "WAVE") return null

            var offset = 12
            var channels = 1
            var sampleRate = 44100
            var bitsPerSample = 16
            var dataOffset = -1
            var dataSize = 0

            while (offset + 8 <= bytes.size) {
                val chunkId = String(bytes, offset, 4)
                val chunkSize = buffer.getInt(offset + 4)
                if (chunkId == "fmt ") {
                    channels = buffer.getShort(offset + 10).toInt()
                    sampleRate = buffer.getInt(offset + 12)
                    bitsPerSample = buffer.getShort(offset + 22).toInt()
                } else if (chunkId == "data") {
                    dataOffset = offset + 8
                    dataSize = chunkSize
                    break
                }
                offset += 8 + chunkSize
            }

            if (dataOffset == -1 || dataOffset + dataSize > bytes.size) {
                dataOffset = 44
                dataSize = bytes.size - 44
            }

            val numSamples = dataSize / (bitsPerSample / 8) / channels
            val floatSamples = FloatArray(numSamples)

            var sampleIdx = 0
            var byteIdx = dataOffset
            val bytesPerFrame = (bitsPerSample / 8) * channels

            while (sampleIdx < numSamples && byteIdx + (bitsPerSample / 8) <= bytes.size) {
                val rawSample: Float = if (bitsPerSample == 16) {
                    val s = buffer.getShort(byteIdx)
                    s / 32768.0f
                } else {
                    // 8-bit unsigned fallback
                    val s = (bytes[byteIdx].toInt() and 0xFF) - 128
                    s / 128.0f
                }
                floatSamples[sampleIdx] = rawSample.coerceIn(-1.0f, 1.0f)
                sampleIdx++
                byteIdx += bytesPerFrame
            }

            WavAudioData(
                sampleRate = sampleRate,
                channels = 1, // Downmixed/mono representation
                bitsPerSample = bitsPerSample,
                samples = floatSamples
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Writes 16-bit PCM WAV file from normalized FloatArray [-1.0f, 1.0f].
     */
    fun writeWav(file: File, samples: FloatArray, sampleRate: Int = 44100, channels: Int = 1) {
        val totalAudioLen = samples.size * 2 * channels
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * 2

        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            val header = ByteArray(44)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            bb.put('R'.code.toByte()).put('I'.code.toByte()).put('F'.code.toByte()).put('F'.code.toByte())
            bb.putInt(totalDataLen)
            bb.put('W'.code.toByte()).put('A'.code.toByte()).put('V'.code.toByte()).put('E'.code.toByte())

            // fmt chunk
            bb.put('f'.code.toByte()).put('m'.code.toByte()).put('t'.code.toByte()).put(' '.code.toByte())
            bb.putInt(16) // Subchunk1Size for PCM
            bb.putShort(1.toShort()) // AudioFormat (1 = PCM)
            bb.putShort(channels.toShort())
            bb.putInt(sampleRate)
            bb.putInt(byteRate)
            bb.putShort((channels * 2).toShort()) // BlockAlign
            bb.putShort(16.toShort()) // BitsPerSample

            // data chunk
            bb.put('d'.code.toByte()).put('a'.code.toByte()).put('t'.code.toByte()).put('a'.code.toByte())
            bb.putInt(totalAudioLen)

            out.write(header)

            // Audio data
            val buffer = ByteArray(4096)
            var bufIdx = 0
            for (sample in samples) {
                val clamped = sample.coerceIn(-1.0f, 1.0f)
                val s16 = (clamped * 32767.0f).toInt().toShort()
                val byte1 = (s16.toInt() and 0xFF).toByte()
                val byte2 = ((s16.toInt() shr 8) and 0xFF).toByte()

                for (ch in 0 until channels) {
                    if (bufIdx + 2 > buffer.size) {
                        out.write(buffer, 0, bufIdx)
                        bufIdx = 0
                    }
                    buffer[bufIdx++] = byte1
                    buffer[bufIdx++] = byte2
                }
            }
            if (bufIdx > 0) {
                out.write(buffer, 0, bufIdx)
            }
        }
    }

    /**
     * Helper to write raw ShortArray PCM buffer to WAV file.
     */
    fun writeWavFromShorts(file: File, shorts: ShortArray, sampleRate: Int = 44100) {
        val floats = FloatArray(shorts.size) { i ->
            shorts[i] / 32768.0f
        }
        writeWav(file, floats, sampleRate, 1)
    }
}
