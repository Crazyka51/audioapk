package com.example.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.dsp.VoiceSeparationMode
import com.example.model.AppTab
import com.example.model.AudioFileItem
import com.example.ui.theme.AudioDetectiveTheme
import com.example.ui.theme.ForensicCardBorder
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicDarkBackground
import com.example.ui.theme.ForensicNeonGreen
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicRed
import com.example.ui.theme.ForensicSurface
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary
import com.example.ui.theme.ForensicTextSecondary
import com.example.desktop.ui.AppTopBar
import com.example.desktop.ui.AudioCard
import com.example.desktop.ui.CyanButton
import com.example.desktop.ui.DspRack
import com.example.desktop.ui.GhostButton
import com.example.desktop.ui.LibraryRow
import com.example.desktop.ui.MetricBox
import com.example.desktop.ui.RecordingDialog
import com.example.desktop.ui.SectionHeader
import com.example.desktop.ui.SpectrogramPanel
import com.example.desktop.ui.StatusChip
import com.example.desktop.ui.WaveformPanel
import com.example.desktop.ui.formatDb
import com.example.desktop.ui.formatMs
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AudioDetective Pro - Forensic Audio Analysis",
        state = androidx.compose.ui.window.rememberWindowState(width = 1440.dp, height = 940.dp)
    ) {
        AudioDetectiveTheme {
            Surface(color = ForensicDarkBackground, modifier = Modifier.fillMaxSize()) {
                val viewModel = remember { DesktopViewModel() }
                DisposableEffect(Unit) {
                    onDispose { viewModel.dispose() }
                }
                AppScaffold(viewModel)
            }
        }
    }
}

/**
 * Application shell mirroring the Android layout: a top bar, the active tab content and a bottom
 * navigation bar with the same five destinations. The visual language (dark surface, cyan accents,
 * rounded cards) is shared with the Android app through com.example.ui.theme.
 */
@Composable
private fun AppScaffold(viewModel: DesktopViewModel) {
    val tab by viewModel.tab.collectAsState()
    val files by viewModel.files.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val config by viewModel.config.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val recording by viewModel.recordingStatus.collectAsState()
    val samples by viewModel.samples.collectAsState()

    var pendingDelete by remember { mutableStateOf<AudioFileItem?>(null) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(6000)
            viewModel.clearMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            libraryPath = viewModel.libraryDir.absolutePath,
            busy = busy,
            onImport = { pickAudioFile()?.let(viewModel::importFile) },
            onOpenFolder = { openInExplorer(viewModel.libraryDir) },
            onRefresh = { viewModel.refresh(autoSelectFirst = false) }
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                AppTab.HOME -> HomeTab(
                    viewModel = viewModel,
                    files = files,
                    selected = selected,
                    isPlaying = playback.isPlaying,
                    positionMs = playback.currentPositionMs,
                    recording = recording,
                    samples = samples,
                    onDelete = { pendingDelete = it }
                )

                AppTab.STUDIO -> StudioTab(viewModel, files, selected, samples, busy)

                AppTab.LIBRARY -> LibraryTab(
                    viewModel = viewModel,
                    files = files,
                    selected = selected,
                    onDelete = { pendingDelete = it }
                )

                AppTab.EFFECTS -> EffectsTab(viewModel, config, busy)

                AppTab.REPORT -> ReportTab(viewModel, selected, busy)
            }
        }

        if (recording.isRecording) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                RecordingDialog(
                    durationMs = recording.durationMs,
                    amplitude = recording.currentAmplitude,
                    decibels = recording.currentDb,
                    onStop = viewModel::stopRecording
                )
            }
        }

        BottomBar(
            message = message,
            activeTab = tab,
            onTab = viewModel::setTab
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Smazat nahrávku?") },
            text = { Text("\"${item.fileName}\" bude nevratně smazán z disku.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item)
                    pendingDelete = null
                }) { Text("Smazat", color = ForensicRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Zrušit") } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeTab(
    viewModel: DesktopViewModel,
    files: List<AudioFileItem>,
    selected: AudioFileItem?,
    isPlaying: Boolean,
    positionMs: Long,
    recording: RecordingStatus,
    samples: FloatArray?,
    onDelete: (AudioFileItem) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        // Hero banner, same role as the Android "Voice Cleaning Dashboard" card.
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ForensicSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(ForensicCyan.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ForensicCyan, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Voice Cleaning Dashboard",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForensicTextPrimary
                    )
                    Text(
                        "High-Pass 300 Hz  -  Whisper Expander  -  60 FPS FFT  -  Voice isolation",
                        fontSize = 12.sp,
                        color = ForensicCyan
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CyanButton(
                        text = if (recording.isRecording) "Recording..." else "REC live audio",
                        enabled = !recording.isRecording,
                        onClick = { viewModel.startRecording() }
                    )
                    GhostButton("Open library folder", onClick = { openInExplorer(viewModel.libraryDir) })
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader(
            title = "Audio Recordings",
            subtitle = "${files.size} items - WAV, AU, AIFF (imported as mono 44.1 kHz WAV)",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Import file", onClick = { pickAudioFile()?.let(viewModel::importFile) })
                    CyanButton("Open studio", onClick = { viewModel.setTab(AppTab.STUDIO) })
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        if (files.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ForensicSurface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = ForensicTextMuted, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No recordings found.\nImport an audio file or record live audio with REC.",
                        color = ForensicTextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(files, key = { it.id }) { item ->
                    AudioCard(
                        item = item,
                        isSelected = selected?.id == item.id,
                        isPlaying = isPlaying && selected?.id == item.id,
                        positionMs = if (selected?.id == item.id) positionMs else 0L,
                        onSelect = { viewModel.select(item) },
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = viewModel::seekTo,
                        onDelete = { onDelete(item) },
                        onOpenStudio = { viewModel.setTab(AppTab.STUDIO) },
                        onNoiseReduction = viewModel::quickNoiseReduction,
                        onWhisperBoost = viewModel::quickWhisperBoost,
                        onExport = viewModel::exportCopy,
                        waveform = {
                            WaveformPanel(
                                samples = samples,
                                durationMs = item.durationMs,
                                positionMs = if (selected?.id == item.id) positionMs else 0L,
                                loopStartMs = viewModel.config.value.loopStartMs,
                                loopEndMs = viewModel.config.value.loopEndMs,
                                loopEnabled = viewModel.config.value.abLoopEnabled,
                                onSeek = viewModel::seekTo,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Studio
// ---------------------------------------------------------------------------------------------

@Composable
private fun StudioTab(
    viewModel: DesktopViewModel,
    files: List<AudioFileItem>,
    selected: AudioFileItem?,
    samples: FloatArray?,
    busy: Boolean
) {
    val playback by viewModel.playbackState.collectAsState()
    val config by viewModel.config.collectAsState()
    val spectrogram by viewModel.spectrogram.collectAsState()
    val inspection by viewModel.inspection.collectAsState()
    val voiceStats by viewModel.voiceStats.collectAsState()
    val reverb by viewModel.reverbMetrics.collectAsState()
    val lms by viewModel.lmsMetrics.collectAsState()

    Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                title = "Forensic Studio",
                subtitle = selected?.let { "${it.title} - ${it.sampleRate} Hz / ${it.bitDepth}-bit / " +
                    if (it.channels == 1) "mono" else "${it.channels} ch" } ?: "No recording selected",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Home", onClick = { viewModel.setTab(AppTab.HOME) })
                        CyanButton(
                            text = if (busy) "Processing..." else "Save cleaned copy",
                            enabled = !busy && selected != null,
                            onClick = { viewModel.applyAndSave("Cleaned") }
                        )
                    }
                }
            )

            if (selected == null) {
                Box(
                    Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).background(ForensicSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Select a recording on the Home tab to open the studio.",
                        color = ForensicTextMuted
                    )
                }
                return@Column
            }

            WaveformPanel(
                samples = samples,
                durationMs = playback.durationMs,
                positionMs = playback.currentPositionMs,
                loopStartMs = config.loopStartMs,
                loopEndMs = config.loopEndMs,
                loopEnabled = config.abLoopEnabled,
                onSeek = viewModel::seekTo,
                modifier = Modifier.fillMaxWidth()
            )

            SpectrogramPanel(
                spectrogram = spectrogram,
                durationMs = playback.durationMs,
                onInspect = viewModel::inspect,
                modifier = Modifier.fillMaxWidth()
            )

            Card(colors = CardDefaults.cardColors(containerColor = ForensicSurface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ForensicCyan)
                                .clickable(enabled = playback.isReady) { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color(0xFF00252B),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        GhostButton("-5 s", onClick = { viewModel.seekTo(playback.currentPositionMs - 5000) })
                        GhostButton("+5 s", onClick = { viewModel.seekTo(playback.currentPositionMs + 5000) })
                        Text(
                            "${formatMs(playback.currentPositionMs)} / ${formatMs(playback.durationMs)}",
                            color = ForensicCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (playback.isProcessing) {
                            Text("recomputing DSP preview...", color = ForensicOrange, fontSize = 11.sp)
                        }
                    }

                    Slider(
                        value = playback.currentPositionMs.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..playback.durationMs.toFloat().coerceAtLeast(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Speed", fontSize = 11.sp, color = ForensicTextMuted)
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                            val active = playback.speed == speed
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) ForensicCyan else ForensicSurfaceVariant)
                                    .clickable { viewModel.setSpeed(speed) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "${speed}x",
                                    fontSize = 11.sp,
                                    color = if (active) Color(0xFF00252B) else ForensicTextSecondary
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        GhostButton(
                            if (config.abLoopEnabled) "A-B loop ON" else "A-B loop OFF",
                            tint = ForensicOrange,
                            onClick = { viewModel.toggleLoop(!config.abLoopEnabled) }
                        )
                        GhostButton("Set A", tint = ForensicOrange, onClick = { viewModel.setLoopStart(playback.currentPositionMs) })
                        GhostButton("Set B", tint = ForensicOrange, onClick = { viewModel.setLoopEnd(playback.currentPositionMs) })
                        GhostButton("-10 ms A", onClick = { viewModel.setLoopStart(config.loopStartMs - 10) })
                        GhostButton("+10 ms A", onClick = { viewModel.setLoopStart(config.loopStartMs + 10) })
                        GhostButton("-10 ms B", onClick = { viewModel.setLoopEnd(config.loopEndMs - 10) })
                        GhostButton("+10 ms B", onClick = { viewModel.setLoopEnd(config.loopEndMs + 10) })
                    }

                    Text(
                        "A ${formatMs(config.loopStartMs)}    B ${formatMs(config.loopEndMs)}    " +
                            "loop length ${formatMs((config.loopEndMs - config.loopStartMs).coerceAtLeast(0L))}",
                        fontSize = 11.sp,
                        color = ForensicOrange
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricBox("Peak", formatDb(selected.peakDb), Modifier.weight(1f))
                MetricBox("RMS", formatDb(selected.rmsDb), Modifier.weight(1f))
                MetricBox("Noise floor", formatDb(selected.noiseFloorDb), Modifier.weight(1f))
                MetricBox(
                    "Voice band energy",
                    voiceStats?.let { "%.1f %%".format(it.vocalEnergyRatio * 100f) } ?: "-",
                    Modifier.weight(1f)
                )
                MetricBox(
                    "Echo delay",
                    reverb?.let { if (it.detectedDelayMs > 0f) "%.0f ms".format(it.detectedDelayMs) else "none" } ?: "-",
                    Modifier.weight(1f)
                )
                MetricBox(
                    "Adaptive LMS attenuation",
                    lms?.let { "%.1f dB".format(it.noiseAttenuationDb) } ?: "-",
                    Modifier.weight(1f)
                )
            }

            Card(colors = CardDefaults.cardColors(containerColor = ForensicSurface)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Frequency probe", fontSize = 11.sp, color = ForensicTextMuted)
                    Text(
                        inspection?.let { "${it.frequencyHz.toInt()} Hz   ${formatDb(it.decibels)}   @ ${formatMs(it.timeMs)}" }
                            ?: "Click or drag on the spectrogram to read Hz and dBFS",
                        fontSize = 13.sp,
                        color = ForensicCyan
                    )
                }
            }

            Text(
                "Playback applies the same DSP chain that gets written on save " +
                    "(the shared AudioDspChain), so the preview matches the exported file.",
                fontSize = 11.sp,
                color = ForensicTextMuted
            )
        }

        Column(Modifier.width(340.dp).fillMaxHeight()) {
            DspRack(
                config = config,
                busy = busy,
                hasProfile = viewModel.profile.collectAsState().value != null,
                onSpeechBandpass = viewModel::setSpeechBandpass,
                onHighPass = viewModel::setHighPass,
                onHighPassCutoff = viewModel::setHighPassCutoff,
                onLowPass = viewModel::setLowPass,
                onLowPassCutoff = viewModel::setLowPassCutoff,
                onWhisperBoost = viewModel::setWhisperBoost,
                onWhisperGain = viewModel::setWhisperGain,
                onNoiseReduction = viewModel::setNoiseReduction,
                onNoiseLevel = viewModel::setNoiseLevel,
                onVoiceSeparation = viewModel::setVoiceSeparation,
                onVoiceMode = viewModel::setVoiceMode,
                onVoiceStrength = viewModel::setVoiceStrength,
                onFormantBoost = viewModel::setFormantBoost,
                onDeverb = viewModel::setDeverb,
                onDeverbStrength = viewModel::setDeverbStrength,
                onLms = viewModel::setAdaptiveLms,
                onLmsIntensity = viewModel::setLmsIntensity,
                onLmsPreserve = viewModel::setLmsPreserve,
                onLearnFromAb = viewModel::learnFromAbInterval,
                onLearnFromFile = { pickAudioFile()?.let(viewModel::learnFromReferenceFile) },
                onApplyProfile = viewModel::applyProfile,
                onClearProfile = viewModel::clearProfile,
                onApplyCleaned = { viewModel.applyAndSave("Cleaned") },
                modifier = Modifier.fillMaxHeight()
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------------------------

@Composable
private fun LibraryTab(
    viewModel: DesktopViewModel,
    files: List<AudioFileItem>,
    selected: AudioFileItem?,
    onDelete: (AudioFileItem) -> Unit
) {
    Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.width(420.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                title = "Library",
                subtitle = "${files.size} recordings in ${viewModel.libraryDir.absolutePath}",
                action = { CyanButton("Import audio", onClick = { pickAudioFile()?.let(viewModel::importFile) }) }
            )

            if (selected != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ForensicSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ForensicCyan.copy(alpha = 0.45f))
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selected.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForensicTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            StatusChip(selected.status, true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("Sample rate", "${selected.sampleRate} Hz", Modifier.weight(1f))
                            MetricBox("Bit depth", "${selected.bitDepth}-bit PCM", Modifier.weight(1f))
                            MetricBox("Channels", if (selected.channels == 1) "Mono" else "Stereo", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("Peak dBFS", formatDb(selected.peakDb), Modifier.weight(1f))
                            MetricBox("RMS level", formatDb(selected.rmsDb), Modifier.weight(1f))
                            MetricBox("Noise floor", formatDb(selected.noiseFloorDb), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("Duration", selected.durationWithMsFormatted, Modifier.weight(1f))
                            MetricBox("Size", selected.fileSizeFormatted, Modifier.weight(1f))
                            MetricBox("Speech band", "%.0f %%".format(selected.speechBandRatio * 100f), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cyan_studio(viewModel)
                            GhostButton("Export copy", onClick = viewModel::exportCopy)
                            GhostButton("Show in folder", onClick = { openInExplorer(File(selected.filePath).parentFile) }, tint = ForensicNeonGreen)
                        }
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(ForensicSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Select a recording to inspect its metadata.", color = ForensicTextMuted)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.id }) { item ->
                    LibraryRow(
                        item = item,
                        isSelected = selected?.id == item.id,
                        onSelect = { viewModel.select(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = ForensicSurface),
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Forensic handling", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForensicCyan)
                Text(
                    "The library folder is the single source of truth. Imported recordings are copied " +
                        "into it as mono WAV, every cleaning step writes a new file beside the source, and " +
                        "the original is never modified or deleted automatically.",
                    fontSize = 12.sp,
                    color = ForensicTextSecondary
                )
                Text(
                    "Folder: ${viewModel.libraryDir.absolutePath}",
                    fontSize = 12.sp,
                    color = ForensicCyan
                )
                Text(
                    "Reports: ${viewModel.reportsDir.absolutePath}",
                    fontSize = 12.sp,
                    color = ForensicCyan
                )
                Spacer(Modifier.height(6.dp))
                Text("Supported imports", fontSize = 13.sp, color = ForensicTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "WAV (8/16/24/32-bit PCM), AU/AIFF/SND through the JVM audio stack. Everything is " +
                        "converted to mono float PCM for processing; exports are written back as 16-bit WAV.",
                    fontSize = 12.sp,
                    color = ForensicTextSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text("Deletion", fontSize = 13.sp, color = ForensicTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Deleting a recording removes the file from disk immediately and cannot be undone, " +
                        "so the app asks for confirmation first.",
                    fontSize = 12.sp,
                    color = ForensicTextSecondary
                )
            }
        }
    }
}

@Composable
private fun cyan_studio(viewModel: DesktopViewModel) {
    GhostButton("Open studio", onClick = { viewModel.setTab(AppTab.STUDIO) })
}

// ---------------------------------------------------------------------------------------------
// Effects
// ---------------------------------------------------------------------------------------------

@Composable
private fun EffectsTab(viewModel: DesktopViewModel, config: com.example.model.DspConfig, busy: Boolean) {
    val profile by viewModel.profile.collectAsState()

    Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.width(380.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(
                title = "Effect Rack",
                subtitle = "Full parameter access - identical to the Android Effects tab"
            )
            Card(colors = CardDefaults.cardColors(containerColor = ForensicSurface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active chain", fontSize = 13.sp, color = ForensicCyan, fontWeight = FontWeight.Bold)
                    val active = buildList {
                        if (config.voiceSeparationEnabled) add("STFT Voice Separation (${config.voiceSeparationMode.titleCz})")
                        if (config.speechBandpassEnabled) add("Speech Band Pass 300-4000 Hz")
                        if (config.highPassEnabled) add("High-Pass ${config.highPassCutoffHz.toInt()} Hz")
                        if (config.lowPassEnabled) add("Low-Pass ${config.lowPassCutoffHz.toInt()} Hz")
                        if (config.noiseReductionEnabled) add("Spectral Noise Gate ${(config.noiseReductionLevel * 100).toInt()} %")
                        if (config.adaptiveLmsEnabled) add("Adaptive LMS ${(config.adaptiveLmsIntensity * 100).toInt()} %")
                        if (config.deverbEnabled) add("Cepstral De-reverb ${(config.deverbStrength * 100).toInt()} %")
                        if (config.whisperBoostEnabled) add("Whisper Boost +${config.whisperBoostGainDb.toInt()} dB")
                    }
                    if (active.isEmpty()) {
                        Text("No effect enabled - the preview plays the untouched recording.", fontSize = 12.sp, color = ForensicTextMuted)
                    } else {
                        active.forEach { Text("- $it", fontSize = 12.sp, color = ForensicTextSecondary) }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = ForensicSurface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reference / sample removal", fontSize = 13.sp, color = ForensicCyan, fontWeight = FontWeight.Bold)
                    Text(
                        if (profile != null) "Profile learned and ready to subtract." else "No profile learned yet.",
                        fontSize = 12.sp,
                        color = if (profile != null) ForensicOrange else ForensicTextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Learn from A-B", onClick = viewModel::learnFromAbInterval)
                        GhostButton("Learn from file", onClick = { pickAudioFile()?.let(viewModel::learnFromReferenceFile) })
                    }
                    GhostButton(
                        "Subtract profile from whole track",
                        enabled = profile != null,
                        tint = ForensicOrange,
                        onClick = viewModel::applyProfile
                    )
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            DspRack(
                config = config,
                busy = busy,
                hasProfile = profile != null,
                onSpeechBandpass = viewModel::setSpeechBandpass,
                onHighPass = viewModel::setHighPass,
                onHighPassCutoff = viewModel::setHighPassCutoff,
                onLowPass = viewModel::setLowPass,
                onLowPassCutoff = viewModel::setLowPassCutoff,
                onWhisperBoost = viewModel::setWhisperBoost,
                onWhisperGain = viewModel::setWhisperGain,
                onNoiseReduction = viewModel::setNoiseReduction,
                onNoiseLevel = viewModel::setNoiseLevel,
                onVoiceSeparation = viewModel::setVoiceSeparation,
                onVoiceMode = viewModel::setVoiceMode,
                onVoiceStrength = viewModel::setVoiceStrength,
                onFormantBoost = viewModel::setFormantBoost,
                onDeverb = viewModel::setDeverb,
                onDeverbStrength = viewModel::setDeverbStrength,
                onLms = viewModel::setAdaptiveLms,
                onLmsIntensity = viewModel::setLmsIntensity,
                onLmsPreserve = viewModel::setLmsPreserve,
                onLearnFromAb = viewModel::learnFromAbInterval,
                onLearnFromFile = { pickAudioFile()?.let(viewModel::learnFromReferenceFile) },
                onApplyProfile = viewModel::applyProfile,
                onClearProfile = viewModel::clearProfile,
                onApplyCleaned = { viewModel.applyAndSave("Cleaned_DSP") },
                modifier = Modifier.fillMaxHeight()
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------------------------

@Composable
private fun ReportTab(viewModel: DesktopViewModel, selected: AudioFileItem?, busy: Boolean) {
    val report by viewModel.report.collectAsState()

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Forensic Report",
            subtitle = "Acoustic processing protocol for the selected recording",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Regenerate", enabled = selected != null && !busy, onClick = viewModel::generateReport)
                    CyanButton(
                        text = if (busy) "Working..." else "Save report to Documents",
                        enabled = report != null && !busy,
                        onClick = viewModel::saveReport
                    )
                }
            }
        )

        if (selected == null) {
            Box(
                Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).background(ForensicSurface),
                contentAlignment = Alignment.Center
            ) {
                Text("Select a recording first.", color = ForensicTextMuted)
            }
            return@Column
        }

        Card(colors = CardDefaults.cardColors(containerColor = ForensicSurface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Source", fontSize = 13.sp, color = ForensicCyan, fontWeight = FontWeight.Bold)
                Text(selected.fileName, fontSize = 15.sp, color = ForensicTextPrimary, fontWeight = FontWeight.SemiBold)
                Text(selected.filePath, fontSize = 11.sp, color = ForensicTextMuted)
                Text(
                    "${selected.durationWithMsFormatted} - ${selected.sampleRate} Hz / ${selected.bitDepth}-bit / " +
                        if (selected.channels == 1) "mono" else "${selected.channels} ch",
                    fontSize = 12.sp,
                    color = ForensicTextSecondary
                )
            }
        }

        val generated = report
        if (generated == null) {
            Box(
                Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).background(ForensicSurface),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = ForensicTextMuted, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No report generated yet.", color = ForensicTextMuted)
                }
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricBox("Peak level", formatDb(generated.peakLevelDb), Modifier.weight(1f))
            MetricBox("RMS level", formatDb(generated.rmsLevelDb), Modifier.weight(1f))
            MetricBox("Estimated SNR", "%.2f dB".format(generated.estimatedSnrDb), Modifier.weight(1f))
            MetricBox("Dynamic range", "%.2f dB".format(generated.dynamicRangeDb), Modifier.weight(1f))
            MetricBox("Voice band share", "%.1f %%".format(generated.vocalBandEnergyPct), Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ForensicSurface),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Detected low level speech segments", fontSize = 13.sp, color = ForensicCyan, fontWeight = FontWeight.Bold)
                    if (generated.detectedWhisperSegments.isEmpty()) {
                        Text("None detected.", fontSize = 12.sp, color = ForensicTextMuted)
                    } else {
                        generated.detectedWhisperSegments.take(40).forEach { (from, to) ->
                            Text("${formatMs(from)} - ${formatMs(to)}", fontSize = 12.sp, color = ForensicTextSecondary)
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = ForensicSurface),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Applied filters", fontSize = 13.sp, color = ForensicCyan, fontWeight = FontWeight.Bold)
                    if (generated.appliedFilters.isEmpty()) {
                        Text("No filter active when the report was generated.", fontSize = 12.sp, color = ForensicTextMuted)
                    } else {
                        generated.appliedFilters.forEach { Text("- $it", fontSize = 12.sp, color = ForensicTextSecondary) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Integrity", fontSize = 13.sp, color = ForensicCyan, fontWeight = FontWeight.Bold)
                    Text(
                        "The source recording is never modified. Every cleaning step produces a new working " +
                            "copy next to it, so the report always refers to a specific file on disk.",
                        fontSize = 12.sp,
                        color = ForensicTextSecondary
                    )
                    Text(
                        "Saved to: ${viewModel.reportsDir.absolutePath}",
                        fontSize = 11.sp,
                        color = ForensicTextMuted
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Bottom navigation
// ---------------------------------------------------------------------------------------------

@Composable
private fun BottomBar(message: String?, activeTab: AppTab, onTab: (AppTab) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(ForensicSurfaceVariant).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationEntry("Home", Icons.Default.GraphicEq, activeTab == AppTab.HOME) { onTab(AppTab.HOME) }
            NavigationEntry("Studio", Icons.Default.Tune, activeTab == AppTab.STUDIO) { onTab(AppTab.STUDIO) }
            NavigationEntry("Library", Icons.Default.Folder, activeTab == AppTab.LIBRARY) { onTab(AppTab.LIBRARY) }
            NavigationEntry("Effects", Icons.Default.Assessment, activeTab == AppTab.EFFECTS) { onTab(AppTab.EFFECTS) }
            NavigationEntry("Report", Icons.Default.Mic, activeTab == AppTab.REPORT) { onTab(AppTab.REPORT) }
        }
        Row(
            Modifier.fillMaxWidth().background(ForensicSurface).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message ?: "Ready",
                fontSize = 11.sp,
                color = if (message != null) ForensicCyan else ForensicTextMuted
            )
        }
    }
}

@Composable
private fun NavigationEntry(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ForensicCyan.copy(alpha = 0.14f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) ForensicCyan else ForensicTextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) ForensicCyan else ForensicTextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

/** Native Windows file picker, no extra dependency needed. */
private fun pickAudioFile(): File? {
    val dialog = FileDialog(null as Frame?, "Select audio file", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.substringAfterLast('.', "").lowercase() in DesktopAudioDecoder.SUPPORTED_EXTENSIONS
    }
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(directory, file)
}

private fun openInExplorer(directory: File?) {
    if (directory == null || !directory.exists()) return
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(directory)
        }
    } catch (ignored: Exception) {
    }
}
