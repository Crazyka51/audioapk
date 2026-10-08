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
enum class HeatmapPalette(val label: String) {
    CYBER_NEON("Cyber Neon"),
    THERMAL_IR("Thermal IR"),
    VIRIDIS("Viridis"),
    XRAY_MONO("X-Ray Mono")
}

        /**
         * Maps a normalized intensity (0.0 to 1.0) to a forensic heat-map color according to the chosen palette.
         */
        fun intensityToHeatmapColor(
            intensity: Float,
            palette: HeatmapPalette = HeatmapPalette.CYBER_NEON
        ): Color {
            val t = intensity.coerceIn(0f, 1f)
            return when (palette) {
                HeatmapPalette.CYBER_NEON -> when {
                    t < 0.25f -> {
                        val factor = t / 0.25f
                        Color(
                            red = 0.03f,
                            green = 0.05f + factor * 0.90f,
                            blue = 0.12f + factor * 0.88f,
                            alpha = 1.0f
                        )
                    }
                    t < 0.5f -> {
                        val factor = (t - 0.25f) / 0.25f
                        Color(
                            red = 0f,
                            green = 0.90f + factor * 0.10f,
                            blue = 1.0f - factor * 0.40f,
                            alpha = 1.0f
                        )
                    }
                    t < 0.75f -> {
                        val factor = (t - 0.5f) / 0.25f
                        Color(
                            red = factor * 1.0f,
                            green = 1.0f - factor * 0.15f,
                            blue = 0.60f * (1f - factor),
                            alpha = 1.0f
                        )
                    }
                    else -> {
                        val factor = (t - 0.75f) / 0.25f
                        Color(
                            red = 1.0f,
                            green = 0.85f * (1f - factor) + factor * 0.95f,
                            blue = factor * 0.95f,
                            alpha = 1.0f
                        )
                    }
                }
                HeatmapPalette.THERMAL_IR -> when {
                    t < 0.3f -> {
                        val f = t / 0.3f
                        Color(red = 0.04f + f * 0.45f, green = 0.02f, blue = 0.08f + f * 0.50f, alpha = 1f)
                    }
                    t < 0.6f -> {
                        val f = (t - 0.3f) / 0.3f
                        Color(red = 0.49f + f * 0.51f, green = 0.02f + f * 0.35f, blue = 0.58f * (1f - f), alpha = 1f)
                    }
                    t < 0.85f -> {
                        val f = (t - 0.6f) / 0.25f
                        Color(red = 1.0f, green = 0.37f + f * 0.55f, blue = f * 0.15f, alpha = 1f)
                    }
                    else -> {
                        val f = (t - 0.85f) / 0.15f
                        Color(red = 1.0f, green = 0.92f + f * 0.08f, blue = 0.15f + f * 0.85f, alpha = 1f)
                    }
                }
                HeatmapPalette.VIRIDIS -> when {
                    t < 0.33f -> {
                        val f = t / 0.33f
                        Color(red = 0.15f * (1f - f) + 0.12f * f, green = 0.05f + f * 0.45f, blue = 0.28f + f * 0.27f, alpha = 1f)
                    }
                    t < 0.66f -> {
                        val f = (t - 0.33f) / 0.33f
                        Color(red = 0.12f + f * 0.25f, green = 0.50f + f * 0.30f, blue = 0.55f - f * 0.18f, alpha = 1f)
                    }
                    else -> {
                        val f = (t - 0.66f) / 0.34f
                        Color(red = 0.37f + f * 0.62f, green = 0.80f + f * 0.18f, blue = 0.37f * (1f - f) + 0.15f, alpha = 1f)
                    }
                }
                HeatmapPalette.XRAY_MONO -> {
                    val v = (t * t * 0.3f + t * 0.7f).coerceIn(0.04f, 1.0f)
                    Color(red = v * 0.9f, green = v * 0.97f, blue = v, alpha = 1f)
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

    fun getColumnAtFraction(fraction: Float): FloatArray? {
        if (columns.isEmpty()) return null
        val idx = (fraction.coerceIn(0f, 1f) * (columns.size - 1)).toInt().coerceIn(0, columns.size - 1)
        return columns[idx]
    }
}
