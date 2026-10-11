package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.FftProcessor
import com.example.dsp.LearnedSourceType
import com.example.dsp.LearnedSpectralProfile
import com.example.model.AudioFileItem
import com.example.model.DspConfig
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSurface
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary
import java.util.Locale

@Composable
fun ReferenceSubtractionCard(
    dspConfig: DspConfig,
    audioFiles: List<AudioFileItem>,
    selectedAudio: AudioFileItem?,
    learnedProfile: LearnedSpectralProfile?,
    attenuationStrength: Float,
    spectralMatchGain: Float,
    timeOffsetMs: Long,
    protectVoiceFormants: Boolean,
    onLearnFromAbInterval: () -> Unit,
    onLearnFromLibraryTrack: (AudioFileItem) -> Unit,
    onLearnFromExternalUri: (android.net.Uri) -> Unit,
    onAutoLearnFromMusic: () -> Unit,
    onClearProfile: () -> Unit,
    onAttenuationChange: (Float) -> Unit,
    onMatchGainChange: (Float) -> Unit,
    onOffsetChange: (Long) -> Unit,
    onProtectFormantsChange: (Boolean) -> Unit,
    onApplySubtractionToWholeTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLibraryPicker by remember { mutableStateOf(false) }

    val referenceFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onLearnFromExternalUri(uri)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ForensicSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (learnedProfile != null) ForensicOrange.copy(alpha = 0.7f) else Color(0xFF283042)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reference_subtraction_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2D1B14)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicOff,
                            contentDescription = "Odečtení referenční hudby a vzorku šumu",
                            tint = ForensicOrange,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Odečtení známé písničky / vzorku šumu",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (learnedProfile != null) ForensicOrange else ForensicTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. Získejte vzorek z úseku A–B nebo souboru písničky → 2. Vyčistěte celou nahrávku",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForensicTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (learnedProfile != null) {
                    IconButton(
                        onClick = onClearProfile,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("clear_learned_profile_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Smazat naučený vzorek",
                            tint = ForensicTextMuted
                        )
                    }
                }
            }

            // STEP 1: Acquire Sample / Reference Track
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ForensicSurface)
                    .border(1.dp, Color(0xFF232938), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "KROK 1: ZÍSKAT VZOREK RUŠIVÉ STOPY (SPEKTRÁLNÍ OTISK)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForensicCyan
                    )
                    val abStartSec = String.format(Locale.US, "%.1fs", dspConfig.loopStartMs / 1000f)
                    val abEndSec = String.format(Locale.US, "%.1fs", dspConfig.loopEndMs / 1000f)
                    Text(
                        text = "A–B: $abStartSec – $abEndSec",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = ForensicNeonGreen
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Button 1A: Learn from A-B interval
                    OutlinedButton(
                        onClick = onLearnFromAbInterval,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("learn_ab_sample_btn"),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ForensicCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = ForensicCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Vzorek z A–B",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForensicCyan,
                            maxLines = 1
                        )
                    }

                    // Button 1B: Pick Reference Track from Library
                    OutlinedButton(
                        onClick = { showLibraryPicker = !showLibraryPicker },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("toggle_ref_library_btn"),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ForensicOrange)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = ForensicOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Známá písnička",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForensicOrange,
                            maxLines = 1
                        )
                    }
                }

                // Button 1C: Load external song file from phone storage
                OutlinedButton(
                    onClick = { referenceFileLauncher.launch("audio/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("import_external_reference_song_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF344156))
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = ForensicTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vybrat soubor známé písničky z telefonu (.mp3 / .wav / .m4a)",
                        fontSize = 11.sp,
                        color = ForensicTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onAutoLearnFromMusic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("auto_detect_music_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ForensicNeonGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = ForensicNeonGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Automaticky najít hudbu v nahrávce a naučit profil",
                        fontSize = 11.sp,
                        color = ForensicNeonGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Expandable quick library selector for known song reference
                AnimatedVisibility(visible = showLibraryPicker) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Vyberte referenční stopu (známou skladbu) z knihovny:",
                            fontSize = 11.sp,
                            color = ForensicTextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val candidateTracks = audioFiles.filter { it.id != selectedAudio?.id }
                            candidateTracks.forEach { item ->
                                val isKnownRef = item.fileName.contains("Reference", ignoreCase = true) ||
                                    item.fileName.contains("Song", ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isKnownRef) Color(0xFF2D1B14) else Color(0xFF1A202C))
                                        .border(
                                            1.dp,
                                            if (isKnownRef) ForensicOrange else Color(0xFF2E384D),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            onLearnFromLibraryTrack(item)
                                            showLibraryPicker = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = item.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isKnownRef) ForensicOrange else ForensicTextPrimary
                                        )
                                        Text(
                                            text = "${item.durationFormatted} • ${item.sampleRate} Hz",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = ForensicTextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // LEARNED SPECTRAL PROFILE VISUALIZATION & STEP 2 CONTROLS
            if (learnedProfile == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10131A))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Zatím není načten žádný vzorek. Označte na časové ose úsek A–B s rušivým zvukem nebo načtěte soubor známé písničky.",
                        fontSize = 11.sp,
                        color = ForensicTextMuted
                    )
                }
            } else {
                // Learned Profile Spectrogram & Metrics Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0E121A))
                        .border(1.dp, ForensicOrange.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AKTIVNÍ VZOREK: ${learnedProfile.sourceName}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForensicOrange,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = learnedProfile.sourceType.labelCz,
                                fontSize = 10.sp,
                                color = ForensicTextSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF162922))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${learnedProfile.fftSize}-pt FFT OTISK",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForensicNeonGreen
                            )
                        }
                    }

                    // Mini Spectrogram Heatmap of the Reference Song / Sample
                    Text(
                        text = "Spektrogram naučeného vzorku / písničky (bude odečten z nahrávky):",
                        fontSize = 10.sp,
                        color = ForensicTextSecondary
                    )

                    ReferenceMiniSpectrogram(
                        profile = learnedProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF252D3D), RoundedCornerShape(8.dp))
                    )

                    // Metrics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Délka vzorku: ${String.format(Locale.US, "%.1f s", learnedProfile.durationMs / 1000f)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = ForensicTextSecondary
                        )
                        Text(
                            text = "Dom. frekvence: ${learnedProfile.dominantFreqHz.toInt()} Hz",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = ForensicCyan
                        )
                        Text(
                            text = "Úroveň: ${learnedProfile.averageDb.toInt()} dB",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = ForensicNeonGreen
                        )
                    }
                }

                // STEP 2: Subtraction Parameters & Apply Button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ForensicSurface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "KROK 2: PARAMETRY ODEČTENÍ Z CELÉ NAHRÁVKY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForensicNeonGreen
                    )

                    // Slider 1: Attenuation Strength
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Síla potlačení vzorku / písničky",
                                fontSize = 12.sp,
                                color = ForensicTextSecondary
                            )
                            Text(
                                text = "${(attenuationStrength * 100).toInt()} %",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForensicOrange
                            )
                        }
                        Slider(
                            value = attenuationStrength,
                            onValueChange = onAttenuationChange,
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = ForensicOrange,
                                activeTrackColor = ForensicOrange,
                                inactiveTrackColor = Color(0xFF2E201A)
                            ),
                            modifier = Modifier.testTag("ref_attenuation_slider")
                        )
                    }

                    // Slider 2: Room / Volume Match Gain
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Kompenzace hlasitosti v místnosti (Gain Match)",
                                fontSize = 12.sp,
                                color = ForensicTextSecondary
                            )
                            Text(
                                text = String.format(Locale.US, "%.2fx", spectralMatchGain),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForensicCyan
                            )
                        }
                        Slider(
                            value = spectralMatchGain,
                            onValueChange = onMatchGainChange,
                            valueRange = 0.5f..2.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = ForensicCyan,
                                activeTrackColor = ForensicCyan,
                                inactiveTrackColor = Color(0xFF1A2836)
                            ),
                            modifier = Modifier.testTag("ref_gain_match_slider")
                        )
                    }

                    // Time Alignment Offset (for Reference Audio Files)
                    if (learnedProfile.sourceType == LearnedSourceType.REFERENCE_AUDIO_FILE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Časová synchronizace skladby",
                                    fontSize = 11.sp,
                                    color = ForensicTextSecondary
                                )
                                Text(
                                    text = "Offset: ${timeOffsetMs} ms (auto-korelace)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = ForensicCyan
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E2636))
                                        .clickable { onOffsetChange((timeOffsetMs - 100L).coerceAtLeast(0L)) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("-100 ms", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ForensicTextPrimary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E2636))
                                        .clickable { onOffsetChange(0L) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("0 ms", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ForensicCyan)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E2636))
                                        .clickable { onOffsetChange(timeOffsetMs + 100L) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("+100 ms", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ForensicTextPrimary)
                                }
                            }
                        }
                    }

                    // Protect Vocal Formants Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Chránit řečové formanty F1–F3 (300–3400 Hz)",
                                fontSize = 12.sp,
                                color = ForensicTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Zabraňuje zkreslení lidského hlasu při odečítání hudby",
                                fontSize = 10.sp,
                                color = ForensicTextMuted
                            )
                        }
                        Switch(
                            checked = protectVoiceFormants,
                            onCheckedChange = onProtectFormantsChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00361F),
                                checkedTrackColor = ForensicNeonGreen
                            )
                        )
                    }

                    // Primary Action: Apply to Entire Recording
                    Button(
                        onClick = onApplySubtractionToWholeTrack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("apply_learned_profile_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForensicOrange,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Krok 2: Vyčistit celou nahrávku (Odečíst vzorek)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceMiniSpectrogram(
    profile: LearnedSpectralProfile,
    modifier: Modifier = Modifier
) {
    val spec = profile.spectrogramPreview
    Box(modifier = modifier.background(Color(0xFF06080D))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = spec.columns
            if (cols.isEmpty()) return@Canvas

            val numCols = cols.size
            val numBins = spec.numBins.coerceAtLeast(1)
            // Focus on 0 - 8000 Hz where music & noise prints dominate
            val maxBin = (numBins * 0.45f).toInt().coerceIn(16, numBins)
            val colW = size.width / numCols
            val rowBins = 32
            val cellH = size.height / rowBins

            for (c in 0 until numCols) {
                val colData = cols[c]
                val x = c * colW
                for (r in 0 until rowBins) {
                    val binStart = (r * maxBin) / rowBins
                    val binEnd = (((r + 1) * maxBin) / rowBins).coerceAtLeast(binStart + 1)
                    var peakDb = -100f
                    for (b in binStart until binEnd.coerceAtMost(colData.size)) {
                        if (colData[b] > peakDb) peakDb = colData[b]
                    }
                    val intensity = ((peakDb + 88f) / 88f).coerceIn(0f, 1f)
                    val color = FftProcessor.intensityToHeatmapColor(
                        intensity = intensity,
                        palette = FftProcessor.Companion.HeatmapPalette.THERMAL_IR
                    )
                    val y = size.height - ((r + 1) * cellH)
                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(colW + 1f, cellH + 1f)
                    )
                }
            }
        }
    }
}
