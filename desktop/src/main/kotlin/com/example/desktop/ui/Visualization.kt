package com.example.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.dsp.FftProcessor
import com.example.dsp.SpectrogramResult
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * RMS envelope oscilloscope with a playback playhead and A-B markers. Clicking or dragging seeks,
 * which mirrors the touch behaviour of the Android `WaveformView`.
 */
@Composable
fun WaveformPanel(
    samples: FloatArray?,
    durationMs: Long,
    positionMs: Long,
    loopStartMs: Long,
    loopEndMs: Long,
    loopEnabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnSeek = rememberUpdatedState(onSeek)

    Column(modifier) {
        Text(
            "Amplitude Waveform (RMS envelope)",
            style = MaterialTheme.typography.labelLarge,
            color = ForensicTextMuted
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(top = 6.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            currentOnSeek.value((offset.x / size.width * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures { change, _ ->
                        if (durationMs > 0) {
                            currentOnSeek.value((change.position.x / size.width * durationMs).toLong())
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(color = ForensicSurfaceVariant)
                if (samples == null || samples.isEmpty()) {
                    return@Canvas
                }

                val midY = size.height / 2f
                drawLine(
                    color = ForensicTextMuted.copy(alpha = 0.35f),
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = 1f
                )

                // One column per pixel: min/max envelope plus an RMS line on top.
                val columns = size.width.toInt().coerceAtLeast(1)
                val step = (samples.size / columns.toFloat()).coerceAtLeast(1f)
                val envelope = Path()
                val rmsPath = Path()
                var started = false

                for (column in 0 until columns) {
                    val from = (column * step).toInt()
                    val to = ((column + 1) * step).toInt().coerceAtMost(samples.size)
                    if (from >= to) continue

                    var min = 0f
                    var max = 0f
                    var sumSquares = 0.0
                    for (i in from until to) {
                        val value = samples[i]
                        if (value < min) min = value
                        if (value > max) max = value
                        sumSquares += value.toDouble() * value
                    }
                    val rms = sqrt(sumSquares / (to - from)).toFloat()
                    val x = column.toFloat()

                    if (!started) {
                        envelope.moveTo(x, midY - max * midY * 0.92f)
                        rmsPath.moveTo(x, midY - rms * midY * 0.92f)
                        started = true
                    } else {
                        envelope.lineTo(x, midY - max * midY * 0.92f)
                        rmsPath.lineTo(x, midY - rms * midY * 0.92f)
                    }
                }

                drawPath(envelope, color = ForensicCyan.copy(alpha = 0.55f), style = Stroke(width = 1f))
                drawPath(rmsPath, color = ForensicCyan, style = Stroke(width = 1.6f))

                // A-B loop region
                if (loopEnabled && loopEndMs > loopStartMs && durationMs > 0) {
                    val fromX = (loopStartMs.toFloat() / durationMs) * size.width
                    val toX = (loopEndMs.toFloat() / durationMs) * size.width
                    drawRect(
                        color = Color(0xFFFF5722).copy(alpha = 0.12f),
                        topLeft = Offset(fromX, 0f),
                        size = Size((toX - fromX).coerceAtLeast(1f), size.height)
                    )
                    drawLine(Color(0xFFFF5722), Offset(fromX, 0f), Offset(fromX, size.height), 1.5f)
                    drawLine(Color(0xFFFF5722), Offset(toX, 0f), Offset(toX, size.height), 1.5f)
                }

                // Playhead
                if (durationMs > 0) {
                    val x = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
                    drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2f)
                }
            }
        }
    }
}

/**
 * FFT heat map of the whole recording. The palette comes from the shared [FftProcessor] companion,
 * so desktop and Android render identical colours.
 */
@Composable
fun SpectrogramPanel(
    spectrogram: SpectrogramResult?,
    durationMs: Long,
    onInspect: (Float, Float, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnInspect = rememberUpdatedState(onInspect)

    Column(modifier) {
        Text(
            "FFT Spectrogram (heat map, 0-22 kHz)",
            style = MaterialTheme.typography.labelLarge,
            color = ForensicTextMuted
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(top = 6.dp)
                .pointerInput(spectrogram) {
                    detectTapGestures { offset ->
                        inspectAt(
                            spectrogram, durationMs, offset,
                            size.width.toFloat(), size.height.toFloat(), currentOnInspect.value
                        )
                    }
                }
                .pointerInput(spectrogram) {
                    detectDragGestures { change, _ ->
                        inspectAt(
                            spectrogram, durationMs, change.position,
                            size.width.toFloat(), size.height.toFloat(), currentOnInspect.value
                        )
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(color = Color(0xFF05070C))
                val result = spectrogram ?: return@Canvas
                if (result.columns.isEmpty()) return@Canvas

                val columns = result.columns.size
                val bins = result.numBins
                val columnWidth = size.width / columns
                val rowHeight = size.height / bins

                for (c in 0 until columns) {
                    val column = result.columns[c]
                    for (bin in 0 until bins) {
                        val db = column.getOrElse(bin) { -90f }
                        val intensity = ((db + 88f) / 88f).coerceIn(0f, 1f)
                        if (intensity <= 0.02f) continue
                        val color = FftProcessor.intensityToHeatmapColor(intensity)
                        // Low frequencies at the bottom, like the Android canvas.
                        val y = size.height - (bin + 1) * rowHeight
                        drawRect(
                            color = color,
                            topLeft = Offset(c * columnWidth, y),
                            size = Size(columnWidth.coerceAtLeast(1f), rowHeight.coerceAtLeast(1f))
                        )
                    }
                }
            }
        }
    }
}

private fun inspectAt(
    spectrogram: SpectrogramResult?,
    durationMs: Long,
    offset: Offset,
    width: Float,
    height: Float,
    onInspect: (Float, Float, Long) -> Unit
) {
    val result = spectrogram ?: return
    if (result.columns.isEmpty() || durationMs <= 0 || width <= 0f || height <= 0f) return

    val fraction = (offset.x / width).coerceIn(0f, 1f)
    val column = result.getColumnAtFraction(fraction) ?: return
    val binIndex = (((height - offset.y) / height) * result.numBins).toInt().coerceIn(0, column.size - 1)
    val db = column.getOrElse(binIndex) { -90f }
    onInspect(result.binToFrequency(binIndex), db, (fraction * durationMs).toLong())
}

/** Small helper used by the library list to show a level bar without decoding the file. */
fun dbToFraction(db: Float, floorDb: Float = -90f): Float =
    ((db - floorDb) / abs(floorDb)).coerceIn(0f, 1f)

/** Formats a millisecond position as mm:ss.mmm. */
fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%02d:%02d.%03d".format(totalSeconds / 60, totalSeconds % 60, ms % 1000)
}

/** Formats a level for the inspection HUD. */
fun formatDb(db: Float): String = if (db <= -89f) "-inf dBFS" else "%.1f dBFS".format(db)

/** Convenience for logging a spectrum peak, kept for parity with the Android HUD text. */
fun describePeak(samples: FloatArray?): String {
    if (samples == null || samples.isEmpty()) return "no signal"
    var peak = 0f
    for (sample in samples) if (abs(sample) > peak) peak = abs(sample)
    val db = if (peak > 1e-4f) (20 * log10(peak.toDouble())).toFloat() else -90f
    return "peak ${formatDb(db)}"
}
