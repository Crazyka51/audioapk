package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AudioFileItem
import com.example.model.DspConfig
import com.example.ui.components.AbLoopController
import com.example.ui.components.ReferenceSubtractionCard
import com.example.ui.components.SpectrogramCanvas
import com.example.ui.components.VoiceSeparationCard
import com.example.ui.components.WaveformView
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicDarkBackground
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSurface
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary
import com.example.viewmodel.AudioDetectiveViewModel
import com.example.viewmodel.InspectionPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: AudioDetectiveViewModel,
    selectedAudio: AudioFileItem?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    pcmSamples: FloatArray?,
    dspConfig: DspConfig,
    inspectionPoint: InspectionPoint?,
    onOpenEffectsRack: () -> Unit,
    onGenerateReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomLevel by remember { mutableStateOf(1.0f) }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ForensicDarkBackground)
    ) {
        if (selectedAudio == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No audio selected.\nPlease choose a recording from the Dashboard or Library.",
                    color = ForensicTextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Track Info Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedAudio.fileName,
                            style = MaterialTheme.typography.titleLarge,
                            color = ForensicTextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${selectedAudio.sampleRate} Hz • 16-bit Mono • ${selectedAudio.fileSizeFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForensicCyan
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { zoomLevel = (zoomLevel * 1.3f).coerceAtMost(4.0f) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = ForensicTextSecondary)
                        }
                        IconButton(
                            onClick = { zoomLevel = (zoomLevel / 1.3f).coerceAtLeast(1.0f) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = ForensicTextSecondary)
                        }
                    }
                }

                // Dual Visualizer Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ForensicSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C3A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        // Section 1: Amplitude Waveform
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. Amplitude Waveform (RMS Envelope)",
                                style = MaterialTheme.typography.labelMedium,
                                color = ForensicCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${formatMsPrecise(currentPositionMs)} / ${formatMsPrecise(durationMs)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ForensicTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0A0F17))
                        ) {
                            WaveformView(
                                samples = pcmSamples,
                                currentPositionMs = currentPositionMs,
                                durationMs = durationMs,
                                abLoopEnabled = dspConfig.abLoopEnabled,
                                loopStartMs = dspConfig.loopStartMs,
                                loopEndMs = dspConfig.loopEndMs,
                                onSeek = { viewModel.seekTo(it) },
                                zoomLevel = zoomLevel,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 2: Spectrogram Heatmap (FFT 1024)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. FFT Spektrální analýza (Tepelná mapa frekvencí)",
                                style = MaterialTheme.typography.labelMedium,
                                color = ForensicNeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                            if (inspectionPoint != null) {
                                Text(
                                    text = "Sonda: ${inspectionPoint.frequencyHz.toInt()} Hz",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = ForensicOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val specResult by viewModel.spectrogram.collectAsStateWithLifecycle()
                        SpectrogramCanvas(
                            spectrogram = specResult,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            inspectionPoint = inspectionPoint,
                            onInspectPoint = { freq, db, time ->
                                viewModel.inspectPoint(freq, db, time)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Inspection legend
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Klepněte nebo táhněte po spektrogramu pro odečet Hz, dBFS a formantů",
                                fontSize = 10.sp,
                                color = ForensicTextMuted
                            )
                            if (inspectionPoint != null) {
                                Text(
                                    text = "Zrušit sondu",
                                    fontSize = 10.sp,
                                    color = ForensicOrange,
                                    modifier = Modifier.clickable { viewModel.clearInspectionPoint() }
                                )
                            }
                        }
                    }
                }

                // Scrub Slider & Main Transport Controls
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ForensicSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C3A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Slider
                        Slider(
                            value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
                            onValueChange = { frac ->
                                if (durationMs > 0) {
                                    viewModel.seekTo((frac * durationMs).toLong())
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = ForensicCyan,
                                activeTrackColor = ForensicCyan,
                                inactiveTrackColor = Color(0xFF263242)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("studio_timeline_slider")
                        )

                        // Transport Buttons: Skip-5s, Large Play/Pause, Skip+5s
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.seekTo((currentPositionMs - 5000L).coerceAtLeast(0L)) },
                                modifier = Modifier.size(44.dp).testTag("skip_backward_5s_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = "Skip back 5s",
                                    tint = ForensicTextPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Large Glowing Play/Pause FAB
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(ForensicCyan)
                                    .border(3.dp, Color(0xFF00B0FF), CircleShape)
                                    .clickable { viewModel.togglePlayPause() }
                                    .testTag("studio_main_play_pause_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color(0xFF00363D),
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            IconButton(
                                onClick = { viewModel.seekTo((currentPositionMs + 5000L).coerceAtMost(durationMs)) },
                                modifier = Modifier.size(44.dp).testTag("skip_forward_5s_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "Skip forward 5s",
                                    tint = ForensicTextPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Playback Speed Selector (0.5x - 1.5x with pitch lock)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Speed:",
                                style = MaterialTheme.typography.bodySmall,
                                color = ForensicTextSecondary,
                                fontSize = 11.sp
                            )

                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                                val isSelectedSpeed = dspConfig.playbackSpeed == speed
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelectedSpeed) ForensicCyan else Color(0xFF1B202C))
                                        .clickable { viewModel.setPlaybackSpeed(speed) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${speed}x",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelectedSpeed) Color(0xFF00363D) else ForensicTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // A-B Forensic Loop Controller
                AbLoopController(
                    config = dspConfig,
                    onToggleLoop = { viewModel.toggleAbLoop(it) },
                    onSetA = { viewModel.setLoopStartToCurrent() },
                    onSetB = { viewModel.setLoopEndToCurrent() },
                    onNudgeA = { viewModel.nudgeLoopStart(it) },
                    onNudgeB = { viewModel.nudgeLoopEnd(it) }
                )

                // 2-Step Spectral Sample & Known Song Subtraction Card
                val audioFiles by viewModel.audioFiles.collectAsStateWithLifecycle()
                val learnedProfile by viewModel.learnedProfile.collectAsStateWithLifecycle()
                val refAttenuation by viewModel.referenceAttenuation.collectAsStateWithLifecycle()
                val refMatchGain by viewModel.referenceMatchGain.collectAsStateWithLifecycle()
                val refOffsetMs by viewModel.referenceOffsetMs.collectAsStateWithLifecycle()
                val protectFormants by viewModel.protectVoiceFormants.collectAsStateWithLifecycle()

                ReferenceSubtractionCard(
                    dspConfig = dspConfig,
                    audioFiles = audioFiles,
                    selectedAudio = selectedAudio,
                    learnedProfile = learnedProfile,
                    attenuationStrength = refAttenuation,
                    spectralMatchGain = refMatchGain,
                    timeOffsetMs = refOffsetMs,
                    protectVoiceFormants = protectFormants,
                    onLearnFromAbInterval = { viewModel.learnProfileFromAbLoop() },
                    onLearnFromLibraryTrack = { viewModel.learnProfileFromLibraryTrack(it) },
                    onLearnFromExternalUri = { viewModel.learnProfileFromExternalUri(it) },
                    onClearProfile = { viewModel.clearLearnedProfile() },
                    onAttenuationChange = { viewModel.setReferenceAttenuation(it) },
                    onMatchGainChange = { viewModel.setReferenceMatchGain(it) },
                    onOffsetChange = { viewModel.setReferenceOffsetMs(it) },
                    onProtectFormantsChange = { viewModel.setProtectVoiceFormants(it) },
                    onApplySubtractionToWholeTrack = { viewModel.applyLearnedProfileToWholeTrack() }
                )

                // STFT Voice Separation & Formant Isolation Card
                val voiceStats by viewModel.voiceSeparationStats.collectAsStateWithLifecycle()
                VoiceSeparationCard(
                    dspConfig = dspConfig,
                    stats = voiceStats,
                    onToggleEnabled = { viewModel.toggleVoiceSeparation(it) },
                    onSelectMode = { viewModel.setVoiceSeparationMode(it) },
                    onStrengthChange = { viewModel.setVoiceSeparationStrength(it) },
                    onFormantBoostChange = { viewModel.setVocalFormantBoost(it) },
                    onSeparateAndSave = { viewModel.separateVoiceAndSave() }
                )

                // Quick Forensic Filter Toggles
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ForensicSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C3A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick DSP Filter Rack",
                                style = MaterialTheme.typography.titleSmall,
                                color = ForensicTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Advanced Sliders →",
                                fontSize = 11.sp,
                                color = ForensicCyan,
                                modifier = Modifier.clickable { onOpenEffectsRack() }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = dspConfig.speechBandpassEnabled,
                                onClick = { viewModel.toggleSpeechBandpass(!dspConfig.speechBandpassEnabled) },
                                label = { Text("Bandpass (300-4kHz)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForensicCyan,
                                    selectedLabelColor = Color(0xFF00363D)
                                ),
                                modifier = Modifier.weight(1f).testTag("quick_speech_bandpass_chip")
                            )

                            FilterChip(
                                selected = dspConfig.whisperBoostEnabled,
                                onClick = { viewModel.toggleWhisperBoost(!dspConfig.whisperBoostEnabled) },
                                label = { Text("Whisper Boost", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForensicNeonGreen,
                                    selectedLabelColor = Color(0xFF00361F)
                                ),
                                modifier = Modifier.weight(1f).testTag("quick_whisper_boost_chip")
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = dspConfig.highPassEnabled,
                                onClick = { viewModel.toggleHighPass(!dspConfig.highPassEnabled) },
                                label = { Text("High-Pass (300Hz)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForensicOrange,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).testTag("quick_high_pass_chip")
                            )

                            FilterChip(
                                selected = dspConfig.noiseReductionEnabled,
                                onClick = { viewModel.toggleNoiseReduction(!dspConfig.noiseReductionEnabled) },
                                label = { Text("Fan/Hum Gate", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF9C27B0),
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).testTag("quick_noise_gate_chip")
                            )
                        }
                    }
                }

                // Action Row: Save Processed Audio & Forensic Report
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.processAndSaveCopy("Cleaned") },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("save_cleaned_copy_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForensicCyan,
                            contentColor = Color(0xFF00363D)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Cleaned Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onGenerateReport,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("studio_generate_report_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E2838),
                            contentColor = ForensicTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp), tint = ForensicNeonGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Forensic Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

private fun formatMsPrecise(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val millis = ms % 1000
    return String.format("%02d:%02d.%03d", min, sec, millis)
}
