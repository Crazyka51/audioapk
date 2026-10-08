package com.example.desktop

import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavAudioData
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.BufferedInputStream
import java.io.File

/**
 * Direct MP3 decoder built on JLayer.
 *
 * MP3SPI registers itself through `META-INF/services`, but since JDK 9 `AudioSystem` no longer picks
 * up providers that live on the application classpath, so every imported MP3 failed with
 * "unsupported format". Driving JLayer directly bypasses the service loader completely and lets the
 * decoder honour the shared memory budget while streaming frames.
 */
object Mp3Decoder {

    fun decode(file: File, maxFrames: Long = AudioMemoryBudget.maxFrames()): WavAudioData? {
        val bitstream = Bitstream(BufferedInputStream(file.inputStream(), 64 * 1024))
        val decoder = Decoder()
        return try {
            var header = bitstream.readFrame() ?: return null
            var samples = FloatArray(INITIAL_CAPACITY_FRAMES)
            var count = 0
            var sampleRate = 44100

            while (true) {
                // JLayer declares the return type as Obuffer; the MPEG decoder always returns a
                // SampleBuffer, which exposes the sample rate, channel count and 16-bit samples.
                val frame = decoder.decodeFrame(header, bitstream) as SampleBuffer
                sampleRate = frame.sampleFrequency.takeIf { it > 0 } ?: sampleRate
                val channels = frame.channelCount.coerceAtLeast(1)
                val shorts = frame.buffer
                val used = frame.bufferLength
                val frames = used / channels

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

                // JLayer reuses one buffer instance, so the samples have to be copied out per frame.
                for (f in 0 until frames) {
                    var sum = 0f
                    for (channel in 0 until channels) {
                        val index = f * channels + channel
                        if (index < used) sum += shorts[index] / 32768.0f
                    }
                    samples[count++] = (sum / channels).coerceIn(-1f, 1f)
                }

                bitstream.closeFrame()
                header = bitstream.readFrame() ?: break
            }

            if (count == 0) return null
            WavAudioData(
                sampleRate = sampleRate,
                channels = 1,
                bitsPerSample = 16,
                samples = if (count == samples.size) samples else samples.copyOf(count)
            )
        } catch (tooLarge: AudioTooLargeException) {
            throw tooLarge
        } catch (e: Exception) {
            DesktopAudioDecoder.recordError("MP3", e)
            null
        } finally {
            try {
                bitstream.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private const val INITIAL_CAPACITY_FRAMES = 4096 * 128
}
