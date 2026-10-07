package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.example.dsp.FftProcessor
import com.example.dsp.SpectrogramResult
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSpectrogramBg
import com.example.viewmodel.InspectionPoint
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

@Composable
fun SpectrogramCanvas(
    spectrogram: SpectrogramResult?,
    currentPositionMs: Long,
    durationMs: Long,
    inspectionPoint: InspectionPoint?,
    onInspectPoint: (freqHz: Float, db: Float, timeMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.argb(160, 160, 175, 195)
            textSize = 24f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val hudPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Box(
        modifier = modifier
            .background(ForensicSpectrogramBg)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(spectrogram, durationMs) {
                    detectTapGestures { offset ->
                        if (spectrogram != null && durationMs > 0) {
                            val timeFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val timeMs = (timeFraction * durationMs).toLong()

                            // Frequency mapping: logarithmic distribution 0..Nyquist
                            val yFraction = 1.0f - (offset.y / size.height).coerceIn(0f, 1f)
                            // Logarithmic mapping for better vocal band resolution
                            val freqHz = (20.0 * (spectrogram.nyquistFreq / 20.0).toDouble().pow(yFraction.toDouble())).toFloat()

                            val colIdx = (timeFraction * (spectrogram.columns.size - 1)).toInt().coerceIn(0, spectrogram.columns.size - 1)
                            val col = spectrogram.columns[colIdx]
                            val bin = spectrogram.frequencyToBin(freqHz)
                            val db = if (bin in col.indices) col[bin] else -60f

                            onInspectPoint(freqHz, db, timeMs)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            if (spectrogram != null && spectrogram.columns.isNotEmpty()) {
                val columns = spectrogram.columns
                val numCols = columns.size
                val colWidth = width / numCols
                val numBins = spectrogram.numBins

                // Draw each frequency column
                for (c in 0 until numCols) {
                    val spectrum = columns[c]
                    val x = c * colWidth

                    // Downsample / group bins for smooth fast drawing
                    val numBands = 64
                    val binsPerBand = max(numBins / numBands, 1)
                    val bandHeight = height / numBands

                    for (band in 0 until numBands) {
                        val startBin = band * binsPerBand
                        val endBin = (startBin + binsPerBand).coerceAtMost(numBins)

                        var maxDb = -90f
                        for (b in startBin until endBin) {
                            if (b in spectrum.indices && spectrum[b] > maxDb) {
                                maxDb = spectrum[b]
                            }
                        }

                        // Normalize dB (-80 dB to -10 dB)
                        val intensity = ((maxDb + 80f) / 70f).coerceIn(0f, 1f)
                        val color = FftProcessor.intensityToHeatmapColor(intensity)

                        // Y coordinate: High frequencies at top, low frequencies at bottom
                        val y = height - (band + 1) * bandHeight

                        drawRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(colWidth + 0.5f, bandHeight + 0.5f)
                        )
                    }
                }

                // Draw standard forensic frequency grid lines
                val frequencyGrid = listOf(
                    300f to "300Hz (HPF)",
                    1000f to "1kHz",
                    2500f to "2.5kHz (Vocal)",
                    4000f to "4kHz (LPF)",
                    8000f to "8kHz",
                    16000f to "16kHz"
                )

                for ((freq, label) in frequencyGrid) {
                    val bin = spectrogram.frequencyToBin(freq)
                    val y = height - (bin.toFloat() / numBins) * height

                    if (y in 0f..height) {
                        val isVocalBand = freq in 300f..4000f
                        drawLine(
                            color = if (isVocalBand) Color(0x3300E5FF) else Color(0x1AFFFFFF),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = if (isVocalBand) 1.5f else 1f
                        )

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(label, 12f, y - 6f, textPaint)
                        }
                    }
                }

                // Draw Playhead line
                if (durationMs > 0) {
                    val playheadX = (currentPositionMs.toFloat() / durationMs) * width
                    drawLine(
                        color = Color.White,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, height),
                        strokeWidth = 2f
                    )
                }

                // Draw crosshairs and HUD if point inspected
                if (inspectionPoint != null) {
                    val inspectTimeFrac = (inspectionPoint.timeMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    val inspectX = inspectTimeFrac * width
                    val bin = spectrogram.frequencyToBin(inspectionPoint.frequencyHz)
                    val inspectY = height - (bin.toFloat() / numBins) * height

                    // Crosshairs
                    drawLine(
                        color = ForensicOrange,
                        start = Offset(0f, inspectY),
                        end = Offset(width, inspectY),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = ForensicOrange,
                        start = Offset(inspectX, 0f),
                        end = Offset(inspectX, height),
                        strokeWidth = 1.5f
                    )
                    drawCircle(
                        color = ForensicOrange,
                        radius = 6f,
                        center = Offset(inspectX, inspectY)
                    )

                    // Draw HUD floating label
                    drawIntoCanvas { canvas ->
                        val hudText = "⚡ ${inspectionPoint.frequencyHz.toInt()} Hz | ${String.format("%.1f", inspectionPoint.decibels)} dBFS"
                        val hudX = (inspectX + 16f).coerceAtMost(width - 320f)
                        val hudY = (inspectY - 16f).coerceAtLeast(40f)

                        // Draw dark HUD background
                        val hudBgPaint = Paint().apply {
                            color = android.graphics.Color.argb(220, 15, 20, 30)
                            style = Paint.Style.FILL
                        }
                        canvas.nativeCanvas.drawRect(
                            hudX - 8f, hudY - 30f, hudX + 310f, hudY + 10f, hudBgPaint
                        )
                        canvas.nativeCanvas.drawText(hudText, hudX, hudY, hudPaint)
                    }
                }
            } else {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        "Loading FFT Spectrum Data...",
                        width / 2f - 180f,
                        height / 2f,
                        textPaint
                    )
                }
            }
        }
    }
}
