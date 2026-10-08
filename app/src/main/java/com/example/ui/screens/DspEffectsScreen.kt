package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.model.DspConfig
import com.example.ui.components.ReferenceSubtractionCard
import com.example.ui.components.VoiceSeparationCard
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicDarkBackground
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary
import com.example.viewmodel.AudioDetectiveViewModel

@Composable
fun DspEffectsScreen(
    viewModel: AudioDetectiveViewModel,
    dspConfig: DspConfig,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val voiceStats by viewModel.voiceSeparationStats.collectAsStateWithLifecycle()
    val audioFiles by viewModel.audioFiles.collectAsStateWithLifecycle()
    val selectedAudio by viewModel.selectedAudio.collectAsStateWithLifecycle()
    val learnedProfile by viewModel.learnedProfile.collectAsStateWithLifecycle()
    val refAttenuation by viewModel.referenceAttenuation.collectAsStateWithLifecycle()
    val refMatchGain by viewModel.referenceMatchGain.collectAsStateWithLifecycle()
    val refOffsetMs by viewModel.referenceOffsetMs.collectAsStateWithLifecycle()
    val protectFormants by viewModel.protectVoiceFormants.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ForensicDarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = ForensicCyan,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Forensic DSP & Separace Hlasu",
                        style = MaterialTheme.typography.titleLarge,
                        color = ForensicTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "STFT separace řeči, izolace formantů a potlačení šumu",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForensicTextSecondary
                    )
                }
            }

            // 0. STFT Voice Separation & Formant Isolation Card
            VoiceSeparationCard(
                dspConfig = dspConfig,
                stats = voiceStats,
                onToggleEnabled = { viewModel.toggleVoiceSeparation(it) },
                onSelectMode = { viewModel.setVoiceSeparationMode(it) },
                onStrengthChange = { viewModel.setVoiceSeparationStrength(it) },
                onFormantBoostChange = { viewModel.setVocalFormantBoost(it) },
                onSeparateAndSave = { viewModel.separateVoiceAndSave() }
            )

            // 0B. 2-Step Reference Song & Sample Profile Subtraction Card
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

            // 1. High-Pass Filter (Voice Isolation / Low Rumble cut)
            FilterCard(
                title = "1. High-Pass Filter (HPF)",
                subtitle = "Cuts out AC hum, motor drone, and rumble below cutoff.",
                enabled = dspConfig.highPassEnabled,
                onToggle = { viewModel.toggleHighPass(it) },
                accentColor = ForensicOrange
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cutoff Frequency",
                            fontSize = 12.sp,
                            color = ForensicTextSecondary
                        )
                        Text(
                            text = "${dspConfig.highPassCutoffHz.toInt()} Hz",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForensicOrange
                        )
                    }

                    Slider(
                        value = dspConfig.highPassCutoffHz,
                        onValueChange = { viewModel.setHighPassCutoff(it) },
                        valueRange = 80f..1000f,
                        enabled = dspConfig.highPassEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = ForensicOrange,
                            activeTrackColor = ForensicOrange,
                            inactiveTrackColor = Color(0xFF2E241E)
                        ),
                        modifier = Modifier.testTag("high_pass_slider")
                    )
                }
            }

            // 2. Low-Pass Filter (Hiss / Ultrasonic noise cut)
            FilterCard(
                title = "2. Low-Pass Filter (LPF)",
                subtitle = "Cuts out high-frequency hiss and electromagnetic whine above cutoff.",
                enabled = dspConfig.lowPassEnabled,
                onToggle = { viewModel.toggleLowPass(it) },
                accentColor = ForensicCyan
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cutoff Frequency",
                            fontSize = 12.sp,
                            color = ForensicTextSecondary
                        )
                        Text(
                            text = "${dspConfig.lowPassCutoffHz.toInt()} Hz",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForensicCyan
                        )
                    }

                    Slider(
                        value = dspConfig.lowPassCutoffHz,
                        onValueChange = { viewModel.setLowPassCutoff(it) },
                        valueRange = 2000f..10000f,
                        enabled = dspConfig.lowPassEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = ForensicCyan,
                            activeTrackColor = ForensicCyan,
                            inactiveTrackColor = Color(0xFF1E2838)
                        ),
                        modifier = Modifier.testTag("low_pass_slider")
                    )
                }
            }

            // 3. Whisper Dynamic Expander / Booster
            FilterCard(
                title = "3. Whisper Dynamic Expander",
                subtitle = "Selectively amplifies low-level speech with soft-knee limiting.",
                enabled = dspConfig.whisperBoostEnabled,
                onToggle = { viewModel.toggleWhisperBoost(it) },
                accentColor = ForensicNeonGreen
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Faint Speech Boost Gain",
                            fontSize = 12.sp,
                            color = ForensicTextSecondary
                        )
                        Text(
                            text = "+${dspConfig.whisperBoostGainDb.toInt()} dB",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForensicNeonGreen
                        )
                    }

                    Slider(
                        value = dspConfig.whisperBoostGainDb,
                        onValueChange = { viewModel.setWhisperBoostGain(it) },
                        valueRange = 3f..30f,
                        enabled = dspConfig.whisperBoostEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = ForensicNeonGreen,
                            activeTrackColor = ForensicNeonGreen,
                            inactiveTrackColor = Color(0xFF162B20)
                        ),
                        modifier = Modifier.testTag("whisper_boost_slider")
                    )
                }
            }

            // 4. Spectral Subtraction Hum/Fan Gate
            FilterCard(
                title = "4. Stationary Noise / Fan Hum Reducer",
                subtitle = "Estimates constant background frequency profile and subtracts it.",
                enabled = dspConfig.noiseReductionEnabled,
                onToggle = { viewModel.toggleNoiseReduction(it) },
                accentColor = Color(0xFFC792EA)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Noise Attenuation Strength",
                            fontSize = 12.sp,
                            color = ForensicTextSecondary
                        )
                        Text(
                            text = "${(dspConfig.noiseReductionLevel * 100).toInt()}%",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC792EA)
                        )
                    }

                    Slider(
                        value = dspConfig.noiseReductionLevel,
                        onValueChange = { viewModel.setNoiseReductionLevel(it) },
                        valueRange = 0.1f..1.0f,
                        enabled = dspConfig.noiseReductionEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFC792EA),
                            activeTrackColor = Color(0xFFC792EA),
                            inactiveTrackColor = Color(0xFF2E2238)
                        ),
                        modifier = Modifier.testTag("noise_reduction_slider")
                    )
                }
            }

            // 5. Pitch-Preserved Time Stretch
            FilterCard(
                title = "5. Pitch-Locked Time Stretch",
                subtitle = "Slows down speech to 0.5x - 0.75x without dropping pitch to deep bass.",
                enabled = true,
                onToggle = {},
                accentColor = ForensicCyan,
                showToggle = false
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Speed Factor",
                            fontSize = 12.sp,
                            color = ForensicTextSecondary
                        )
                        Text(
                            text = "${dspConfig.playbackSpeed}x",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForensicCyan
                        )
                    }

                    Slider(
                        value = dspConfig.playbackSpeed,
                        onValueChange = { viewModel.setPlaybackSpeed(it) },
                        valueRange = 0.5f..1.5f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = ForensicCyan,
                            activeTrackColor = ForensicCyan,
                            inactiveTrackColor = Color(0xFF1E2838)
                        ),
                        modifier = Modifier.testTag("time_stretch_slider")
                    )
                }
            }

            // Save Copy Button
            Button(
                onClick = { viewModel.processAndSaveCopy("Cleaned_DSP") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("bounce_dsp_copy_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForensicCyan,
                    contentColor = Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Process & Save Cleaned Audio Copy", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun FilterCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    accentColor: Color,
    showToggle: Boolean = true,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ForensicSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled && showToggle) accentColor.copy(alpha = 0.5f) else Color(0xFF252A38)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled && showToggle) accentColor else ForensicTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ForensicTextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (showToggle) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00363D),
                            checkedTrackColor = accentColor,
                            uncheckedTrackColor = Color(0xFF222634)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
