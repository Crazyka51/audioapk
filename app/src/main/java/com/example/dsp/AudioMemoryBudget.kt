package com.example.dsp

/**
 * Central budget for how much decoded audio may be held in memory.
 *
 * The whole app keeps a recording in RAM as 32-bit float mono PCM (4 bytes per sample), and several
 * DSP stages allocate one or two additional full-length arrays on top of that. Importing a long
 * recording used to blow the heap with an [OutOfMemoryError]; instead of crashing, decoding now
 * refuses anything that cannot fit and the UI can explain the limit to the user.
 */
object AudioMemoryBudget {

    /**
     * Share of the process heap that the decoded PCM itself may occupy. The rest has to cover the
     * DSP working copies (the heaviest stage, STFT voice separation, needs roughly three times the
     * PCM size), the spectrogram and the playback buffer.
     */
    private const val PCM_HEAP_SHARE = 0.30

    private const val MIN_PCM_BYTES = 16L * 1024 * 1024

    /** Maximum size of the decoded mono float PCM buffer, derived from the real heap limit. */
    fun maxPcmBytes(): Long {
        val maxHeap = Runtime.getRuntime().maxMemory()
        return (maxHeap * PCM_HEAP_SHARE).toLong().coerceAtLeast(MIN_PCM_BYTES)
    }

    /** Maximum number of mono frames (one float each) that may be decoded. */
    fun maxFrames(): Long = maxPcmBytes() / Float.SIZE_BYTES

    /** Maximum recording length in whole minutes that fits the budget at [sampleRate]. */
    fun maxDurationMinutes(sampleRate: Int): Int {
        if (sampleRate <= 0) return 0
        return (maxFrames() / sampleRate / 60).toInt()
    }

    /** Recording length in whole minutes for a given frame count, used for user-facing messages. */
    fun durationMinutesOf(frameCount: Long, sampleRate: Int): Int {
        if (sampleRate <= 0) return 0
        return (frameCount / sampleRate / 60).toInt()
    }
}
