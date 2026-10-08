package com.example.desktop

import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavAudioData
import org.jflac.FLACDecoder
import org.jflac.PCMProcessor
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData
import java.io.File
import java.io.FileInputStream

/**
 * Direct FLAC decoder built on jFLAC, needed for the same reason as [Mp3Decoder]: the jFLAC SPI
 * provider is not discovered by `AudioSystem` on JDK 9+, so FLAC imports reported an unsupported
 * format. The decoder collects PCM through the [PCMProcessor] callback and downmixes to mono.
 */
object FlacDecoder {

    fun decode(file: File, maxFrames: Long = AudioMemoryBudget.maxFrames()): WavAudioData? {
        var samples: FloatArray? = null
        var count = 0
        var sampleRate = 44100
        var channels = 1

        val processor = object : PCMProcessor {
            override fun processStreamInfo(info: StreamInfo) {
                sampleRate = info.sampleRate.takeIf { it > 0 } ?: 44100
                channels = info.channels.coerceAtLeast(1)
            }

            override fun processPCM(data: ByteData) {
                val existing = samples ?: FloatArray(INITIAL_CAPACITY_FRAMES).also { samples = it }
                val current = existing
                val bytes = data.data
                val total = data.len
                val bytesPerSample = 2 // jFLAC hands over 16-bit samples by default
                val frames = total / (bytesPerSample * channels)
                if (frames <= 0) return

                if (count + frames > current.size) {
                    val grown = (count + frames).coerceAtLeast(current.size * 2)
                    if (grown.toLong() > maxFrames) {
                        throw AudioTooLargeException(
                            requiredPcmBytes = grown.toLong() * Float.SIZE_BYTES,
                            limitPcmBytes = maxFrames * Float.SIZE_BYTES
                        )
                    }
                    samples = current.copyOf(grown)
                }
                val target = samples ?: return

                for (frame in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) {
                        val index = (frame * channels + channel) * bytesPerSample
                        if (index + 1 >= total) continue
                        val low = bytes[index].toInt() and 0xFF
                        val high = bytes[index + 1].toInt()
                        sum += ((high shl 8) or low).toShort() / 32768.0f
                    }
                    target[count++] = (sum / channels).coerceIn(-1f, 1f)
                }
            }
        }

        FileInputStream(file).use { input ->
            try {
                val decoder = FLACDecoder(input)
                decoder.addPCMProcessor(processor)
                decoder.decode()
            } catch (tooLarge: AudioTooLargeException) {
                throw tooLarge
            } catch (e: Exception) {
                DesktopAudioDecoder.recordError("FLAC", e)
                return null
            }
        }

        val decoded = samples ?: return null
        if (count == 0) return null
        return WavAudioData(
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
            samples = if (count == decoded.size) decoded else decoded.copyOf(count)
        )
    }

    private const val INITIAL_CAPACITY_FRAMES = 4096 * 128
}
