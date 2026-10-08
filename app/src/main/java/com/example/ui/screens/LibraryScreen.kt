package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.AudioFileItem
import com.example.ui.components.StatusBadge
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicDarkBackground
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicRed
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary
import com.example.viewmodel.AudioDetectiveViewModel

@Composable
fun LibraryScreen(
    viewModel: AudioDetectiveViewModel,
    audioFiles: List<AudioFileItem>,
    selectedAudio: AudioFileItem?,
    onOpenStudio: () -> Unit,
    onGenerateReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importAudioFile(uri, null)
        }
    }

    var pendingDelete by remember { mutableStateOf<AudioFileItem?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ForensicDarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = ForensicCyan,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Audio Case Library",
                            style = MaterialTheme.typography.titleLarge,
                            color = ForensicTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { importLauncher.launch("audio/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF14303A),
                            contentColor = ForensicCyan
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("import_audio_btn")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Audio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Audio Inspector Card if file is selected
            if (selectedAudio != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ForensicSurfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ForensicCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Assessment,
                                        contentDescription = null,
                                        tint = ForensicCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Forensic Metadata Inspector",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ForensicCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                StatusBadge(status = selectedAudio.status)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = selectedAudio.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                color = ForensicTextPrimary,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Grid of forensic audio metrics
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricBox(label = "Sample Rate", value = "${selectedAudio.sampleRate} Hz", modifier = Modifier.weight(1f))
                                MetricBox(label = "Bit Depth", value = "${selectedAudio.bitDepth}-bit PCM", modifier = Modifier.weight(1f))
                                MetricBox(label = "Channels", value = if (selectedAudio.channels == 1) "Mono" else "Stereo", modifier = Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricBox(label = "Peak dBFS", value = "${selectedAudio.peakDb} dB", modifier = Modifier.weight(1f))
                                MetricBox(label = "RMS Level", value = "${selectedAudio.rmsDb} dB", modifier = Modifier.weight(1f))
                                MetricBox(label = "Noise Floor", value = "${selectedAudio.noiseFloorDb} dB", modifier = Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onOpenStudio,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ForensicCyan,
                                        contentColor = Color(0xFF00363D)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open in Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = onGenerateReport,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF222C3D),
                                        contentColor = ForensicNeonGreen
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Full Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Section: All Audio Files
            item {
                Text(
                    text = "Storage Files (${audioFiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = ForensicTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (audioFiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ForensicSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Knihovna je prázdná.\nImportuj vlastní audio tlačítkem Import Audio.",
                            color = ForensicTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            items(audioFiles, key = { it.id }) { item ->
                val isSelected = selectedAudio?.id == item.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF1B2332) else ForensicSurfaceVariant)
                        .border(1.dp, if (isSelected) ForensicCyan.copy(alpha = 0.5f) else Color(0xFF252A38), RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectAudioFile(item) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) ForensicCyan else Color(0xFF182230)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF00363D) else ForensicCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = item.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ForensicTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${item.durationFormatted} • ${item.fileSizeFormatted}",
                                fontSize = 11.sp,
                                color = ForensicTextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(status = item.status)
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { pendingDelete = item },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = ForensicTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        val deleteTarget = pendingDelete
        if (deleteTarget != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Smazat nahrávku?", color = ForensicTextPrimary) },
                text = {
                    Text(
                        "Nahrávka \"${deleteTarget.fileName}\" bude nevratně smazána ze zařízení.",
                        color = ForensicTextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteAudioFile(deleteTarget)
                        pendingDelete = null
                    }) {
                        Text("Smazat", color = ForensicRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Zrušit", color = ForensicTextSecondary)
                    }
                },
                containerColor = ForensicSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10141E))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = ForensicTextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = ForensicTextPrimary
        )
    }
}
