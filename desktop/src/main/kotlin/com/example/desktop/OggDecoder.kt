package com.example.desktop

import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavAudioData
import javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * OGG Vorbis decoder.
 *
 * VorbisSPI registers through `META-INF/services`, which `AudioSystem` no longer honours for
 * classpath providers on modern JDKs, so the reader is instantiated directly instead of being
 * discovered. It returns a decoded PCM stream which [PcmStreamReader] converts to mono float.
 */
object OggDecoder {

    fun decode(file: File, maxFrames: Long = AudioMemoryBudget.maxFrames()): WavAudioData? {
        var primary: AudioInputStream? = null
        var secondary: AudioInputStream? = null
        return try {
            val decoded = VorbisAudioFileReader().getAudioInputStream(file)
            primary = decoded

            val pcmStream = if (isPcm(decoded.format)) {
                decoded
            } else {
                val target = AudioFormat(decoded.format.sampleRate, 16, decoded.format.channels, true, false)
                if (AudioSystem.isConversionSupported(target, decoded.format)) {
                    AudioSystem.getAudioInputStream(target, decoded).also { secondary = it }
                } else {
                    decoded
                }
            }

            PcmStreamReader.readMono(pcmStream, maxFrames)
        } catch (tooLarge: AudioTooLargeException) {
            throw tooLarge
        } catch (e: Exception) {
            DesktopAudioDecoder.recordError("OGG", e)
            null
        } finally {
            try {
                secondary?.close()
            } catch (ignored: Exception) {
            }
            try {
                primary?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun isPcm(format: AudioFormat): Boolean =
        format.encoding == AudioFormat.Encoding.PCM_SIGNED ||
            format.encoding == AudioFormat.Encoding.PCM_UNSIGNED
}
