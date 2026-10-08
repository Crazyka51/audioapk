package com.example

import com.example.dsp.AudioMemoryBudget
import com.example.dsp.AudioTooLargeException
import com.example.dsp.WavHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Covers the streamed WAV reading path. Importing a long recording used to load the whole file into
 * a ByteArray and crash with OutOfMemoryError, so both the streaming behaviour and the size guard
 * are verified here.
 */
class WavHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun readWavHeader_reportsFormatAndLengthWithoutDecoding() {
        val sampleRate = 48000
        val frames = 5000
        val file = writeStereoWav(tempFolder.newFile("stereo.wav"), sampleRate, frames)

        val header = WavHelper.readWavHeader(file)

        assertNotNull(header)
        assertEquals(sampleRate, header!!.sampleRate)
        assertEquals(2, header.channels)
        assertEquals(16, header.bitsPerSample)
        assertEquals(frames.toLong(), header.frameCount)
    }

    @Test
    fun readWav_averagesChannelsIntoMono() {
        val sampleRate = 44100
        val frames = 1000
        val file = writeStereoWav(tempFolder.newFile("downmix.wav"), sampleRate, frames)

        val decoded = WavHelper.readWav(file)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.channels)
        assertEquals(sampleRate, decoded.sampleRate)
        assertEquals(frames, decoded.samples.size)
        // Left channel is 0.5, right channel is -0.5, so the mono average is silence.
        for (sample in decoded.samples) {
            assertTrue("Expected downmixed silence, got $sample", abs(sample) < 1e-4f)
        }
    }

    @Test
    fun readWav_preservesMonoSignal() {
        val sampleRate = 44100
        val frames = 4096
        val file = tempFolder.newFile("mono.wav")
        val samples = FloatArray(frames) { i -> (0.6 * sin(2.0 * PI * 500.0 * i / sampleRate)).toFloat() }
        WavHelper.writeWav(file, samples, sampleRate, 1)

        val decoded = WavHelper.readWav(file)

        assertNotNull(decoded)
        assertEquals(frames, decoded!!.samples.size)
        var maxError = 0f
        for (i in 0 until frames) {
            maxError = maxOf(maxError, abs(decoded.samples[i] - samples[i]))
        }
        assertTrue("16-bit round trip should stay within one LSB, error was $maxError", maxError < 1e-3f)
    }

    @Test
    fun readWav_throwsWhenRecordingExceedsMemoryBudget() {
        val sampleRate = 44100
        val frames = 20000
        val file = writeStereoWav(tempFolder.newFile("too_long.wav"), sampleRate, frames)

        val thrown = assertThrows(AudioTooLargeException::class.java) {
            WavHelper.readWav(file, maxFrames = 1000)
        }

        assertEquals(frames * Float.SIZE_BYTES.toLong(), thrown.requiredPcmBytes)
        assertEquals(1000 * Float.SIZE_BYTES.toLong(), thrown.limitPcmBytes)
    }

    @Test
    fun readWavHeader_returnsNullForNonWavData() {
        val file = tempFolder.newFile("not_audio.wav")
        file.writeBytes(ByteArray(64) { 0x7F })

        assertNull(WavHelper.readWavHeader(file))
        assertNull(WavHelper.readWav(file))
    }

    @Test
    fun readWavHeader_skipsUnknownChunksBeforeData() {
        val sampleRate = 22050
        val frames = 300
        val file = writeStereoWav(tempFolder.newFile("with_list.wav"), sampleRate, frames, extraChunkBytes = 100)

        val header = WavHelper.readWavHeader(file)

        assertNotNull(header)
        assertEquals(sampleRate, header!!.sampleRate)
        assertEquals(frames.toLong(), header.frameCount)
        assertEquals(frames, WavHelper.readWav(file)!!.samples.size)
    }

    @Test
    fun memoryBudget_isABoundedShareOfTheHeap() {
        val maxHeap = Runtime.getRuntime().maxMemory()
        assertTrue(AudioMemoryBudget.maxPcmBytes() <= maxHeap)
        assertTrue(AudioMemoryBudget.maxFrames() > 0)
    }

    /**
     * Writes a 16-bit stereo WAV whose left channel is +0.5 and right channel is -0.5, optionally with
     * a filler chunk before `data` so chunk skipping is exercised.
     */
    private fun writeStereoWav(
        file: File,
        sampleRate: Int,
        frames: Int,
        extraChunkBytes: Int = 0
    ): File {
        val dataBytes = frames * 2 * 2
        val extras = if (extraChunkBytes > 0) 8 + extraChunkBytes else 0
        val out = ByteArrayOutputStream()
        fun le16(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
        fun le32(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        le32(36 + dataBytes + extras)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        le32(16)
        le16(1)
        le16(2)
        le32(sampleRate)
        le32(sampleRate * 4)
        le16(4)
        le16(16)
        if (extraChunkBytes > 0) {
            out.write("LIST".toByteArray(Charsets.US_ASCII))
            le32(extraChunkBytes)
            repeat(extraChunkBytes) { out.write(0) }
        }
        out.write("data".toByteArray(Charsets.US_ASCII))
        le32(dataBytes)
        repeat(frames) {
            le16(16384) // left channel: +0.5
            le16(-16384 and 0xFFFF) // right channel: -0.5
        }
        file.writeBytes(out.toByteArray())
        return file
    }
}
