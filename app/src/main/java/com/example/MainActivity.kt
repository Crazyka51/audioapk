package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppTab
import com.example.ui.components.LiveRecordingDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DspEffectsScreen
import com.example.ui.screens.ForensicReportScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.AudioDetectiveTheme
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicDarkBackground
import com.example.ui.theme.ForensicSurface
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextSecondary
import com.example.viewmodel.AudioDetectiveViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AudioDetectiveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AudioDetectiveTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: AudioDetectiveViewModel) {
    // Android 9 and below need WRITE_EXTERNAL_STORAGE to publish exports into Music/AudioDetective.
    // Android 10+ writes through MediaStore and asks for nothing, so the request is skipped there.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val context = LocalContext.current
        var requested by remember { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { }

        LaunchedEffect(Unit) {
            if (!requested && ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requested = true
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val audioFiles by viewModel.audioFiles.collectAsStateWithLifecycle()
    val selectedAudio by viewModel.selectedAudio.collectAsStateWithLifecycle()
    val pcmSamples by viewModel.pcmSamples.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val dspConfig by viewModel.dspConfig.collectAsStateWithLifecycle()
    val recordingStatus by viewModel.recordingStatus.collectAsStateWithLifecycle()
    val isRecordingOpen by viewModel.isRecordingDialogOpen.collectAsStateWithLifecycle()
    val inspectionPoint by viewModel.inspectionPoint.collectAsStateWithLifecycle()
    val forensicReport by viewModel.forensicReport.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ForensicDarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = ForensicSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                // 1. Home / Dashboard
                NavigationBarItem(
                    selected = activeTab == AppTab.HOME,
                    onClick = { viewModel.setTab(AppTab.HOME) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00363D),
                        selectedTextColor = ForensicCyan,
                        indicatorColor = ForensicCyan,
                        unselectedIconColor = ForensicTextMuted,
                        unselectedTextColor = ForensicTextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_home")
                )

                // 2. Studio
                NavigationBarItem(
                    selected = activeTab == AppTab.STUDIO,
                    onClick = { viewModel.setTab(AppTab.STUDIO) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Studio"
                        )
                    },
                    label = { Text("Studio", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00363D),
                        selectedTextColor = ForensicCyan,
                        indicatorColor = ForensicCyan,
                        unselectedIconColor = ForensicTextMuted,
                        unselectedTextColor = ForensicTextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_studio")
                )

                // 3. Library
                NavigationBarItem(
                    selected = activeTab == AppTab.LIBRARY,
                    onClick = { viewModel.setTab(AppTab.LIBRARY) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Library"
                        )
                    },
                    label = { Text("Library", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00363D),
                        selectedTextColor = ForensicCyan,
                        indicatorColor = ForensicCyan,
                        unselectedIconColor = ForensicTextMuted,
                        unselectedTextColor = ForensicTextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_library")
                )

                // 4. Effects / DSP
                NavigationBarItem(
                    selected = activeTab == AppTab.EFFECTS,
                    onClick = { viewModel.setTab(AppTab.EFFECTS) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Effects"
                        )
                    },
                    label = { Text("Effects", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00363D),
                        selectedTextColor = ForensicCyan,
                        indicatorColor = ForensicCyan,
                        unselectedIconColor = ForensicTextMuted,
                        unselectedTextColor = ForensicTextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_effects")
                )

                // 5. Forensic Report
                NavigationBarItem(
                    selected = activeTab == AppTab.REPORT,
                    onClick = { viewModel.setTab(AppTab.REPORT) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Report"
                        )
                    },
                    label = { Text("Report", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00363D),
                        selectedTextColor = ForensicCyan,
                        indicatorColor = ForensicCyan,
                        unselectedIconColor = ForensicTextMuted,
                        unselectedTextColor = ForensicTextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_report")
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = activeTab,
            label = "tab_transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { tab ->
            when (tab) {
                AppTab.HOME -> DashboardScreen(
                    viewModel = viewModel,
                    audioFiles = audioFiles,
                    selectedAudio = selectedAudio,
                    isPlaying = playbackState.isPlaying,
                    currentPositionMs = playbackState.currentPositionMs,
                    pcmSamples = pcmSamples,
                    onOpenStudio = { viewModel.setTab(AppTab.STUDIO) },
                    onOpenSettings = { viewModel.setTab(AppTab.REPORT) },
                    onOpenEffects = { viewModel.setTab(AppTab.EFFECTS) }
                )
                AppTab.STUDIO -> StudioScreen(
                    viewModel = viewModel,
                    selectedAudio = selectedAudio,
                    isPlaying = playbackState.isPlaying,
                    isProcessing = playbackState.isProcessing,
                    currentPositionMs = playbackState.currentPositionMs,
                    durationMs = playbackState.durationMs,
                    pcmSamples = pcmSamples,
                    dspConfig = dspConfig,
                    inspectionPoint = inspectionPoint,
                    onOpenEffectsRack = { viewModel.setTab(AppTab.EFFECTS) },
                    onGenerateReport = { viewModel.generateForensicReport() }
                )
                AppTab.LIBRARY -> LibraryScreen(
                    viewModel = viewModel,
                    audioFiles = audioFiles,
                    selectedAudio = selectedAudio,
                    onOpenStudio = { viewModel.setTab(AppTab.STUDIO) },
                    onGenerateReport = { viewModel.generateForensicReport() }
                )
                AppTab.EFFECTS -> DspEffectsScreen(
                    viewModel = viewModel,
                    dspConfig = dspConfig
                )
                AppTab.REPORT -> ForensicReportScreen(
                    report = forensicReport,
                    onGenerateNewReport = { viewModel.generateForensicReport() }
                )
            }
        }

        // Live Audio Recorder Modal Dialog
        if (isRecordingOpen) {
            LiveRecordingDialog(
                status = recordingStatus,
                onStartRecording = { title -> viewModel.startRecording(title) },
                onStopRecording = { viewModel.stopRecordingAndSave() },
                onDismiss = { viewModel.closeRecordingDialog() }
            )
        }
    }
}
