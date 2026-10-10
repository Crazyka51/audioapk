package com.example.desktop

import com.example.dsp.WavHelper
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertTrue

/**
 * Regression test for a bug where pausing playback in the Studio tab and then resuming it made the
 * recording "jump to the end" and stop responding to the position slider.
 *
 * Root cause: pausing only stops (but does not close) the JVM `SourceDataLine`. Resuming reused that
 * same line but never called `.start()` again, so the writer coroutine kept writing audio into a
 * buffer that never drained - it filled within a fraction of a second and the write() call blocked
 * forever, freezing the reported position and leaving the transport unresponsive to further seeks.
 */
class DesktopAudioPlayerPauseResumeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sampleRate = 44100

    @Test
    fun resumingAfterPauseContinuesFromWhereItLeftOff() {
        assumeAudioOutputAvailable()

        val file = tempFolder.newFile("resume.wav")
        val seconds = 5
        val samples = FloatArray(seconds * sampleRate) { i ->
            (0.2 * sin(2.0 * PI * 440.0 * i / sampleRate)).toFloat()
        }
        WavHelper.writeWav(file, samples, sampleRate, 1)

        val player = DesktopAudioPlayer()
        try {
            player.load(file)
            waitUntil(timeoutMs = 5000) { player.playbackState.value.isReady }

            player.play()
            Thread.sleep(400)
            player.pause()
            // Let any in-flight write fully settle before reading the paused position.
            Thread.sleep(100)
            val pausedPosition = player.playbackState.value.currentPositionMs
            assertTrue(
                !player.playbackState.value.isPlaying,
                "Player should report paused after pause()"
            )

            // While genuinely paused, the position must not drift on its own.
            Thread.sleep(300)
            val stillPaused = player.playbackState.value.currentPositionMs
            assertTrue(
                kotlin.math.abs(stillPaused - pausedPosition) < 100,
                "Position moved while paused: $pausedPosition -> $stillPaused"
            )

            player.play()
            Thread.sleep(400)
            val resumedPosition = player.playbackState.value.currentPositionMs
            val durationMs = player.playbackState.value.durationMs

            assertTrue(
                resumedPosition < durationMs - 200,
                "Resuming jumped (almost) straight to the end: paused=$pausedPosition " +
                    "resumed=$resumedPosition duration=$durationMs"
            )
            assertTrue(
                resumedPosition > pausedPosition,
                "Resuming did not advance playback at all: paused=$pausedPosition resumed=$resumedPosition"
            )

            // The writer must still be alive and advancing, not stuck in a blocked write() call.
            Thread.sleep(300)
            val laterPosition = player.playbackState.value.currentPositionMs
            assertTrue(
                laterPosition > resumedPosition,
                "Playback froze after resuming: resumed=$resumedPosition later=$laterPosition"
            )
        } finally {
            player.release()
        }
    }

    @Test
    fun seekingRepeatedlyWhilePlayingKeepsAdvancingAfterwards() {
        assumeAudioOutputAvailable()

        val file = tempFolder.newFile("seek.wav")
        val seconds = 8
        val samples = FloatArray(seconds * sampleRate) { i ->
            (0.2 * sin(2.0 * PI * 440.0 * i / sampleRate)).toFloat()
        }
        WavHelper.writeWav(file, samples, sampleRate, 1)

        val player = DesktopAudioPlayer()
        try {
            player.load(file)
            waitUntil(timeoutMs = 5000) { player.playbackState.value.isReady }

            player.play()
            Thread.sleep(150)

            // Simulate rapidly dragging the position slider: many seeks fired in quick succession
            // while playback is active, which used to race over the same audio line.
            var target = 500L
            repeat(15) {
                player.seekTo(target)
                target += 300L
                Thread.sleep(20)
            }

            val afterDragging = player.playbackState.value.currentPositionMs
            Thread.sleep(400)
            val later = player.playbackState.value.currentPositionMs

            assertTrue(
                later > afterDragging,
                "Playback stopped advancing after rapid seeking: afterDragging=$afterDragging later=$later"
            )
        } finally {
            player.release()
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue(condition(), "Condition not met within ${timeoutMs}ms")
    }

    /** Skips (rather than fails) on environments without a usable audio output, e.g. some CI runners. */
    private fun assumeAudioOutputAvailable() {
        val available = try {
            val format = javax.sound.sampled.AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(format)
            line.close()
            true
        } catch (e: LineUnavailableException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
        Assume.assumeTrue("No usable audio output line on this machine", available)
    }
}
