package com.example.desktop

import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavAudioData
import java.io.InputStream

/**
 * Converts any PCM [InputStream] into the mono float format the app uses internally.
 *
 * Shared by the compressed-format decoders, which obtain a decoded PCM stream (from a service
 * provider instantiated directly or from a decoder API) and then need the same 16-bit downmix and
 * 24/32-bit handling the WAV reader performs.
 */
object PcmStreamReader {

    /** A provider that keeps returning 0 without producing data is treated as broken. */
    private const val MAX_EMPTY_READS = 64

    fun readMono(stream: InputStream, maxFrames: Long = AudioMemoryBudget.maxFrames()): WavAudioData? {
        val format = when (stream) {
            is javax.sound.sampled.AudioInputStream -> stream.format
            else -> return null
        }
        val audio = stream as javax.sound.sampled.AudioInputStream

        val sampleRate = format.sampleRate.toInt().takeIf { it > 0 } ?: 44100
        val channels = format.channels.coerceAtLeast(1)
        val bits = format.sampleSizeInBits.takeIf { it > 0 } ?: 16
        val bytesPerSample = (bits / 8).coerceAtLeast(1)
        val bytesPerFrame = bytesPerSample * channels

        val knownFrames = audio.frameLength.takeIf { it > 0 }?.toLong()
        if (knownFrames != null && knownFrames > maxFrames) {
            throw AudioTooLargeException(
                requiredPcmBytes = knownFrames * Float.SIZE_BYTES,
                limitPcmBytes = maxFrames * Float.SIZE_BYTES
            )
        }

        var samples = FloatArray(
            (knownFrames ?: (sampleRate * 8L)).toInt().coerceAtLeast(4096)
        )
        var count = 0

        val chunkFrames = (64 * 1024 / bytesPerFrame).coerceAtLeast(1)
        val buffer = ByteArray(chunkFrames * bytesPerFrame)

        // Some providers (notably VorbisSPI) can return 0 before they have data ready, which is not
        // end of stream. Only a negative result ends the loop, and a run of empty reads guards
        // against a stream that never delivers.
        var emptyReads = 0
        while (true) {
            val read = audio.read(buffer)
            if (read < 0) break
            if (read == 0) {
                if (++emptyReads > MAX_EMPTY_READS) break
                continue
            }
            emptyReads = 0
            val frames = read / bytesPerFrame
            if (frames == 0) continue

            if (count + frames > samples.size) {
                val grown = (count + frames).coerceAtLeast(samples.size * 2)
                if (grown.toLong() > maxFrames) {
                    throw AudioTooLargeException(
                        requiredPcmBytes = grown.toLong() * Float.SIZE_BYTES,
                        limitPcmBytes = maxFrames * Float.SIZE_BYTES
                    )
                }
                samples = samples.copyOf(grown)
            }

            for (frame in 0 until frames) {
                var sum = 0f
                for (channel in 0 until channels) {
                    val offset = frame * bytesPerFrame + channel * bytesPerSample
                    sum += sampleAt(buffer, offset, bits, format.isBigEndian)
                }
                samples[count++] = (sum / channels).coerceIn(-1f, 1f)
            }
        }

        if (count == 0) return null
        return WavAudioData(
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
            samples = if (count == samples.size) samples else samples.copyOf(count)
        )
    }

    private fun sampleAt(bytes: ByteArray, offset: Int, bits: Int, bigEndian: Boolean): Float {
        if (offset + (bits / 8) > bytes.size) return 0f
        return if (bigEndian) {
            when (bits) {
                8 -> ((bytes[offset].toInt() and 0xFF) - 128) / 128.0f
                16 -> (((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)).toShort() / 32768.0f
                24 -> {
                    val value = ((bytes[offset].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 2].toInt() and 0xFF)
                    (value shl 8) / 2147483648.0f
                }
                else -> (((bytes[offset].toInt() and 0xFF) shl 24) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)) / 2147483648.0f
            }
        } else {
            when (bits) {
                8 -> ((bytes[offset].toInt() and 0xFF) - 128) / 128.0f
                16 -> (((bytes[offset + 1].toInt() and 0xFF) shl 8) or (bytes[offset].toInt() and 0xFF)).toShort() / 32768.0f
                24 -> {
                    val value = ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                        (bytes[offset].toInt() and 0xFF)
                    (value shl 8) / 2147483648.0f
                }
                else -> (((bytes[offset + 3].toInt() and 0xFF) shl 24) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    (bytes[offset].toInt() and 0xFF)) / 2147483648.0f
            }
        }
    }
}
