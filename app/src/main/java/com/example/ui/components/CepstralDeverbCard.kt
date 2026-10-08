package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.ReverberationMetrics
import com.example.model.DspConfig

private val DeverbCardBg = Color(0xFF131A26)
private val DeverbBorder = Color(0xFF1F2E45)
private val DeverbCyan = Color(0xFF00E5FF)
private val DeverbPurple = Color(0xFFB388FF)
private val ForensicTextPrimary = Color(0xFFF0F4F8)
private val ForensicTextSecondary = Color(0xFF90A4AE)

/**
 * Cepstral De-reverberation (Inverse Room Comb Filter) UI Card.
 * Solves heavy echo in empty rooms, bathrooms, and corridors.
 */
@Composable
fun CepstralDeverbCard(
    dspConfig: DspConfig,
    metrics: ReverberationMetrics?,
    onToggleEnabled: (Boolean) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onManualDelayChange: (Float) -> Unit,
    onApplyAndSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (dspConfig.deverbEnabled) DeverbCyan.copy(alpha = 0.6f) else DeverbBorder, RoundedCornerShape(12.dp))
            .testTag("cepsral_deverb_card"),
        colors = CardDefaults.cardColors(containerColor = DeverbCardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DeverbCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MeetingRoom,
                            contentDescription = "De-reverberation Room Icon",
                            tint = DeverbCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Inverzní filtr místnosti / De-reverberace",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ForensicTextPrimary
                            )
                        }
                        Text(
                            text = "Cepstrální analýza & Inverzní hřebenový filtr pro „vysušení“ ozvěny",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = ForensicTextSecondary
                        )
                    }
                }

                Switch(
                    checked = dspConfig.deverbEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF0D141F),
                        checkedTrackColor = DeverbCyan,
                        uncheckedTrackColor = Color(0xFF242F3E)
                    ),
                    modifier = Modifier.testTag("deverb_switch")
                )
            }

            AnimatedVisibility(visible = dspConfig.deverbEnabled) {
                Column(modifier = Modifier.padding(top = 12.dp)) {

                    // Diagnostic Metrics HUD Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0A101A))
                            .border(1.dp, Color(0xFF1E2B3E), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = "Sensor",
                                        tint = DeverbCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Akustický profil místnosti (Quefrency cepstrum):",
                                        fontSize = 11.sp,
                                        color = ForensicTextSecondary
                                    )
                                }
                                Text(
                                    text = if (metrics != null) "${String.format("%.1f", metrics.detectedDelayMs)} ms" else "Auto-detekce",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeverbCyan
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = metrics?.roomAcousticProfile ?: "Detekce odrazů stěn v pásmu 10–120 ms...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ForensicTextPrimary
                            )

                            if (metrics != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Síla prvního odrazu stěny:",
                                        fontSize = 10.sp,
                                        color = ForensicTextSecondary
                                    )
                                    Text(
                                        text = "${(metrics.estimatedReflectionGain * 100).toInt()}%",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = DeverbPurple,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                LinearProgressIndicator(
                                    progress = { (metrics.estimatedReflectionGain).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = DeverbPurple,
                                    trackColor = Color(0xFF1E2838),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Slider: Intenzita de-reverberace (Dry/Wet comb cancellation)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = "Dryness",
                                tint = DeverbCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Intenzita „vysušení“ (Potlačení odrazů)",
                                fontSize = 12.sp,
                                color = ForensicTextSecondary
                            )
                        }
                        Text(
                            text = "${(dspConfig.deverbStrength * 100).toInt()}%",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeverbCyan
                        )
                    }

                    Slider(
                        value = dspConfig.deverbStrength,
                        onValueChange = onStrengthChange,
                        valueRange = 0.10f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = DeverbCyan,
                            activeTrackColor = DeverbCyan,
                            inactiveTrackColor = Color(0xFF243246)
                        ),
                        modifier = Modifier.testTag("deverb_strength_slider")
                    )

                    // Optional Manual Delay Override (0 = auto)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Manuální zpoždění odrazu (0 = Cepstrální auto)",
                            fontSize = 11.sp,
                            color = ForensicTextSecondary
                        )
                        Text(
                            text = if (dspConfig.deverbManualDelayMs <= 0.1f) "AUTO" else "${dspConfig.deverbManualDelayMs.toInt()} ms",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = DeverbPurple,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = dspConfig.deverbManualDelayMs,
                        onValueChange = onManualDelayChange,
                        valueRange = 0.0f..100.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = DeverbPurple,
                            activeTrackColor = DeverbPurple,
                            inactiveTrackColor = Color(0xFF243246)
                        ),
                        modifier = Modifier.testTag("deverb_delay_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Apply and Save Action Button
                    Button(
                        onClick = onApplyAndSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("apply_deverb_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeverbCyan,
                            contentColor = Color(0xFF09121E)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Save",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vysušit ozvěnu a uložit kopii (_Deverbed.wav)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
