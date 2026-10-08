package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.FftProcessor
import com.example.dsp.FftProcessor.Companion.HeatmapPalette
import com.example.dsp.SpectrogramResult
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSpectrogramBg
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary
import com.example.viewmodel.InspectionPoint
import java.util.Locale
import kotlin.math.max

@Composable
fun SpectrogramCanvas(
    spectrogram: SpectrogramResult?,
    currentPositionMs: Long,
    durationMs: Long,
    inspectionPoint: InspectionPoint?,
    onInspectPoint: (freqHz: Float, db: Float, timeMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    showControls: Boolean = true
) {
    var selectedPalette by remember { mutableStateOf(HeatmapPalette.CYBER_NEON) }
    var vocalFocusMode by remember { mutableStateOf(true) } // true = 0..6000 Hz vocal zoom, false = full 0..22050 Hz
    var dbFloor by remember { mutableFloatStateOf(-82f) }  // Dynamic range floor

    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.argb(190, 185, 200, 220)
            textSize = 22f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val formantPaint = remember {
        Paint().apply {
            color = android.graphics.Color.argb(215, 0, 255, 157)
            textSize = 21f
            isAntiAlias = true
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    val hudPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 25f
            isAntiAlias = true
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showControls) {
            // Top Spectral Controls Row: Palette + Vocal Focus Zoom + Sensitivity
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Frequency Range Mode Toggle (Vocal 0-6kHz vs Full 0-22kHz)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (vocalFocusMode) Color(0xFF0E3328) else Color(0xFF181E2C))
                        .border(
                            1.dp,
                            if (vocalFocusMode) ForensicNeonGreen else Color(0xFF2B3244),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { vocalFocusMode = !vocalFocusMode }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("spectrogram_vocal_zoom_toggle")
                ) {
                    Text(
                        text = if (vocalFocusMode) "🎯 Pásmo řeči (0–6 kHz)" else "🌐 Plné spektrum (0–22 kHz)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (vocalFocusMode) ForensicNeonGreen else ForensicTextSecondary
                    )
                }

                // Heatmap Palette Selector Chips
                HeatmapPalette.entries.forEach { palette ->
                    val isSelected = selectedPalette == palette
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF122C38) else Color(0xFF151922))
                            .border(
                                1.dp,
                                if (isSelected) ForensicCyan else Color(0xFF262C3A),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedPalette = palette }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = palette.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) ForensicCyan else ForensicTextSecondary
                        )
                    }
                }

                // Whisper Gain Floor Sensitivity toggle
                val isHighSens = dbFloor <= -85f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isHighSens) Color(0xFF2E2015) else Color(0xFF151922))
                        .border(
                            1.dp,
                            if (isHighSens) ForensicOrange else Color(0xFF262C3A),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { dbFloor = if (isHighSens) -72f else -88f }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isHighSens) "Citlivost: Šepot (-88dB)" else "Citlivost: Standard (-72dB)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighSens) ForensicOrange else ForensicTextSecondary
                    )
                }
            }
        }

        // Main Heatmap Canvas + Right-Side dBFS Color Scale Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF222938), RoundedCornerShape(10.dp))
                .background(ForensicSpectrogramBg)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("fft_spectrogram_canvas")
                        .pointerInput(spectrogram, durationMs, vocalFocusMode) {
                            detectTapGestures { offset ->
                                if (spectrogram != null && durationMs > 0) {
                                    probeAtOffset(
                                        offset = offset,
                                        width = size.width.toFloat(),
                                        height = size.height.toFloat(),
                                        spectrogram = spectrogram,
                                        durationMs = durationMs,
                                        vocalFocusMode = vocalFocusMode,
                                        onInspectPoint = onInspectPoint
                                    )
                                }
                            }
                        }
                        .pointerInput(spectrogram, durationMs, vocalFocusMode) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                if (spectrogram != null && durationMs > 0) {
                                    probeAtOffset(
                                        offset = change.position,
                                        width = size.width.toFloat(),
                                        height = size.height.toFloat(),
                                        spectrogram = spectrogram,
                                        durationMs = durationMs,
                                        vocalFocusMode = vocalFocusMode,
                                        onInspectPoint = onInspectPoint
                                    )
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
                        val maxDisplayedFreq = if (vocalFocusMode) {
                            6000f.coerceAtMost(spectrogram.nyquistFreq)
                        } else {
                            spectrogram.nyquistFreq
                        }
                        val maxDisplayBin = spectrogram.frequencyToBin(maxDisplayedFreq).coerceAtLeast(16)

                        val numBands = 84
                        val bandHeight = height / numBands
                        val dynamicSpan = (-8f - dbFloor).coerceAtLeast(30f)

                        // Render 2D FFT Heatmap Matrix
                        for (c in 0 until numCols) {
                            val spectrum = columns[c]
                            val x = c * colWidth

                            for (band in 0 until numBands) {
                                val startBin = (band * maxDisplayBin) / numBands
                                val endBin = (((band + 1) * maxDisplayBin) / numBands).coerceAtLeast(startBin + 1)

                                var maxDb = -95f
                                for (b in startBin until endBin.coerceAtMost(spectrum.size)) {
                                    if (spectrum[b] > maxDb) {
                                        maxDb = spectrum[b]
                                    }
                                }

                                val intensity = ((maxDb - dbFloor) / dynamicSpan).coerceIn(0f, 1f)
                                val color = FftProcessor.intensityToHeatmapColor(intensity, selectedPalette)
                                val y = height - (band + 1) * bandHeight

                                drawRect(
                                    color = color,
                                    topLeft = Offset(x, y),
                                    size = Size(colWidth + 0.6f, bandHeight + 0.6f)
                                )
                            }
                        }

                        // Draw Vocal Formant Reference Guides & Frequency Grid
                        val gridMarkers = if (vocalFocusMode) {
                            listOf(
                                Triple(150f, "150Hz (F0 Základ hlasu)", true),
                                Triple(650f, "650Hz (Formant F1)", true),
                                Triple(1650f, "1.65kHz (Formant F2 - Srozumitelnost)", true),
                                Triple(2850f, "2.85kHz (Formant F3 - Šepot)", true),
                                Triple(4500f, "4.5kHz (Sykavky)", false)
                            )
                        } else {
                            listOf(
                                Triple(300f, "300Hz (HPF Řeč)", true),
                                Triple(1500f, "1.5kHz (Formant F2)", true),
                                Triple(3800f, "3.8kHz (Horní hranice hlasu)", true),
                                Triple(8000f, "8kHz", false),
                                Triple(14000f, "14kHz", false),
                                Triple(18000f, "18kHz", false)
                            )
                        }

                        for ((freq, label, isVocal) in gridMarkers) {
                            if (freq <= maxDisplayedFreq) {
                                val fracY = freq / maxDisplayedFreq
                                val y = height - fracY * height
                                if (y in 14f..(height - 6f)) {
                                    drawLine(
                                        color = if (isVocal) Color(0x4400FF9D) else Color(0x22FFFFFF),
                                        start = Offset(0f, y),
                                        end = Offset(width, y),
                                        strokeWidth = if (isVocal) 1.5f else 1f
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            label,
                                            10f,
                                            y - 5f,
                                            if (isVocal) formantPaint else textPaint
                                        )
                                    }
                                }
                            }
                        }

                        // Draw Playhead Cursor Line
                        if (durationMs > 0) {
                            val playheadX = (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) * width
                            drawLine(
                                color = Color.White,
                                start = Offset(playheadX, 0f),
                                end = Offset(playheadX, height),
                                strokeWidth = 2.2f
                            )
                        }

                        // Draw Interactive Frequency Probe Crosshairs & Classification HUD
                        if (inspectionPoint != null) {
                            val inspectTimeFrac = (inspectionPoint.timeMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                            val inspectX = inspectTimeFrac * width
                            val clampedFreq = inspectionPoint.frequencyHz.coerceIn(0f, maxDisplayedFreq)
                            val inspectY = height - (clampedFreq / maxDisplayedFreq) * height

                            drawLine(
                                color = ForensicOrange,
                                start = Offset(0f, inspectY),
                                end = Offset(width, inspectY),
                                strokeWidth = 1.6f
                            )
                            drawLine(
                                color = ForensicOrange,
                                start = Offset(inspectX, 0f),
                                end = Offset(inspectX, height),
                                strokeWidth = 1.6f
                            )
                            drawCircle(
                                color = ForensicOrange,
                                radius = 6.5f,
                                center = Offset(inspectX, inspectY)
                            )

                            val bandClass = classifyFrequencyBand(inspectionPoint.frequencyHz)
                            drawIntoCanvas { canvas ->
                                val hudText = "⚡ ${inspectionPoint.frequencyHz.toInt()} Hz (${String.format(Locale.US, "%.1f", inspectionPoint.decibels)} dB) • $bandClass"
                                val textWidth = hudPaint.measureText(hudText)
                                val hudX = (inspectX + 14f).coerceAtMost((width - textWidth - 20f).coerceAtLeast(10f))
                                val hudY = (inspectY - 14f).coerceIn(34f, height - 14f)

                                val hudBgPaint = Paint().apply {
                                    color = android.graphics.Color.argb(230, 10, 14, 22)
                                    style = Paint.Style.FILL
                                }
                                canvas.nativeCanvas.drawRoundRect(
                                    hudX - 10f,
                                    hudY - 28f,
                                    hudX + textWidth + 10f,
                                    hudY + 10f,
                                    8f,
                                    8f,
                                    hudBgPaint
                                )
                                canvas.nativeCanvas.drawText(hudText, hudX, hudY, hudPaint)
                            }
                        }
                    } else {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                "Výpočet FFT spektrální tepelné mapy...",
                                width / 2f - 190f,
                                height / 2f,
                                textPaint
                            )
                        }
                    }
                }
            }

            // Right-side dBFS Thermal Scale Legend
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0B0E15))
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0dB", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = ForensicTextPrimary)
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    FftProcessor.intensityToHeatmapColor(1.0f, selectedPalette),
                                    FftProcessor.intensityToHeatmapColor(0.75f, selectedPalette),
                                    FftProcessor.intensityToHeatmapColor(0.5f, selectedPalette),
                                    FftProcessor.intensityToHeatmapColor(0.25f, selectedPalette),
                                    FftProcessor.intensityToHeatmapColor(0.0f, selectedPalette)
                                )
                            )
                        )
                )
                Text("${dbFloor.toInt()}", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = ForensicTextMuted)
            }
        }

        // Real-Time Instantaneous FFT Band Analyzer at Current Playhead Position
        if (spectrogram != null && spectrogram.columns.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            InstantFftBarAnalyzer(
                spectrogram = spectrogram,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                palette = selectedPalette
            )
        }
    }
}

@Composable
private fun InstantFftBarAnalyzer(
    spectrogram: SpectrogramResult,
    currentPositionMs: Long,
    durationMs: Long,
    palette: HeatmapPalette
) {
    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val currentColumn = spectrogram.getColumnAtFraction(progress) ?: return

    val bands = remember {
        listOf(
            60f to "60Hz",
            150f to "150Hz",
            350f to "350Hz",
            700f to "700Hz",
            1200f to "1.2k",
            2000f to "2.0k",
            3000f to "3.0k",
            4500f to "4.5k",
            8000f to "8k",
            14000f to "14k"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E121B))
            .border(1.dp, Color(0xFF1F2637), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Okamžité FFT spektrum v bodě přehrávání",
                fontSize = 10.sp,
                color = ForensicTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Zelené pásmo = Lidský hlas (150 Hz – 3.5 kHz)",
                fontSize = 9.sp,
                color = ForensicNeonGreen
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bands.forEach { (freqHz, label) ->
                val bin = spectrogram.frequencyToBin(freqHz)
                // Average nearby bins for stable bar reading
                var sumDb = 0f
                var count = 0
                for (b in (bin - 2).coerceAtLeast(0)..(bin + 2).coerceAtMost(currentColumn.size - 1)) {
                    sumDb += currentColumn[b]
                    count++
                }
                val avgDb = if (count > 0) sumDb / count else -85f
                val norm = ((avgDb + 85f) / 75f).coerceIn(0.06f, 1.0f)
                val isVocalBand = freqHz in 150f..3500f

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .fillMaxHeight(norm)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(
                                    if (isVocalBand) {
                                        FftProcessor.intensityToHeatmapColor(norm.coerceAtLeast(0.4f), palette)
                                    } else {
                                        ForensicCyan.copy(alpha = 0.55f)
                                    }
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isVocalBand) ForensicNeonGreen else ForensicTextMuted
                    )
                }
            }
        }
    }
}

private fun probeAtOffset(
    offset: Offset,
    width: Float,
    height: Float,
    spectrogram: SpectrogramResult,
    durationMs: Long,
    vocalFocusMode: Boolean,
    onInspectPoint: (freqHz: Float, db: Float, timeMs: Long) -> Unit
) {
    val timeFraction = (offset.x / width).coerceIn(0f, 1f)
    val timeMs = (timeFraction * durationMs).toLong()

    val maxDisplayedFreq = if (vocalFocusMode) {
        6000f.coerceAtMost(spectrogram.nyquistFreq)
    } else {
        spectrogram.nyquistFreq
    }

    val yFraction = 1.0f - (offset.y / height).coerceIn(0f, 1f)
    val freqHz = (yFraction * maxDisplayedFreq).coerceIn(20f, maxDisplayedFreq)

    val colIdx = (timeFraction * (spectrogram.columns.size - 1)).toInt()
        .coerceIn(0, spectrogram.columns.size - 1)
    val col = spectrogram.columns[colIdx]
    val bin = spectrogram.frequencyToBin(freqHz)
    val db = if (bin in col.indices) col[bin] else -75f

    onInspectPoint(freqHz, db, timeMs)
}

private fun classifyFrequencyBand(freqHz: Float): String {
    return when {
        freqHz < 85f -> "Sub-bas / Brum sítě"
        freqHz < 260f -> "F0 Základní tón hlasu"
        freqHz < 900f -> "Formant F1 (Samohlásky)"
        freqHz < 2400f -> "Formant F2 (Srozumitelnost řeči)"
        freqHz < 3800f -> "Formant F3 (Šepot / Barva hlasu)"
        freqHz < 6500f -> "Sykavky (S, Š, C, Č)"
        else -> "Vysokofrekvenční šum"
    }
}
