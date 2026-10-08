package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.VoiceSeparationMode
import com.example.dsp.VoiceSeparationStats
import com.example.model.DspConfig
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary
import java.util.Locale

@Composable
fun VoiceSeparationCard(
    dspConfig: DspConfig,
    stats: VoiceSeparationStats?,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectMode: (VoiceSeparationMode) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onFormantBoostChange: (Float) -> Unit,
    onSeparateAndSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ForensicSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (dspConfig.voiceSeparationEnabled) ForensicNeonGreen.copy(alpha = 0.65f) else Color(0xFF283040)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_separation_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
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
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = "Separace hlasu",
                        tint = ForensicNeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "STFT Separace hlasu (Voice Isolation)",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForensicNeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Wienerovo spektrální maskování a izolace formantů F0–F3",
                            fontSize = 11.sp,
                            color = ForensicTextSecondary
                        )
                    }
                }

                Switch(
                    checked = dspConfig.voiceSeparationEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00391C),
                        checkedTrackColor = ForensicNeonGreen,
                        uncheckedTrackColor = Color(0xFF222634)
                    ),
                    modifier = Modifier.testTag("voice_separation_switch")
                )
            }

            // Voice vs Background Spectral Ratio Meter
            if (stats != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val vocalPct = (stats.vocalEnergyRatio * 100).toInt()
                val noisePct = (stats.backgroundNoiseRatio * 100).toInt()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF10141E))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🗣️ Energie řeči: $vocalPct%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForensicNeonGreen
                        )
                        Text(
                            text = "Dominantní formant: ${stats.dominantFormantHz.toInt()} Hz",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ForensicCyan
                        )
                        Text(
                            text = "Šum: $noisePct%",
                            fontSize = 11.sp,
                            color = ForensicOrange
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Dual Split Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF28201E))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(stats.vocalEnergyRatio.coerceIn(0.08f, 0.92f))
                                .background(ForensicNeonGreen)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .background(ForensicOrange.copy(alpha = 0.65f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Selection List
            Text(
                text = "Režim separace hlasu:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ForensicTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VoiceSeparationMode.entries.forEach { mode ->
                    val isSelected = dspConfig.voiceSeparationMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF102A24) else Color(0xFF131722))
                            .border(
                                1.dp,
                                if (isSelected) ForensicNeonGreen else Color(0xFF232838),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.titleCz,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) ForensicNeonGreen else ForensicTextPrimary
                            )
                            Text(
                                text = mode.subtitleCz,
                                fontSize = 10.sp,
                                color = ForensicTextSecondary
                            )
                        }
                        if (isSelected) {
                            Text(
                                text = "AKTIVNÍ",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ForensicNeonGreen,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Strength Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Síla separace a potlačení okolí",
                    fontSize = 12.sp,
                    color = ForensicTextSecondary
                )
                Text(
                    text = "${(dspConfig.voiceSeparationStrength * 100).toInt()}%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForensicNeonGreen
                )
            }
            Slider(
                value = dspConfig.voiceSeparationStrength,
                onValueChange = onStrengthChange,
                valueRange = 0.15f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = ForensicNeonGreen,
                    activeTrackColor = ForensicNeonGreen,
                    inactiveTrackColor = Color(0xFF1A2E26)
                ),
                modifier = Modifier.testTag("voice_separation_strength_slider")
            )

            // Formant Boost Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Zesílení vokálních formantů (F1–F3)",
                    fontSize = 12.sp,
                    color = ForensicTextSecondary
                )
                Text(
                    text = "+${String.format(Locale.US, "%.1f", dspConfig.vocalFormantBoostDb)} dB",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForensicCyan
                )
            }
            Slider(
                value = dspConfig.vocalFormantBoostDb,
                onValueChange = onFormantBoostChange,
                valueRange = 0f..18f,
                colors = SliderDefaults.colors(
                    thumbColor = ForensicCyan,
                    activeTrackColor = ForensicCyan,
                    inactiveTrackColor = Color(0xFF1A2836)
                ),
                modifier = Modifier.testTag("vocal_formant_boost_slider")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Execute Voice Separation Action Button
            Button(
                onClick = onSeparateAndSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("separate_voice_now_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForensicNeonGreen,
                    contentColor = Color(0xFF002915)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Separovat hlas a zobrazit na spektrogramu",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
