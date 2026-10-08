package com.example.desktop.ui

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioFileItem
import com.example.model.AudioStatus
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicCardBorder
import com.example.ui.theme.ForensicDarkBackground
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicRed
import com.example.ui.theme.ForensicSurface
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary

/** Status pill shown on recording cards, mirroring the Android library badge. */
@Composable
fun StatusChip(status: AudioStatus, selected: Boolean = false) {
    val (label, color) = when (status) {
        AudioStatus.ORIGINAL -> "Original" to ForensicTextSecondary
        AudioStatus.CLEANED -> "Cleaned" to ForensicCyan
        AudioStatus.VOICE_SEPARATED -> "Voice Isolated" to ForensicNeonGreen
        AudioStatus.HPF_ACTIVE -> "HPF Active" to ForensicOrange
        AudioStatus.WHISPER_BOOSTED -> "Boosted" to ForensicNeonGreen
        AudioStatus.PROCESSED -> "Processed" to ForensicCyan
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (selected) 0.22f else 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** Small read-only metric tile used by the library inspector and the report tab. */
@Composable
fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ForensicSurface)
            .padding(10.dp)
    ) {
        Text(label, fontSize = 10.sp, color = ForensicTextMuted)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 13.sp, color = ForensicTextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

/** Section header with an optional trailing action, used above lists. */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ForensicTextPrimary, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ForensicTextMuted)
            }
        }
        action?.invoke()
    }
}

/** Primary action button styled like the Android cyan call to action. */
@Composable
fun CyanButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) ForensicCyan else ForensicSurfaceVariant)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color(0xFF00252B) else ForensicTextMuted
        )
    }
}

/** Secondary (outlined) action button. */
@Composable
fun GhostButton(text: String, enabled: Boolean = true, tint: Color = ForensicCyan, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, (if (enabled) tint else ForensicTextMuted).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 12.sp, color = if (enabled) tint else ForensicTextMuted, fontWeight = FontWeight.Medium)
    }
}

/** One quick action tile of the expanded recording card (Trim / Noise / Boost / Export). */
@Composable
private fun ActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        Modifier.clickable { onClick() }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = ForensicCyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = ForensicTextSecondary)
    }
}

/**
 * Recording card. Collapsed it shows a play button, duration and status; when it is the selected
 * recording it expands into the full player with quick actions, exactly like the Android card.
 */
@Composable
fun AudioCard(
    item: AudioFileItem,
    isSelected: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    onSelect: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onDelete: () -> Unit,
    onOpenStudio: () -> Unit,
    onNoiseReduction: () -> Unit,
    onWhisperBoost: () -> Unit,
    onExport: () -> Unit,
    waveform: @Composable () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ForensicSurfaceVariant else ForensicSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.dp else 0.dp,
            if (isSelected) ForensicCyan.copy(alpha = 0.55f) else ForensicCardBorder
        ),
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ForensicCyan.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = ForensicCyan, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ForensicTextPrimary
                    )
                    Text(
                        "Duration: ${item.durationFormatted}   Size: ${item.fileSizeFormatted}",
                        fontSize = 11.sp,
                        color = ForensicTextMuted
                    )
                }
                StatusChip(item.status, isSelected)
                Spacer(Modifier.width(6.dp))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = ForensicTextMuted)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(Color(0xFF1E2330))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open in Forensic Studio", color = ForensicCyan) },
                            onClick = {
                                menuOpen = false
                                onSelect()
                                onOpenStudio()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export working copy", color = ForensicTextPrimary) },
                            onClick = {
                                menuOpen = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete recording", color = ForensicRed) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (isSelected) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(ForensicCyan)
                            .clickable { onPlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF00252B),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${formatMs(positionMs)}  /  ${formatMs(item.durationMs)}",
                        fontSize = 14.sp,
                        color = ForensicCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(8.dp))
                waveform()

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionTile("Trim", Icons.Default.Tune) { onOpenStudio() }
                    ActionTile("Noise Reduction", Icons.Default.GraphicEq) { onNoiseReduction() }
                    ActionTile("Voice Boost", Icons.Default.Mic) { onWhisperBoost() }
                    ActionTile("Export", Icons.Default.Share) { onExport() }
                }
            }
        }
    }
}

/** Compact one line list row used in the library list. */
@Composable
fun LibraryRow(
    item: AudioFileItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) ForensicSurfaceVariant else ForensicSurface)
            .border(
                1.dp,
                if (isSelected) ForensicCyan.copy(alpha = 0.5f) else ForensicCardBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 14.sp,
                color = if (isSelected) ForensicCyan else ForensicTextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${item.durationFormatted} - ${item.fileSizeFormatted} - ${item.sampleRate} Hz / ${item.bitDepth}-bit",
                fontSize = 11.sp,
                color = ForensicTextMuted
            )
        }
        StatusChip(item.status, isSelected)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ForensicRed, modifier = Modifier.size(18.dp))
        }
    }
}

/** Modal step shown while a recording is running, mirroring the Android live recording dialog. */
@Composable
fun RecordingDialog(
    durationMs: Long,
    amplitude: Float,
    decibels: Float,
    onStop: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ForensicSurface)
            .border(1.dp, ForensicRed.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(ForensicRed)
                )
                Spacer(Modifier.width(8.dp))
                Text("RECORDING", color = ForensicRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(12.dp))
                Text(formatMs(durationMs), color = ForensicTextPrimary, fontSize = 15.sp)
            }
            Spacer(Modifier.height(14.dp))

            // VU meter: one bar per 5 % of level
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(20) { index ->
                    val threshold = (index + 1) / 20f
                    val active = amplitude >= threshold
                    Box(
                        Modifier
                            .height(26.dp)
                            .width(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (active) {
                                    when {
                                        threshold > 0.9f -> ForensicRed
                                        threshold > 0.7f -> ForensicOrange
                                        else -> ForensicNeonGreen
                                    }
                                } else {
                                    ForensicSurfaceVariant
                                }
                            )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(formatDb(decibels), color = ForensicCyan, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CyanButton("Stop and save", onClick = onStop)
            }
        }
    }
}

/** Top application bar with the app name and the global actions. */
@Composable
fun AppTopBar(
    libraryPath: String,
    busy: Boolean,
    onImport: () -> Unit,
    onOpenFolder: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ForensicDarkBackground)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "AudioDetective Pro",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ForensicTextPrimary
            )
            Text(
                "Forensic audio analysis - 100% offline on this machine",
                fontSize = 11.sp,
                color = ForensicTextMuted
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(libraryPath, fontSize = 11.sp, color = ForensicTextMuted)
            GhostButton("Refresh", enabled = !busy, onClick = onRefresh)
            GhostButton("Open folder", onClick = onOpenFolder)
            CyanButton("Import audio", enabled = !busy, onClick = onImport)
        }
    }
}
