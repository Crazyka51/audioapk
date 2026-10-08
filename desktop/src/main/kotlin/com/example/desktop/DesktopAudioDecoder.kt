package com.example.desktop

import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavAudioData
import com.example.dsp.WavHelper
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Desktop counterpart of the Android `AudioDecoder`.
 *
 * Android decodes through MediaCodec/MediaExtractor; on the JVM the platform audio stack comes from
 * `javax.sound.sampled`, which reads WAV, AU and AIFF out of the box. Plain WAV is still routed
 * through [WavHelper] so 24-bit and 32-bit files, multi-channel downmixing and the memory budget
 * behave exactly as on Android.
 */
object DesktopAudioDecoder {

    /**
     * Extensions the JVM audio stack can decode. WAV/AU/AIFF are built in; MP3, OGG Vorbis and FLAC
     * come from the Service Providers declared in the module dependencies, which is how voice memos
     * and archival recordings imported from a phone are handled.
     */
    val SUPPORTED_EXTENSIONS = listOf(
        "wav", "wave", "au", "aiff", "aif", "snd",
        "mp3", "flac", "ogg", "oga"
    )

    /** Extensions known to be unsupported, so the UI can explain the reason instead of failing silently. */
    val UNSUPPORTED_EXTENSIONS = listOf("m4a", "aac", "wma", "opus", "amr")

    /** Human readable list used in user-facing messages. */
    val SUPPORTED_LABEL: String = SUPPORTED_EXTENSIONS.joinToString(", ") { it.uppercase() }

    fun decode(file: File, maxFrames: Long = AudioMemoryBudget.maxFrames()): WavAudioData? {
        if (!file.exists()) return null
        lastError = null

        when (file.extension.lowercase()) {
            "wav", "wave" -> {
                val header = WavHelper.readWavHeader(file)
                if (header != null) return WavHelper.readWav(file, maxFrames)
            }
            "mp3" -> return Mp3Decoder.decode(file, maxFrames)
            "flac" -> return FlacDecoder.decode(file, maxFrames)
            "ogg", "oga" -> return OggDecoder.decode(file, maxFrames)
        }
        return decodeWithSampled(file, maxFrames)
    }

    /**
     * Last decode failure, used by the UI to explain why a file could not be read instead of showing
     * a generic message.
     */
    @Volatile
    var lastError: String? = null
        private set

    /** Records a failure from one of the format specific decoders. */
    internal fun recordError(format: String, error: Throwable) {
        lastError = "$format dekodér selhal: ${error::class.simpleName}: ${error.message}"
    }

    /** True when the extension is one the app can decode, used to reject files up front. */
    fun isSupported(file: File): Boolean = file.extension.lowercase() in SUPPORTED_EXTENSIONS

    /** Decodes through `javax.sound.sampled` and converts the result to mono float PCM. */
    private fun decodeWithSampled(file: File, maxFrames: Long): WavAudioData? {
        var stream: AudioInputStream? = null
        return try {
            stream = AudioSystem.getAudioInputStream(file)
            val format = stream.format
            val sampleRate = format.sampleRate.toInt().takeIf { it > 0 } ?: 44100

            val totalFrames = stream.frameLength.takeIf { it > 0 }?.toLong()
            if (totalFrames != null && totalFrames > maxFrames) {
                throw AudioTooLargeException(
                    requiredPcmBytes = totalFrames * Float.SIZE_BYTES,
                    limitPcmBytes = maxFrames * Float.SIZE_BYTES
                )
            }

            val channels = format.channels.coerceAtLeast(1)
            val bytesPerFrame = channels * (format.sampleSizeInBits / 8).coerceAtLeast(1)
            val chunkFrames = (64 * 1024 / bytesPerFrame).coerceAtLeast(1)
            val buffer = ByteArray(chunkFrames * bytesPerFrame)

            var samples = FloatArray(
                (totalFrames ?: (sampleRate * 8L)).toInt().coerceAtLeast(4096)
            )
            var count = 0

            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                val frames = read / bytesPerFrame
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
                for (f in 0 until frames) {
                    val base = f * bytesPerFrame
                    var sum = 0f
                    for (ch in 0 until channels) {
                        sum += sampleAsFloat(buffer, base + ch * (bytesPerFrame / channels), format)
                    }
                    samples[count++] = (sum / channels).coerceIn(-1f, 1f)
                }
            }

            WavAudioData(
                sampleRate = sampleRate,
                channels = 1,
                bitsPerSample = 16,
                samples = if (count == samples.size) samples else samples.copyOf(count)
            )
        } catch (tooLarge: AudioTooLargeException) {
            throw tooLarge
        } catch (e: Exception) {
            lastError = "Soubor se nepodařilo dekódovat: ${e::class.simpleName}: ${e.message}"
            e.printStackTrace()
            null
        } finally {
            try {
                stream?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun sampleAsFloat(bytes: ByteArray, offset: Int, format: AudioFormat): Float {
        if (format.isBigEndian) {
            return when (format.sampleSizeInBits) {
                8 -> ((bytes[offset].toInt() and 0xFF) - 128) / 128.0f
                16 -> (((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)).toShort() / 32768.0f
                24 -> {
                    val v = ((bytes[offset].toInt() and 0xFF) shl 16) or
                        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                        (bytes[offset + 2].toInt() and 0xFF)
                    (v shl 8) / 2147483648.0f
                }
                else -> (((bytes[offset].toInt() and 0xFF) shl 24) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)) / 2147483648.0f
            }
        }
        return when (format.sampleSizeInBits) {
            8 -> ((bytes[offset].toInt() and 0xFF) - 128) / 128.0f
            16 -> (((bytes[offset + 1].toInt() and 0xFF) shl 8) or (bytes[offset].toInt() and 0xFF)).toShort() / 32768.0f
            24 -> {
                val v = ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    (bytes[offset].toInt() and 0xFF)
                (v shl 8) / 2147483648.0f
            }
            else -> (((bytes[offset + 3].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                (bytes[offset].toInt() and 0xFF)) / 2147483648.0f
        }
    }
}
