package com.example

import com.example.dsp.AudioWindowKind
import com.example.dsp.DynamicReference
import com.example.dsp.FftProcessor
import com.example.dsp.MusicAnalyzer
import com.example.dsp.SpectralAligner
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Covers the background-music pipeline: finding the song in a recording, aligning to it precisely
 * (including a variable playback rate) and keeping a full length reference instead of truncating it.
 */
class MusicSyncTest {

    private val sampleRate = 44100

    /**
     * Music-like material matching what a real song looks like on a spectrogram: a bass line an octave
     * below the vocal range, mid-range chords, hi-hat noise above 6 kHz and a rhythmic pulse. The
     * progression advances so the signal is aperiodic - a repeating loop would make alignment
     * ambiguous (the match could land one loop off) which is a fixture artefact, not a real case.
     */
    private fun music(seconds: Int, offset: Int = 0): FloatArray {
        val samples = FloatArray(seconds * sampleRate)
        val chordRoots = listOf(220.0, 246.94, 277.18, 329.63, 293.66, 349.23, 392.0, 440.0)
        var noiseState = 12345
        for (i in samples.indices) {
            val t = (i + offset).toFloat() / sampleRate
            // Chord changes every 2 s, different root each time -> no short repeating cycle.
            val chord = chordRoots[(t / 2).toInt() % chordRoots.size]

            val beat = 1.0 + 0.4 * sin(2.0 * PI * 2.0 * t)
            val bass = 0.30 * sin(2.0 * PI * (chord / 2) * t)
            val mid = 0.18 * sin(2.0 * PI * chord * t) + 0.10 * sin(2.0 * PI * chord * 1.5 * t)
            val upper = 0.06 * sin(2.0 * PI * chord * 3 * t)

            // Hi-hat on every beat: deterministic pseudo noise, high passed by construction.
            val beatPhase = (t * 2.0) % 1.0
            noiseState = noiseState * 1103515245 + 12345
            val noise = ((noiseState ushr 16) and 0x7FFF) / 32767.0 - 0.5
            val hat = if (beatPhase < 0.12) 0.22 * noise * (1.0 - beatPhase / 0.12) else 0.0

            samples[i] = (beat * (bass + mid + upper) + hat).toFloat()
        }
        return samples
    }
    /**
     * Speech-like material: energy concentrated in the formant band with *irregular* syllable timing.
     * Real speech is not metrically periodic; a perfectly periodic fixture would be indistinguishable
     * from music by any onset-based measure.
     */
    private fun speech(seconds: Int): FloatArray {
        val samples = FloatArray(seconds * sampleRate)
        val formants = doubleArrayOf(700.0, 1400.0, 2500.0)
        var syllableStart = 0
        var syllableLength = (sampleRate * 0.22).toInt()
        var state = 987_654_321
        for (i in samples.indices) {
            if (i >= syllableStart + syllableLength) {
                // Next syllable: irregular length and a short pause, as in natural speech.
                state = state * 1103515245 + 12345
                val jitter = ((state ushr 16) and 0xFF) / 255.0
                syllableLength = (sampleRate * (0.12 + jitter * 0.28)).toInt()
                syllableStart = i + (sampleRate * (0.02 + jitter * 0.10)).toInt()
            }
            val insideSyllable = i >= syllableStart && i < syllableStart + syllableLength
            if (!insideSyllable) continue

            val t = i.toFloat() / sampleRate
            // Amplitude envelope shaped like a vowel, not a sine.
            val progress = (i - syllableStart).toFloat() / syllableLength
            val envelope = kotlin.math.sin(PI * progress).toFloat()
            samples[i] = (0.30 * envelope * sin(2.0 * PI * formants[0] * t)).toFloat() +
                (0.16 * envelope * sin(2.0 * PI * formants[1] * t)).toFloat() +
                (0.08 * envelope * sin(2.0 * PI * formants[2] * t)).toFloat()
        }
        return samples
    }

    @Test
    fun aligner_findsSampleAccurateOffset() {
        val reference = music(6)
        val lead = FloatArray(sampleRate * 3)
        val target = lead + reference + FloatArray(sampleRate)

        val alignment = SpectralAligner.align(target, reference, sampleRate)

        assertNotNull("alignment should be found", alignment)
        val errorSamples = abs(alignment!!.offsetSamples - lead.size)
        // Envelope resolution is ~11 ms, the refinement pass should bring this well below that.
        assertTrue(
            "offset should be within 5 ms of ${lead.size} samples, was ${alignment.offsetSamples}",
            errorSamples < sampleRate / 200
        )
        assertTrue("confidence should be usable, was ${alignment.confidence}", alignment.confidence > 0.35f)
    }

    @Test
    fun aligner_detectsVariablePlaybackRate() {
        // Reference played 3 % faster inside the recording: each reference sample then occupies
        // 1/1.03 target samples, which is what drifts a long subtraction if it is not measured.
        val original = music(12)
        val speedFactor = 1.03
        val replayed = FloatArray((original.size / speedFactor).toInt())
        for (i in replayed.indices) {
            val source = i * speedFactor
            val index = source.toInt()
            val fraction = (source - index).toFloat()
            val a = original[index.coerceAtMost(original.size - 1)]
            val b = original[(index + 1).coerceAtMost(original.size - 1)]
            replayed[i] = a + (b - a) * fraction
        }

        val lead = FloatArray(sampleRate)
        val target = lead + replayed + FloatArray(sampleRate)

        val alignment = SpectralAligner.align(target, original, sampleRate)

        assertNotNull("alignment should be found", alignment)
        val expectedRate = (1.0 / speedFactor).toFloat()
        assertTrue(
            "rate should be close to $expectedRate, was ${alignment!!.rate}",
            abs(alignment.rate - expectedRate) < 0.02f
        )
        assertTrue("drift should be reported", abs(alignment.driftPercent) > 0.5f)
    }

    @Test
    fun aligner_reportsLowConfidenceForUnrelatedMaterial() {
        val reference = music(6)
        val target = speech(12)

        val alignment = SpectralAligner.align(target, reference, sampleRate)

        // Either no alignment at all, or one that is explicitly marked as unreliable.
        assertTrue(
            "unrelated material must not produce a confident match",
            alignment == null || alignment.confidence < 0.55f
        )
    }

    @Test
    fun dynamicReference_coversTheWholeSongInsteadOfTruncating() {
        val fft = FftProcessor(1024)
        val window = FloatArray(1024) { i ->
            (0.5 * (1.0 - kotlin.math.cos(2.0 * PI * i / 1023))).toFloat()
        }
        val fourMinutes = music(240)

        // Frame budget used by the canceller for a full length song.
        val budget = 24000
        var hop = 1
        val minHop = ((fourMinutes.size - 1024) / budget).coerceAtLeast(1)
        while (hop < minHop) hop = hop shl 1

        val reference = DynamicReference.build(fourMinutes, hop, 1024, fft, window)

        assertNotNull("reference should build", reference)
        val covered = reference!!.frames.toDouble() * reference.hopSamples
        // The last frame needs a full FFT window, so coverage can legitimately be one window short.
        assertTrue(
            "reference should cover the whole song (covered $covered of ${fourMinutes.size})",
            covered + 1024 >= fourMinutes.size
        )
        // One byte per bin keeps a four minute song affordable.
        assertTrue(
            "quantized reference should stay small, was ${reference.sizeBytes / (1024 * 1024)} MB",
            reference.sizeBytes < 12L * 1024 * 1024
        )
    }

    @Test
    fun musicAnalyzer_separatesMusicFromSpeechAndSilence() {
        val silence = FloatArray(sampleRate * 3)
        val recording = music(12) + silence + speech(8)

        val analysis = MusicAnalyzer.analyze(recording, sampleRate)

        assertTrue("windows should be produced", analysis.windows.size > 10)
        assertTrue("music should be found", analysis.musicSegments.isNotEmpty())
        assertTrue("silence should be detected", analysis.silenceRatio > 0.05f)

        val musicWindows = analysis.windows.filter { it.kind == AudioWindowKind.MUSIC }
        val speechWindows = analysis.windows.filter { it.kind == AudioWindowKind.SPEECH }

        assertTrue("some windows should classify as music", musicWindows.isNotEmpty())
        assertTrue("some windows should classify as speech", speechWindows.isNotEmpty())
        // The speech part starts after the music part, so classification should not be uniform.
        val firstSpeech = speechWindows.first().startMs
        val lastMusic = musicWindows.last().startMs
        assertTrue(
            "speech should be found in the later part of the recording (speech at $firstSpeech, music ends $lastMusic)",
            firstSpeech > analysis.windows.first().startMs
        )
    }

    @Test
    fun musicAnalyzer_picksALearningSegmentInsideTheMusic() {
        val recording = FloatArray(sampleRate) + music(10)
        val analysis = MusicAnalyzer.analyze(recording, sampleRate)

        val segment = analysis.bestLearningSegment(2000L)

        assertNotNull("a learning segment should be suggested", segment)
        assertTrue("segment should be long enough", segment!!.second - segment.first >= 1000L)
        // It must land in the music (after the initial second), not in the leading silence.
        assertTrue("segment should start in the music, was ${segment.first} ms", segment.first >= 500L)
    }
}
