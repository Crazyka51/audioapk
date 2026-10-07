package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicCyanGlow
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicWaveformBase
import com.example.ui.theme.ForensicWaveformGlow
import kotlin.math.abs
import kotlin.math.max

@Composable
fun WaveformView(
    samples: FloatArray?,
    currentPositionMs: Long,
    durationMs: Long,
    abLoopEnabled: Boolean = false,
    loopStartMs: Long = 0L,
    loopEndMs: Long = 0L,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    zoomLevel: Float = 1.0f,
    showLoopMarkers: Boolean = true
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val targetMs = (fraction * durationMs).toLong()
                            onSeek(targetMs)
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (durationMs > 0) {
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            val targetMs = (fraction * durationMs).toLong()
                            onSeek(targetMs)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            // Draw center reference zero line
            drawLine(
                color = Color(0x2200E5FF),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1f
            )

            // Draw A-B Loop shaded region if enabled
            if (abLoopEnabled && durationMs > 0 && loopEndMs > loopStartMs) {
                val startX = (loopStartMs.toFloat() / durationMs) * width
                val endX = (loopEndMs.toFloat() / durationMs) * width
                drawRect(
                    color = Color(0x2800E5FF),
                    topLeft = Offset(startX, 0f),
                    size = Size(max(endX - startX, 2f), height)
                )

                // Marker A line
                drawLine(
                    color = ForensicOrange,
                    start = Offset(startX, 0f),
                    end = Offset(startX, height),
                    strokeWidth = 2.5f
                )

                // Marker B line
                drawLine(
                    color = ForensicOrange,
                    start = Offset(endX, 0f),
                    end = Offset(endX, height),
                    strokeWidth = 2.5f
                )
            }

            // Draw Waveform bars / envelope
            if (samples != null && samples.isNotEmpty()) {
                val numBars = (width / 3.5f).toInt().coerceAtLeast(30)
                val samplesPerBar = max(samples.size / numBars, 1)

                val barWidth = 2.2f
                val spacing = width / numBars

                val progressFraction = if (durationMs > 0) {
                    (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else 0f
                val playheadX = progressFraction * width

                for (bar in 0 until numBars) {
                    val startSample = bar * samplesPerBar
                    val endSample = (startSample + samplesPerBar).coerceAtMost(samples.size)

                    var maxAmp = 0f
                    for (i in startSample until endSample) {
                        val a = abs(samples[i])
                        if (a > maxAmp) maxAmp = a
                    }

                    // Boost visibility of low amplitudes
                    val boostedAmp = (maxAmp * 1.35f).coerceIn(0.04f, 1.0f)
                    val barHeight = boostedAmp * (height * 0.85f)
                    val x = bar * spacing + spacing / 2f

                    val isPlayed = x <= playheadX
                    val barColor = if (isPlayed) {
                        ForensicCyan
                    } else {
                        Color(0xFF284852)
                    }

                    // Glow behind active played bars
                    if (isPlayed && boostedAmp > 0.3f) {
                        drawLine(
                            color = ForensicCyanGlow,
                            start = Offset(x, centerY - barHeight / 2f - 2f),
                            end = Offset(x, centerY + barHeight / 2f + 2f),
                            strokeWidth = barWidth * 2.2f,
                            cap = StrokeCap.Round
                        )
                    }

                    drawLine(
                        color = barColor,
                        start = Offset(x, centerY - barHeight / 2f),
                        end = Offset(x, centerY + barHeight / 2f),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }

                // Draw Playhead Cursor
                drawLine(
                    color = Color.White,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, height),
                    strokeWidth = 2.5f
                )
                // Playhead glowing head
                drawCircle(
                    color = ForensicCyan,
                    radius = 5f,
                    center = Offset(playheadX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5f,
                    center = Offset(playheadX, centerY)
                )
            } else {
                // Fallback placeholder sine wave
                drawPlaceholderWaveform(width, height, centerY)
            }
        }
    }
}

private fun DrawScope.drawPlaceholderWaveform(width: Float, height: Float, centerY: Float) {
    val path = Path()
    path.moveTo(0f, centerY)
    val steps = 80
    for (i in 0..steps) {
        val x = (i.toFloat() / steps) * width
        val y = centerY + kotlin.math.sin(i * 0.4) * (height * 0.3f)
        path.lineTo(x, y.toFloat())
    }
    drawPath(
        path = path,
        color = ForensicCyan.copy(alpha = 0.5f),
        style = Stroke(width = 2f)
    )
}
