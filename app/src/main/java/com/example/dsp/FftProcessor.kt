package com.example.dsp

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class FftProcessor(val fftSize: Int = 1024) {

    private val window: FloatArray = FloatArray(fftSize)
    private val bitRev: IntArray = IntArray(fftSize)
    private val sinTable: FloatArray = FloatArray(fftSize / 2)
    private val cosTable: FloatArray = FloatArray(fftSize / 2)

    init {
        require((fftSize and (fftSize - 1)) == 0) { "FFT size must be a power of 2" }

        // Precompute Hanning window
        for (i in 0 until fftSize) {
            window[i] = (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
        }

        // Precompute bit reversal lookup
        val numBits = Integer.numberOfTrailingZeros(fftSize)
        for (i in 0 until fftSize) {
            var rev = 0
            var temp = i
            for (j in 0 until numBits) {
                rev = (rev shl 1) or (temp and 1)
                temp = temp shr 1
            }
            bitRev[i] = rev
        }

        // Precompute trigonometric tables
        for (i in 0 until fftSize / 2) {
            val angle = -2.0 * PI * i / fftSize
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
    }

    /**
     * In-place Radix-2 Cooley-Tukey FFT.
     * real and imag must be of size fftSize.
     */
    fun forward(real: FloatArray, imag: FloatArray) {
        // Bit reversal
        for (i in 0 until fftSize) {
            val j = bitRev[i]
            if (j > i) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
        }

        // Butterfly stages
        var len = 2
        while (len <= fftSize) {
            val halfLen = len / 2
            val step = fftSize / len

            var i = 0
            while (i < fftSize) {
                var k = 0
                for (j in 0 until halfLen) {
                    val tableIdx = k * step
                    val c = cosTable[tableIdx]
                    val s = sinTable[tableIdx]

                    val uR = real[i + j]
                    val uI = imag[i + j]

                    val vR = real[i + j + halfLen] * c - imag[i + j + halfLen] * s
                    val vI = real[i + j + halfLen] * s + imag[i + j + halfLen] * c

                    real[i + j] = uR + vR
                    imag[i + j] = uI + vI

                    real[i + j + halfLen] = uR - vR
                    imag[i + j + halfLen] = uI - vI
                    k++
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Computes the magnitude spectrum in dB for a single frame of samples.
     * Output array length is fftSize / 2 (frequency bins up to Nyquist).
     */
    fun computeMagnitudesDb(samples: FloatArray, offset: Int = 0): FloatArray {
        val numBins = fftSize / 2
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)

        for (i in 0 until fftSize) {
            val sampleIdx = offset + i
            val sample = if (sampleIdx in samples.indices) samples[sampleIdx] else 0f
            real[i] = sample * window[i]
            imag[i] = 0f
        }

        forward(real, imag)

        val db = FloatArray(numBins)
        for (i in 0 until numBins) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i]) + 1e-9f
            // Convert to dB relative to 1.0 (approx 0 dBFS max)
            val dbVal = (20.0 * log10(mag.toDouble() / (fftSize / 2))).toFloat()
            db[i] = dbVal.coerceIn(-90f, 0f)
        }
        return db
    }

    /**
     * Generates a matrix of magnitude columns representing a full spectrogram.
     * Each FloatArray in the returned list is a spectrum column [0..numBins-1].
     */
    fun computeSpectrogram(
        samples: FloatArray,
        sampleRate: Int = 44100,
        maxColumns: Int = 300,
        hopSize: Int = 512
    ): SpectrogramResult {
        if (samples.isEmpty()) {
            return SpectrogramResult(emptyList(), sampleRate, fftSize, 0)
        }

        val totalFrames = (samples.size - fftSize).coerceAtLeast(0) / hopSize + 1
        val step = if (totalFrames > maxColumns) {
            (totalFrames / maxColumns).coerceAtLeast(1)
        } else {
            1
        }

        val columns = mutableListOf<FloatArray>()
        var frameIndex = 0

        while (frameIndex * hopSize + fftSize <= samples.size && columns.size < maxColumns) {
            val sampleOffset = frameIndex * hopSize
            val dbSpectrum = computeMagnitudesDb(samples, sampleOffset)
            columns.add(dbSpectrum)
            frameIndex += step
        }

        return SpectrogramResult(
            columns = columns,
            sampleRate = sampleRate,
            fftSize = fftSize,
            totalSamples = samples.size
        )
    }

    companion object {
        /**
         * Maps a normalized intensity (0.0 to 1.0) to a rich forensic heat-map color:
         * 0.0: Deep Space Navy / Dark Blue (#07080B -> #002244)
         * 0.3: Electric Forensic Cyan (#00E5FF)
         * 0.6: Neon Green (#00FF9D)
         * 0.8: Forensic Amber / Yellow (#FFD600)
         * 1.0: Hot White / Infrared Red (#FF1744 -> #FFFFFF)
         */
        fun intensityToHeatmapColor(intensity: Float): Color {
            val t = intensity.coerceIn(0f, 1f)
            return when {
                t < 0.25f -> {
                    // Dark background to Cyan
                    val factor = t / 0.25f
                    Color(
                        red = 0.03f + factor * 0f,
                        green = 0.05f + factor * 0.90f,
                        blue = 0.12f + factor * 0.88f,
                        alpha = 1.0f
                    )
                }
                t < 0.5f -> {
                    // Cyan to Neon Green
                    val factor = (t - 0.25f) / 0.25f
                    Color(
                        red = 0f + factor * 0f,
                        green = 0.90f + factor * 0.10f,
                        blue = 1.0f - factor * 0.40f,
                        alpha = 1.0f
                    )
                }
                t < 0.75f -> {
                    // Neon Green to Yellow/Amber
                    val factor = (t - 0.5f) / 0.25f
                    Color(
                        red = factor * 1.0f,
                        green = 1.0f - factor * 0.15f,
                        blue = 0.60f * (1f - factor),
                        alpha = 1.0f
                    )
                }
                else -> {
                    // Amber to Intense Red / Hot White
                    val factor = (t - 0.75f) / 0.25f
                    Color(
                        red = 1.0f,
                        green = 0.85f * (1f - factor) + factor * 0.95f,
                        blue = factor * 0.95f,
                        alpha = 1.0f
                    )
                }
            }
        }
    }
}

data class SpectrogramResult(
    val columns: List<FloatArray>,
    val sampleRate: Int,
    val fftSize: Int,
    val totalSamples: Int
) {
    val nyquistFreq: Float = sampleRate / 2.0f
    val numBins: Int = fftSize / 2

    fun binToFrequency(bin: Int): Float {
        return (bin.toFloat() / numBins) * nyquistFreq
    }

    fun frequencyToBin(freq: Float): Int {
        return ((freq / nyquistFreq) * numBins).toInt().coerceIn(0, numBins - 1)
    }
}
