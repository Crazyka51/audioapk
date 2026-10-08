package com.example.desktop

import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavHelper
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the desktop decoding paths. WAV goes through the shared [WavHelper]; compressed formats go
 * through the JVM audio stack with the MP3/FLAC/OGG service providers this module declares, which is
 * what failed for imported MP3 voice memos before.
 */
class DesktopAudioDecoderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun decodesWavThroughTheSharedHelper() {
        val file = tempFolder.newFile("tone.wav")
        val sampleRate = 44100
        val frames = 22050
        val samples = FloatArray(frames) { i ->
            (0.5 * sin(2.0 * PI * 440.0 * i / sampleRate)).toFloat()
        }
        WavHelper.writeWav(file, samples, sampleRate, 1)

        val decoded = DesktopAudioDecoder.decode(file)

        assertNotNull(decoded)
        assertEquals(sampleRate, decoded!!.sampleRate)
        assertEquals(frames, decoded.samples.size)
        var maxError = 0f
        for (i in 0 until frames) {
            maxError = maxOf(maxError, abs(decoded.samples[i] - samples[i]))
        }
        assertTrue(maxError < 1e-3f, "16-bit round trip should stay within one LSB, was $maxError")
    }

    @Test
    fun rejectsUnsupportedExtensionUpFront() {
        val file = tempFolder.newFile("voice.m4a")
        file.writeBytes(ByteArray(64))

        assertTrue(DesktopAudioDecoder.isSupported(File("clip.wav")))
        assertTrue(DesktopAudioDecoder.isSupported(File("memo.mp3")))
        assertTrue(!DesktopAudioDecoder.isSupported(file))
    }

    @Test
    fun enforcesTheMemoryBudgetOnWav() {
        val file = tempFolder.newFile("long.wav")
        val sampleRate = 44100
        val frames = 20000
        WavHelper.writeWav(file, FloatArray(frames), sampleRate, 1)

        var thrown: AudioTooLargeException? = null
        try {
            DesktopAudioDecoder.decode(file, maxFrames = 1000)
        } catch (e: AudioTooLargeException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertEquals(1000 * Float.SIZE_BYTES.toLong(), thrown!!.limitPcmBytes)
        assertTrue(AudioMemoryBudget.maxFrames() > 0)
    }

    @Test
    fun returnsNullForGarbageData() {
        val file = tempFolder.newFile("broken.wav")
        file.writeBytes(ByteArray(128) { 0x5A })

        assertNull(WavHelper.readWavHeader(file))
        assertNull(DesktopAudioDecoder.decode(file))
    }

    /**
     * Decodes real compressed files when fixtures are provided with
     * -Dfixture.mp3= / -Dfixture.flac= / -Dfixture.ogg=. These are exactly the imports that used to
     * fail: MP3SPI/VorbisSPI/jFLAC register through META-INF/services, which AudioSystem no longer
     * honours on modern JDKs, so the app drives the decoders directly.
     */
    @Test
    fun decodesCompressedFixturesWhenProvided() {
        val fixtures = listOf(
            "MP3" to System.getProperty("fixture.mp3").orEmpty(),
            "FLAC" to System.getProperty("fixture.flac").orEmpty(),
            "OGG" to System.getProperty("fixture.ogg").orEmpty()
        )

        var checked = 0
        for ((format, path) in fixtures) {
            if (path.isBlank()) continue
            val file = File(path)
            if (!file.exists()) continue
            checked++

            val decoded = DesktopAudioDecoder.decode(file)

            assertNotNull(
                decoded,
                "$format fixture should decode through its dedicated decoder " +
                    "(lastError=${DesktopAudioDecoder.lastError})"
            )
            assertTrue(decoded!!.samples.isNotEmpty(), "$format produced no samples")
            assertTrue(decoded.sampleRate > 0, "$format produced no sample rate")

            // Silence would mean the format was recognised but the payload was not decoded.
            var peak = 0f
            for (sample in decoded.samples) peak = maxOf(peak, abs(sample))
            assertTrue(peak > 0.001f, "$format decoded to silence (peak=$peak)")
        }

        if (checked == 0) {
            println("No compressed fixtures provided; set -Dfixture.mp3=... to cover them.")
        }
    }

    /** Reports which audio formats the JVM exposes, to keep the UI honest about support. */
    @Test
    fun reportsDecodableFormats() {
        val types = javax.sound.sampled.AudioSystem.getAudioFileTypes()
        println("JVM built-in decodable file types: " + types.joinToString { it.extension })
        // MP3, FLAC and OGG are decoded by dedicated decoders because these SPI providers are not
        // discovered by AudioSystem on modern JDKs.
        println("App adds: " + DesktopAudioDecoder.SUPPORTED_EXTENSIONS.joinToString(", "))
    }
}
