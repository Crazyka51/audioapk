package com.example

import com.example.data.AudioExporter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Covers the target naming used when publishing exports into Music/AudioDetective. */
class AudioExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun uniqueTarget_usesPlainNameWhenFree() {
        val directory = tempFolder.newFolder("AudioDetective")

        val target = AudioExporter.uniqueTarget(directory, "case1_Cleaned.wav")

        assertEquals("case1_Cleaned.wav", target.name)
        assertEquals(directory, target.parentFile)
    }

    @Test
    fun uniqueTarget_appendsCounterInsteadOfOverwriting() {
        val directory = tempFolder.newFolder("AudioDetective")
        File(directory, "case1_Cleaned.wav").writeText("first")
        File(directory, "case1_Cleaned_1.wav").writeText("second")

        val target = AudioExporter.uniqueTarget(directory, "case1_Cleaned.wav")

        assertEquals("case1_Cleaned_2.wav", target.name)
    }

    @Test
    fun uniqueTarget_keepsExtensionWithoutDotInName() {
        val directory = tempFolder.newFolder("AudioDetective")

        val target = AudioExporter.uniqueTarget(directory, "recording")

        assertEquals("recording.wav", target.name)
    }

    @Test
    fun displayLocation_matchesRequestedFolder() {
        assertEquals("Music/AudioDetective", AudioExporter.displayLocation)
    }
}
