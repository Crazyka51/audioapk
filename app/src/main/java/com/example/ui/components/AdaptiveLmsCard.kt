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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
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
import com.example.dsp.LmsFilterMetrics
import com.example.model.DspConfig

private val LmsCardBg = Color(0xFF141D17)
private val LmsBorder = Color(0xFF1F3525)
private val LmsNeonGreen = Color(0xFF00FF9D)
private val LmsAmber = Color(0xFFFFB300)
private val ForensicTextPrimary = Color(0xFFF0F4F8)
private val ForensicTextSecondary = Color(0xFF90A4AE)

/**
 * Adaptive LMS Filter (Least Mean Squares) UI Card.
 * Tracks and cancels non-stationary dynamic noise (traffic, passing cars, barking dogs) in real time.
 */
@Composable
fun AdaptiveLmsCard(
    dspConfig: DspConfig,
    metrics: LmsFilterMetrics?,
    onToggleEnabled: (Boolean) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onSpeechPreserveChange: (Float) -> Unit,
    onApplyAndSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (dspConfig.adaptiveLmsEnabled) LmsNeonGreen.copy(alpha = 0.6f) else LmsBorder, RoundedCornerShape(12.dp))
            .testTag("adaptive_lms_card"),
        colors = CardDefaults.cardColors(containerColor = LmsCardBg),
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
                            .background(LmsNeonGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "LMS Car/Traffic Icon",
                            tint = LmsNeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Adaptivní LMS filtr (Dynamický šum)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ForensicTextPrimary
                            )
                        }
                        Text(
                            text = "Least Mean Squares filtrace proměnlivého hluku aut a psů bez poškození řeči",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = ForensicTextSecondary
                        )
                    }
                }

                Switch(
                    checked = dspConfig.adaptiveLmsEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF0D1C13),
                        checkedTrackColor = LmsNeonGreen,
                        uncheckedTrackColor = Color(0xFF233529)
                    ),
                    modifier = Modifier.testTag("lms_switch")
                )
            }

            AnimatedVisibility(visible = dspConfig.adaptiveLmsEnabled) {
                Column(modifier = Modifier.padding(top = 12.dp)) {

                    // Live Diagnostics HUD Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0C140F))
                            .border(1.dp, Color(0xFF1B2E21), RoundedCornerShape(8.dp))
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
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Speed",
                                        tint = LmsNeonGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Adaptivní potlačení nestacionárního hluku:",
                                        fontSize = 11.sp,
                                        color = ForensicTextSecondary
                                    )
                                }
                                Text(
                                    text = if (metrics != null) "-${String.format("%.1f", metrics.noiseAttenuationDb)} dB" else "48 Taps FIR",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LmsNeonGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ALE architektura: Automatická predikce a odečtení korelovaného hluku pozadí v čase.",
                                fontSize = 11.sp,
                                color = ForensicTextPrimary
                            )

                            if (metrics != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Poměr konvergence (Signal-to-Interference zisk):",
                                        fontSize = 10.sp,
                                        color = ForensicTextSecondary
                                    )
                                    Text(
                                        text = "${String.format("%.1f", metrics.convergenceRatio)}x",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = LmsAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                LinearProgressIndicator(
                                    progress = { ((metrics.convergenceRatio - 1.0f) / 4.0f).coerceIn(0.1f, 1.0f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = LmsNeonGreen,
                                    trackColor = Color(0xFF1E3024),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Slider: Intenzita LMS filtrace (Adaptation Intensity)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Waves,
                                contentDescription = "Intensity",
                                tint = LmsNeonGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Intenzita sledování a potlačení (Adaptation rate)",
                                fontSize = 12.sp,
                                color = ForensicTextSecondary
                            )
                        }
                        Text(
                            text = "${(dspConfig.adaptiveLmsIntensity * 100).toInt()}%",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = LmsNeonGreen
                        )
                    }

                    Slider(
                        value = dspConfig.adaptiveLmsIntensity,
                        onValueChange = onIntensityChange,
                        valueRange = 0.10f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = LmsNeonGreen,
                            activeTrackColor = LmsNeonGreen,
                            inactiveTrackColor = Color(0xFF223829)
                        ),
                        modifier = Modifier.testTag("lms_intensity_slider")
                    )

                    // Slider: Ochrana řeči (Speech Activity & Formant Protection)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Voice",
                                tint = LmsAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ochrana formantů řeči (Zmrazení váh při slovech)",
                                fontSize = 12.sp,
                                color = ForensicTextSecondary
                            )
                        }
                        Text(
                            text = "${(dspConfig.adaptiveLmsSpeechPreserve * 100).toInt()}%",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = LmsAmber
                        )
                    }

                    Slider(
                        value = dspConfig.adaptiveLmsSpeechPreserve,
                        onValueChange = onSpeechPreserveChange,
                        valueRange = 0.20f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = LmsAmber,
                            activeTrackColor = LmsAmber,
                            inactiveTrackColor = Color(0xFF223829)
                        ),
                        modifier = Modifier.testTag("lms_preserve_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Apply and Save Action Button
                    Button(
                        onClick = onApplyAndSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("apply_lms_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LmsNeonGreen,
                            contentColor = Color(0xFF09140C)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Save LMS",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Odfiltrovat proměnlivý hluk a uložit kopii (_LmsCleaned.wav)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
