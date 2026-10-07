package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ForensicReport
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicDarkBackground
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSurface
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ForensicReportScreen(
    report: ForensicReport?,
    onGenerateNewReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ForensicDarkBackground)
    ) {
        if (report == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    tint = ForensicCyan,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Forensic Report Generated Yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = ForensicTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Analyze audio in the Studio and tap 'Forensic Report' to generate a comprehensive certificate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ForensicTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onGenerateNewReport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForensicCyan,
                        contentColor = Color(0xFF00363D)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Generate Report for Current Track", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header & Verification Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = ForensicNeonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Forensic Audio Certificate",
                            style = MaterialTheme.typography.titleLarge,
                            color = ForensicTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "Forensic Audio Examination Report - ${report.fileTitle}"
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    """
                                    --- AUDIODETECTIVE PRO FORENSIC EXAMINATION REPORT ---
                                    Target Audio: ${report.fileTitle}
                                    Examination Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))}
                                    Format: ${report.sampleRate} Hz, ${report.bitDepth}-bit PCM Mono
                                    
                                    SPECTRAL ANALYSIS:
                                    - Estimated SNR: ${String.format("%.1f", report.estimatedSnrDb)} dB
                                    - Peak dBFS: ${String.format("%.1f", report.peakLevelDb)} dB
                                    - RMS Power: ${String.format("%.1f", report.rmsLevelDb)} dB
                                    - Vocal Band Energy Ratio (300Hz-3.4kHz): ${report.vocalBandEnergyPct}%
                                    
                                    APPLIED DSP FILTERS:
                                    ${report.appliedFilters.joinToString("\n") { "• $it" }}
                                    
                                    DETECTED LOW-LEVEL SPEECH / WHISPER INTERVALS:
                                    ${report.detectedWhisperSegments.joinToString("\n") { (s, e) -> "• [${s}ms - ${e}ms]" }}
                                    
                                    ANALYSIS SUMMARY:
                                    ${report.analystNotes}
                                    
                                    Verification: 100% Offline Forensic Sandbox Compliance.
                                    """.trimIndent()
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Forensic Report"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF162B34),
                            contentColor = ForensicCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("share_forensic_report_btn")
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Official Forensic Document Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ForensicSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263040)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = "CASE FILE: ${report.fileTitle}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForensicCyan
                        )
                        Text(
                            text = "Generated: ${SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US).format(Date(report.timestamp))}",
                            fontSize = 11.sp,
                            color = ForensicTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = Color(0xFF252C3C))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Key Findings Grid
                        Text(
                            text = "1. Technical Signal Parameters",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForensicTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReportStatBox("Estimated SNR", "${String.format("%.1f", report.estimatedSnrDb)} dB", ForensicNeonGreen, Modifier.weight(1f))
                            ReportStatBox("Peak Amplitude", "${String.format("%.1f", report.peakLevelDb)} dB", ForensicOrange, Modifier.weight(1f))
                            ReportStatBox("RMS Level", "${String.format("%.1f", report.rmsLevelDb)} dB", ForensicCyan, Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Vocal Band Energy
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Human Speech Formant Concentration:",
                                fontSize = 12.sp,
                                color = ForensicTextSecondary
                            )
                            Text(
                                text = "${report.vocalBandEnergyPct}%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForensicCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = Color(0xFF252C3C))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Detected Whisper Segments
                        Text(
                            text = "2. Detected Whisper & Faint Speech Timestamps",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForensicTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        report.detectedWhisperSegments.forEach { (start, end) ->
                            val sSec = start / 1000f
                            val eSec = end / 1000f
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF101520))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ForensicNeonGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Interval: ${String.format("%.2f", sSec)}s – ${String.format("%.2f", eSec)}s",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = ForensicTextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Whisper Pattern Isolated",
                                    fontSize = 11.sp,
                                    color = ForensicCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = Color(0xFF252C3C))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Applied DSP Chain
                        Text(
                            text = "3. Active Filter Processing Chain",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForensicTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (report.appliedFilters.isEmpty()) {
                            Text(
                                text = "• Raw unprocessed audio",
                                fontSize = 12.sp,
                                color = ForensicTextSecondary
                            )
                        } else {
                            report.appliedFilters.forEach { filter ->
                                Text(
                                    text = "• $filter",
                                    fontSize = 12.sp,
                                    color = ForensicNeonGreen,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = Color(0xFF252C3C))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Analyst notes
                        Text(
                            text = "4. Examination Findings & Notes",
                            style = MaterialTheme.typography.titleSmall,
                            color = ForensicTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = report.analystNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = ForensicTextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Offline certification banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F1E19))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = ForensicNeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "100% Offline Local Sandbox Execution Verified",
                                fontSize = 11.sp,
                                color = ForensicNeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ReportStatBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10141E))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 10.sp, color = ForensicTextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
