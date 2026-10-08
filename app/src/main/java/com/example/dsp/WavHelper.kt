package com.example.dsp

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavAudioData(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val samples: FloatArray // Normalized -1.0f to 1.0f
)

data class WavHeader(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val frameCount: Long,
    val dataSizeBytes: Long
)

/**
 * Raised when the decoded mono PCM of a recording would not fit into the process heap.
 * The check runs right after the header is parsed, so no large allocation is attempted first.
 */
class AudioTooLargeException(
    val requiredPcmBytes: Long,
    val limitPcmBytes: Long
) : Exception(
    "Decoded PCM needs $requiredPcmBytes bytes, the limit is $limitPcmBytes bytes"
)

object WavHelper {

    private const val READ_BUFFER_BYTES = 64 * 1024
    private const val MAX_FMT_CHUNK_BYTES = 64

    /**
     * Reads only the RIFF/WAVE header, without touching the audio payload. Cheap enough to call for
     * every file in the library, which is why metadata listing never loads samples into memory.
     */
    fun readWavHeader(file: File): WavHeader? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            BufferedInputStream(file.inputStream(), READ_BUFFER_BYTES).use { readWavHeader(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parses the RIFF/WAVE header from a stream. On return the stream is positioned on the first PCM
     * byte of the `data` chunk, so the caller can pipe it straight into [readWavFromStream].
     */
    fun readWavHeader(input: InputStream): WavHeader? {
        val riff = ByteArray(12)
        if (!readFully(input, riff)) return null
        if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") return null

        var channels = 1
        var sampleRate = 44100
        var bitsPerSample = 16
        var dataSizeBytes = -1L

        val chunkHeader = ByteArray(8)
        while (true) {
            if (!readFully(input, chunkHeader)) return null
            val chunkId = String(chunkHeader, 0, 4)
            val chunkSize = leInt(chunkHeader, 4).toLong() and 0xFFFFFFFFL

            when (chunkId) {
                "fmt " -> {
                    val fmt = ByteArray(chunkSize.coerceAtMost(MAX_FMT_CHUNK_BYTES.toLong()).toInt())
                    if (!readFully(input, fmt) || fmt.size < 16) return null
                    channels = leShort(fmt, 2).coerceAtLeast(1)
                    sampleRate = leInt(fmt, 4).takeIf { it > 0 } ?: 44100
                    bitsPerSample = leShort(fmt, 14).takeIf { it > 0 } ?: 16
                    skipFully(input, chunkSize - fmt.size + (chunkSize and 1L))
                }
                "data" -> {
                    dataSizeBytes = chunkSize
                    break
                }
                else -> skipFully(input, chunkSize + (chunkSize and 1L))
            }
        }

        val bytesPerFrame = (bitsPerSample / 8).coerceAtLeast(1) * channels
        return WavHeader(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            frameCount = dataSizeBytes / bytesPerFrame,
            dataSizeBytes = dataSizeBytes
        )
    }

    /**
     * Reads a PCM WAV file into normalized mono floats without loading the raw file into memory.
     *
     * @throws AudioTooLargeException when the decoded PCM would exceed [maxFrames].
     */
    fun readWav(file: File, maxFrames: Long = AudioMemoryBudget.maxFrames()): WavAudioData? {
        if (!file.exists() || file.length() < 44) return null
        return try {
            BufferedInputStream(file.inputStream(), READ_BUFFER_BYTES).use { readWavFromStream(it, maxFrames) }
        } catch (tooLarge: AudioTooLargeException) {
            throw tooLarge
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes the `data` chunk of a WAV stream into normalized mono float PCM [-1.0f, 1.0f] in
     * bounded chunks. Multi-channel audio is properly averaged down to mono.
     *
     * @throws AudioTooLargeException when the decoded PCM would exceed [maxFrames].
     */
    fun readWavFromStream(input: InputStream, maxFrames: Long = AudioMemoryBudget.maxFrames()): WavAudioData? {
        val header = readWavHeader(input) ?: return null
        if (header.frameCount > maxFrames) {
            throw AudioTooLargeException(
                requiredPcmBytes = header.frameCount * Float.SIZE_BYTES,
                limitPcmBytes = maxFrames * Float.SIZE_BYTES
            )
        }

        val samples = FloatArray(header.frameCount.toInt())
        val bytesPerSample = (header.bitsPerSample / 8).coerceAtLeast(1)
        val bytesPerFrame = bytesPerSample * header.channels
        val chunkFrames = (READ_BUFFER_BYTES / bytesPerFrame).coerceAtLeast(1)
        val buffer = ByteArray(chunkFrames * bytesPerFrame)

        var frameIndex = 0
        var remaining = header.frameCount
        while (remaining > 0) {
            val framesToRead = minOf(remaining, chunkFrames.toLong()).toInt()
            val bytesToRead = framesToRead * bytesPerFrame
            if (!readFully(input, buffer, bytesToRead)) break

            for (f in 0 until framesToRead) {
                val frameBase = f * bytesPerFrame
                var sum = 0f
                for (ch in 0 until header.channels) {
                    sum += pcmSampleToFloat(buffer, frameBase + ch * bytesPerSample, header.bitsPerSample)
                }
                samples[frameIndex++] = (sum / header.channels).coerceIn(-1.0f, 1.0f)
            }
            remaining -= framesToRead
        }

        val decoded = if (frameIndex == samples.size) samples else samples.copyOf(frameIndex)
        return WavAudioData(
            sampleRate = header.sampleRate,
            channels = 1,
            bitsPerSample = 16,
            samples = decoded
        )
    }

    private fun pcmSampleToFloat(bytes: ByteArray, offset: Int, bitsPerSample: Int): Float =
        when (bitsPerSample) {
            16 -> leShort(bytes, offset).toShort() / 32768.0f
            24 -> {
                val b0 = bytes[offset].toInt() and 0xFF
                val b1 = bytes[offset + 1].toInt() and 0xFF
                val b2 = bytes[offset + 2].toInt() // signed MSB, sign-extends the 24-bit value
                ((b2 shl 16) or (b1 shl 8) or b0) / 8388608.0f
            }
            32 -> leInt(bytes, offset) / 2147483648.0f
            else -> ((bytes[offset].toInt() and 0xFF) - 128) / 128.0f
        }

    private fun leShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readFully(input: InputStream, target: ByteArray, length: Int = target.size): Boolean {
        var read = 0
        while (read < length) {
            val n = input.read(target, read, length - read)
            if (n <= 0) return false
            read += n
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() < 0) {
                return
            } else {
                remaining--
            }
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
