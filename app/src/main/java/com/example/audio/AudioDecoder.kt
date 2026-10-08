package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavAudioData
import com.example.dsp.WavHelper
import java.io.BufferedInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {

    /**
     * Decodes an audio file (URI or content stream) into normalized mono FloatArray PCM [-1.0..1.0].
     *
     * Nothing ever loads the raw file into memory: WAV data is streamed in bounded chunks and other
     * formats go through [MediaCodec] directly into a growing float buffer.
     *
     * @throws AudioTooLargeException when the decoded PCM would not fit into the heap. Callers are
     *   expected to report that to the user; retrying will not help.
     */
    fun decodeToPcm(
        context: Context,
        uri: Uri,
        maxFrames: Long = AudioMemoryBudget.maxFrames()
    ): WavAudioData? {
        // 1. Local file: stream it straight from disk.
        val localFile = uri.path?.takeIf { uri.scheme == "file" || uri.scheme == null }?.let { File(it) }
        if (localFile != null && localFile.exists()) {
            if (localFile.name.endsWith(".wav", ignoreCase = true) &&
                WavHelper.readWavHeader(localFile) != null
            ) {
                return WavHelper.readWav(localFile, maxFrames)
            }
            return decodeWithMediaCodec(context, uri, maxFrames)
        }

        // 2. content:// stream: sniff RIFF/WAVE on the same stream that is then decoded.
        val streamed = try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                val input = if (raw is BufferedInputStream) raw else BufferedInputStream(raw, 64 * 1024)
                if (isRiffWave(input)) WavHelper.readWavFromStream(input, maxFrames) else null
            }
        } catch (tooLarge: AudioTooLargeException) {
            throw tooLarge
        } catch (ignored: Exception) {
            null
        }
        if (streamed != null) return streamed

        return decodeWithMediaCodec(context, uri, maxFrames)
    }

    private fun isRiffWave(input: BufferedInputStream): Boolean {
        input.mark(12)
        val header = ByteArray(12)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            if (n <= 0) break
            read += n
        }
        input.reset()
        return read == header.size &&
            String(header, 0, 4) == "RIFF" &&
            String(header, 8, 4) == "WAVE"
    }

    private fun decodeWithMediaCodec(context: Context, uri: Uri, maxFrames: Long): WavAudioData? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            // Refuse files that cannot fit into the heap before allocating anything large.
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            val estimatedFrames = if (durationUs > 0) {
                (durationUs / 1_000_000.0 * sampleRate).toLong()
            } else 0L
            if (estimatedFrames > maxFrames) {
                throw AudioTooLargeException(
                    requiredPcmBytes = estimatedFrames * Float.SIZE_BYTES,
                    limitPcmBytes = maxFrames * Float.SIZE_BYTES
                )
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var isEos = false
            // Growable float sink. Boxing every sample into an ArrayList<Float> would cost ~16 bytes
            // per sample and blow the heap on long recordings.
            var pcm = FloatArray(
                if (estimatedFrames > 0) {
                    estimatedFrames.toInt().coerceAtLeast(4096)
                } else {
                    4096 * 128
                }
            )
            var pcmCount = 0

            fun appendSample(value: Float) {
                if (pcmCount == pcm.size) {
                    val grownSize = pcm.size * 2
                    if (grownSize.toLong() > maxFrames) {
                        throw AudioTooLargeException(
                            requiredPcmBytes = grownSize.toLong() * Float.SIZE_BYTES,
                            limitPcmBytes = maxFrames * Float.SIZE_BYTES
                        )
                    }
                    pcm = pcm.copyOf(grownSize)
                }
                pcm[pcmCount++] = value
            }

            val kTimeoutUs = 5000L

            while (!isEos) {
                val inIndex = codec.dequeueInputBuffer(kTimeoutUs)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(info, kTimeoutUs)
                while (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && info.size > 0) {
                        outBuffer.position(info.offset)
                        outBuffer.limit(info.offset + info.size)
                        outBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val shortBuffer = outBuffer.asShortBuffer()
                        val numShorts = info.size / 2

                        if (channelCount > 1) {
                            // Downmix stereo/multichannel to mono
                            for (s in 0 until numShorts step channelCount) {
                                var sum = 0f
                                for (ch in 0 until channelCount) {
                                    if (s + ch < numShorts) {
                                        sum += shortBuffer.get(s + ch) / 32768.0f
                                    }
                                }
                                appendSample((sum / channelCount).coerceIn(-1.0f, 1.0f))
                            }
                        } else {
                            for (s in 0 until numShorts) {
                                appendSample((shortBuffer.get(s) / 32768.0f).coerceIn(-1.0f, 1.0f))
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(info, 0)
                }
            }

            val samplesArray = if (pcmCount == pcm.size) pcm else pcm.copyOf(pcmCount)

            WavAudioData(
                sampleRate = sampleRate,
                channels = 1,
                bitsPerSample = 16,
                samples = samplesArray
            )
        } catch (tooLarge: AudioTooLargeException) {
            throw tooLarge
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                codec?.stop()
                codec?.release()
                extractor.release()
            } catch (ignored: Exception) {}
        }
    }
}
