package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioFileItem
import com.example.model.AudioStatus
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicCyanGlow
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary

@Composable
fun AudioCardItem(
    item: AudioFileItem,
    isSelected: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    samples: FloatArray?,
    onSelect: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onQuickTrim: () -> Unit,
    onQuickNoiseReduction: () -> Unit,
    onQuickVoiceBoost: () -> Unit,
    onQuickExport: () -> Unit,
    onDelete: () -> Unit,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableStateOf(1.0f) }

    val borderColor = if (isSelected) ForensicCyan.copy(alpha = 0.6f) else Color(0xFF232836)
    val cardBg = if (isSelected) Color(0xFF161A24) else ForensicSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .padding(14.dp)
            .testTag("audio_card_${item.id}")
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Icon Avatar
                val (icon, iconBg, iconTint) = when {
                    item.fileName.endsWith(".mp3", ignoreCase = true) -> Triple(
                        Icons.Default.Headphones, Color(0xFF1E2838), ForensicCyan
                    )
                    item.fileName.endsWith(".m4a", ignoreCase = true) -> Triple(
                        Icons.Default.Movie, Color(0xFF1A3340), ForensicCyan
                    )
                    else -> Triple(
                        Icons.Default.Mic, Color(0xFF173844), ForensicCyan
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = ForensicTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Duration: ${item.durationFormatted}   Size: ${item.fileSizeFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ForensicTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Status Badge and Menu
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(status = item.status)
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = ForensicTextMuted
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E2330))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open in Forensic Studio", color = ForensicCyan) },
                            onClick = {
                                menuExpanded = false
                                onSelect()
                                onOpenStudio()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export / Share Cleaned WAV", color = ForensicTextPrimary) },
                            onClick = {
                                menuExpanded = false
                                onQuickExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Recording", color = Color(0xFFFF5252)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }

        // Expanded Interactive Waveform Player Section
        if (isSelected) {
            Spacer(modifier = Modifier.height(12.dp))

            // Timecode and Zoom controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Play / Pause Icon Button + Timecode
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ForensicCyan)
                            .clickable { onPlayPause() }
                            .testTag("card_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF00363D),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val posFormatted = formatMsToTimecode(currentPositionMs)
                    Text(
                        text = "$posFormatted / ${item.durationFormatted}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForensicCyan
                    )
                }

                // Zoom controls
                Row {
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel * 1.25f).coerceAtMost(3.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom in",
                            tint = ForensicTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel / 1.25f).coerceAtLeast(1.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Zoom out",
                            tint = ForensicTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Waveform Canvas View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0C111A))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                WaveformView(
                    samples = samples,
                    currentPositionMs = currentPositionMs,
                    durationMs = item.durationMs,
                    onSeek = onSeek,
                    zoomLevel = zoomLevel,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Actions Bar (Trim, Noise Reduction, Voice Boost, Export)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                QuickActionButton(
                    icon = Icons.Default.ContentCut,
                    label = "Trim",
                    onClick = onQuickTrim
                )
                QuickActionButton(
                    icon = Icons.Default.GraphicEq,
                    label = "Noise Reduction",
                    onClick = onQuickNoiseReduction
                )
                QuickActionButton(
                    icon = Icons.Default.Mic,
                    label = "Voice Boost",
                    onClick = onQuickVoiceBoost
                )
                QuickActionButton(
                    icon = Icons.Default.IosShare,
                    label = "Export",
                    onClick = onQuickExport
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: AudioStatus) {
    val (bg, text, textColor) = when (status) {
        AudioStatus.CLEANED -> Triple(Color(0xFF0A333E), "Cleaned", ForensicCyan)
        AudioStatus.VOICE_SEPARATED -> Triple(Color(0xFF0B3829), "Voice Separated", ForensicNeonGreen)
        AudioStatus.WHISPER_BOOSTED -> Triple(Color(0xFF0D3B20), "Boosted", ForensicNeonGreen)
        AudioStatus.HPF_ACTIVE -> Triple(Color(0xFF38230D), "HPF 300Hz", ForensicOrange)
        AudioStatus.PROCESSED -> Triple(Color(0xFF261D38), "Filtered", Color(0xFFC792EA))
        AudioStatus.ORIGINAL -> Triple(Color(0xFF1E2430), "Original", ForensicTextSecondary)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = ForensicCyan,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = ForensicTextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatMsToTimecode(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
