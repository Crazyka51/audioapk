package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.model.DspConfig
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary

@Composable
fun AbLoopController(
    config: DspConfig,
    onToggleLoop: (Boolean) -> Unit,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onNudgeA: (Long) -> Unit,
    onNudgeB: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val loopDurationMs = (config.loopEndMs - config.loopStartMs).coerceAtLeast(0L)
    val loopSeconds = loopDurationMs / 1000f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ForensicSurfaceVariant)
            .border(1.dp, if (config.abLoopEnabled) ForensicCyan.copy(alpha = 0.5f) else Color(0xFF2B303E), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = "A-B Forensic Loop",
                    tint = if (config.abLoopEnabled) ForensicCyan else ForensicTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "A-B Forensic Loop Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (config.abLoopEnabled) ForensicCyan else ForensicTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (config.abLoopEnabled) {
                    Text(
                        text = "Δ ${String.format("%.3f", loopSeconds)}s",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = ForensicNeonGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Switch(
                    checked = config.abLoopEnabled,
                    onCheckedChange = onToggleLoop,
                    modifier = Modifier.testTag("toggle_ab_loop_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00363D),
                        checkedTrackColor = ForensicCyan,
                        uncheckedTrackColor = Color(0xFF222632)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Set A and Set B buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Point A Controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141720))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Point A (Start)",
                        style = MaterialTheme.typography.labelMedium,
                        color = ForensicOrange,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatMs(config.loopStartMs),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = ForensicTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                FilledTonalButton(
                    onClick = onSetA,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("set_loop_marker_a_btn"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF2E2218),
                        contentColor = ForensicOrange
                    )
                ) {
                    Text("Set A Here", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Nudge A buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNudgeA(-10L) },
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text("-10ms", fontSize = 9.sp)
                    }
                    OutlinedButton(
                        onClick = { onNudgeA(10L) },
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text("+10ms", fontSize = 9.sp)
                    }
                }
            }

            // Point B Controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141720))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Point B (End)",
                        style = MaterialTheme.typography.labelMedium,
                        color = ForensicCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatMs(config.loopEndMs),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = ForensicTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                FilledTonalButton(
                    onClick = onSetB,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("set_loop_marker_b_btn"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF143038),
                        contentColor = ForensicCyan
                    )
                ) {
                    Text("Set B Here", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Nudge B buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNudgeB(-10L) },
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text("-10ms", fontSize = 9.sp)
                    }
                    OutlinedButton(
                        onClick = { onNudgeB(10L) },
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text("+10ms", fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val millis = ms % 1000
    return String.format("%02d:%02d.%03d", min, sec, millis)
}
