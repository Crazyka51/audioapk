package com.example.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {

    /**
     * Decodes an audio file (URI or local File) into a normalized FloatArray PCM [-1.0..1.0].
     */
    fun decodeToPcm(context: Context, uri: Uri): WavAudioData? {
        // If it's a standard WAV file, read directly for speed
        if (uri.path?.endsWith(".wav", ignoreCase = true) == true) {
            val localFile = File(uri.path ?: "")
            if (localFile.exists()) {
                val wav = WavHelper.readWav(localFile)
                if (wav != null) return wav
            }
        }

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

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var isEos = false
            val pcmFloats = ArrayList<Float>(sampleRate * 10) // 10s initial capacity

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
                                pcmFloats.add((sum / channelCount).coerceIn(-1.0f, 1.0f))
                            }
                        } else {
                            for (s in 0 until numShorts) {
                                val floatSample = shortBuffer.get(s) / 32768.0f
                                pcmFloats.add(floatSample.coerceIn(-1.0f, 1.0f))
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

            val samplesArray = FloatArray(pcmFloats.size)
            for (i in pcmFloats.indices) {
                samplesArray[i] = pcmFloats[i]
            }

            WavAudioData(
                sampleRate = sampleRate,
                channels = 1,
                bitsPerSample = 16,
                samples = samplesArray
            )
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
