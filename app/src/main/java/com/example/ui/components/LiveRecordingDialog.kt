package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.audio.RecordingStatus
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicRed
import com.example.ui.theme.ForensicSurface
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary

@Composable
fun LiveRecordingDialog(
    status: RecordingStatus,
    onStartRecording: (String) -> Unit,
    onStopRecording: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
    }

    var recordTitle by remember { mutableStateOf("Forensic_Capture") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ForensicSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ForensicCyan.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().testTag("live_recording_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Close row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Forensic Audio Recorder",
                        style = MaterialTheme.typography.titleMedium,
                        color = ForensicTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ForensicTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Format specs chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141A24))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "44.1 kHz • 16-bit Mono • Lossless PCM (WAV)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ForensicCyan
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Glowing Record Sphere
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(if (status.isRecording) pulseScale else 1.0f)
                        .clip(CircleShape)
                        .background(
                            if (status.isRecording) ForensicRed.copy(alpha = 0.2f) else ForensicCyan.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (status.isRecording) ForensicRed else ForensicCyan
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (status.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = if (status.isRecording) Color.White else Color(0xFF00363D),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Timer & dB Readout
                if (status.isRecording) {
                    val totalSec = status.durationMs / 1000
                    val min = totalSec / 60
                    val sec = totalSec % 60
                    val millis = (status.durationMs % 1000) / 100

                    Text(
                        text = String.format("%02d:%02d.%01d", min, sec, millis),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForensicRed
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "VU Level: ${String.format("%.1f", status.currentDb)} dBFS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (status.currentDb > -12f) ForensicOrange else ForensicNeonGreen
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live VU Level Bar
                    LinearProgressIndicator(
                        progress = { ((status.currentDb + 60f) / 60f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (status.currentDb > -12f) ForensicOrange else ForensicCyan,
                        trackColor = Color(0xFF222838)
                    )
                } else {
                    OutlinedTextField(
                        value = recordTitle,
                        onValueChange = { recordTitle = it },
                        label = { Text("Recording Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForensicCyan,
                            unfocusedBorderColor = Color(0xFF2E3446),
                            focusedTextColor = ForensicTextPrimary,
                            unfocusedTextColor = ForensicTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("record_title_input")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!status.isRecording) {
                        Button(
                            onClick = {
                                if (hasRecordPermission) {
                                    onStartRecording(recordTitle.ifBlank { "Forensic_Recording" })
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_live_record_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForensicRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Forensic Recording", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onStopRecording,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("stop_live_record_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForensicCyan,
                                contentColor = Color(0xFF00363D)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop & Save to Sandbox", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
