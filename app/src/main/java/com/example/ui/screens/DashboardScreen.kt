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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioFileItem
import com.example.ui.components.AudioCardItem
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
fun DashboardScreen(
    viewModel: AudioDetectiveViewModel,
    audioFiles: List<AudioFileItem>,
    selectedAudio: AudioFileItem?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    pcmSamples: FloatArray?,
    onOpenStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEffects: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importAudioFile(uri, null)
        }
    }

    val filteredFiles = remember(audioFiles, searchQuery) {
        if (searchQuery.isBlank()) audioFiles
        else audioFiles.filter { it.fileName.contains(searchQuery, ignoreCase = true) || it.title.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ForensicDarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = ForensicCyan,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AudioDetective Pro",
                            style = MaterialTheme.typography.titleLarge,
                            color = ForensicTextPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { searchVisible = !searchVisible },
                            modifier = Modifier.testTag("dashboard_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (searchVisible) ForensicCyan else ForensicTextSecondary
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("dashboard_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = ForensicTextSecondary
                            )
                        }
                    }
                }
            }

            // Search input field
            if (searchVisible) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search recordings...", color = ForensicTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForensicCyan,
                            unfocusedBorderColor = Color(0xFF2E3446),
                            focusedTextColor = ForensicTextPrimary,
                            unfocusedTextColor = ForensicTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_search_input")
                    )
                }
            }

            // Voice Cleaning Dashboard Hero Banner (Matching Screenshot)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ForensicSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282F3F)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStudio() }
                        .testTag("voice_cleaning_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F2C38)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = ForensicCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Voice Cleaning Dashboard",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ForensicTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "High-Pass 300Hz • Whisper Expander • 60 FPS FFT",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ForensicCyan,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF14323D))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "STUDIO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForensicCyan
                            )
                        }
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audio Recordings",
                            style = MaterialTheme.typography.titleMedium,
                            color = ForensicTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${filteredFiles.size} items • WAV, MP3, M4A, AAC",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForensicTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF14303A))
                            .border(1.dp, ForensicCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable { importLauncher.launch("audio/*") }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("dashboard_import_audio_btn"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Import Audio",
                            tint = ForensicCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import File",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForensicCyan
                        )
                    }
                }
            }

            // List of Recordings
            if (filteredFiles.isEmpty()) {
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
                            text = "No recordings found.\nTap the REC button below to record live audio.",
                            color = ForensicTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(filteredFiles, key = { it.id }) { item ->
                    val isSelected = selectedAudio?.id == item.id
                    AudioCardItem(
                        item = item,
                        isSelected = isSelected,
                        isPlaying = if (isSelected) isPlaying else false,
                        currentPositionMs = if (isSelected) currentPositionMs else 0L,
                        samples = if (isSelected) pcmSamples else null,
                        onSelect = { viewModel.selectAudioFile(item) },
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.seekTo(it) },
                        onQuickTrim = {
                            viewModel.selectAudioFile(item)
                            viewModel.toggleAbLoop(true)
                            onOpenStudio()
                        },
                        onQuickNoiseReduction = {
                            viewModel.selectAudioFile(item)
                            viewModel.toggleNoiseReduction(true)
                            viewModel.processAndSaveCopy("NoiseReduced")
                        },
                        onQuickVoiceBoost = {
                            viewModel.selectAudioFile(item)
                            viewModel.toggleWhisperBoost(true)
                            viewModel.processAndSaveCopy("WhisperBoost")
                        },
                        onQuickExport = {
                            viewModel.selectAudioFile(item)
                            viewModel.processAndSaveCopy("Exported")
                        },
                        onDelete = { viewModel.deleteAudioFile(item) },
                        onOpenStudio = onOpenStudio
                    )
                }
            }

            // Bottom space for floating button
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // Floating glowing REC Button (matching screenshot badge)
        FloatingActionButton(
            onClick = { viewModel.openRecordingDialog() },
            containerColor = Color(0xFF263238),
            contentColor = ForensicRed,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp)
                .border(2.dp, ForensicRed.copy(alpha = 0.5f), CircleShape)
                .testTag("floating_rec_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "REC",
                    tint = ForensicRed,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "REC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForensicRed
                )
            }
        }
    }
}
