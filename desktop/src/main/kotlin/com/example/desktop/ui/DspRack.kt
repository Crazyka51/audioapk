package com.example.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.dsp.VoiceSeparationMode
import com.example.model.DspConfig
import com.example.ui.theme.ForensicCyan
import com.example.ui.theme.ForensicOrange
import com.example.ui.theme.ForensicSurfaceVariant
import com.example.ui.theme.ForensicTextMuted
import com.example.ui.theme.ForensicTextPrimary

/**
 * The forensic effect rack. Every control maps 1:1 onto a field of the shared [DspConfig], so the
 * desktop rack and the Android rack drive exactly the same DSP chain.
 */
@Composable
fun DspRack(
    config: DspConfig,
    busy: Boolean,
    hasProfile: Boolean,
    onSpeechBandpass: (Boolean) -> Unit,
    onHighPass: (Boolean) -> Unit,
    onHighPassCutoff: (Float) -> Unit,
    onLowPass: (Boolean) -> Unit,
    onLowPassCutoff: (Float) -> Unit,
    onWhisperBoost: (Boolean) -> Unit,
    onWhisperGain: (Float) -> Unit,
    onNoiseReduction: (Boolean) -> Unit,
    onNoiseLevel: (Float) -> Unit,
    onVoiceSeparation: (Boolean) -> Unit,
    onVoiceMode: (VoiceSeparationMode) -> Unit,
    onVoiceStrength: (Float) -> Unit,
    onFormantBoost: (Float) -> Unit,
    onDeverb: (Boolean) -> Unit,
    onDeverbStrength: (Float) -> Unit,
    onLms: (Boolean) -> Unit,
    onLmsIntensity: (Float) -> Unit,
    onLmsPreserve: (Float) -> Unit,
    onLearnFromAb: () -> Unit,
    onLearnFromFile: () -> Unit,
    onApplyProfile: () -> Unit,
    onClearProfile: () -> Unit,
    onApplyCleaned: () -> Unit,
    attenuation: Float = 0.88f,
    matchGain: Float = 1.20f,
    offsetMs: Long = 0L,
    protectFormants: Boolean = true,
    onAttenuation: (Float) -> Unit = {},
    onMatchGain: (Float) -> Unit = {},
    onOffset: (Long) -> Unit = {},
    onProtectFormants: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("DSP Effect Rack", style = MaterialTheme.typography.titleMedium, color = ForensicCyan)

        RackSection("Speech band") {
            SwitchRow("Speech Band Pass (300-4000 Hz)", config.speechBandpassEnabled, onSpeechBandpass)
            SwitchRow("High-Pass Filter", config.highPassEnabled, onHighPass)
            ValueSlider("HPF cutoff", config.highPassCutoffHz, 40f..1200f, "${config.highPassCutoffHz.toInt()} Hz", onHighPassCutoff)
            SwitchRow("Low-Pass Filter", config.lowPassEnabled, onLowPass)
            ValueSlider("LPF cutoff", config.lowPassCutoffHz, 1000f..12000f, "${config.lowPassCutoffHz.toInt()} Hz", onLowPassCutoff)
        }

        RackSection("Whisper") {
            SwitchRow("Whisper Dynamic Boost", config.whisperBoostEnabled, onWhisperBoost)
            ValueSlider(
                "Boost gain", config.whisperBoostGainDb, 3f..30f,
                "+${config.whisperBoostGainDb.toInt()} dB", onWhisperGain
            )
        }

        RackSection("Noise") {
            SwitchRow("Spectral Noise Gate", config.noiseReductionEnabled, onNoiseReduction)
            ValueSlider(
                "Reduction", config.noiseReductionLevel, 0.1f..1f,
                "${(config.noiseReductionLevel * 100).toInt()} %", onNoiseLevel
            )
            SwitchRow("Adaptive LMS (dynamic noise)", config.adaptiveLmsEnabled, onLms)
            ValueSlider(
                "Intensity", config.adaptiveLmsIntensity, 0.1f..1f,
                "${(config.adaptiveLmsIntensity * 100).toInt()} %", onLmsIntensity
            )
            ValueSlider(
                "Speech protection", config.adaptiveLmsSpeechPreserve, 0f..1f,
                "${(config.adaptiveLmsSpeechPreserve * 100).toInt()} %", onLmsPreserve
            )
            SwitchRow("Cepstral De-reverberation", config.deverbEnabled, onDeverb)
            ValueSlider(
                "De-reverb strength", config.deverbStrength, 0.1f..1f,
                "${(config.deverbStrength * 100).toInt()} %", onDeverbStrength
            )
        }

        RackSection("Voice separation") {
            SwitchRow("STFT Voice Separation", config.voiceSeparationEnabled, onVoiceSeparation)
            ModeSelector(config.voiceSeparationMode, onVoiceMode)
            ValueSlider(
                "Separation strength", config.voiceSeparationStrength, 0.1f..1f,
                "${(config.voiceSeparationStrength * 100).toInt()} %", onVoiceStrength
            )
            ValueSlider(
                "Formant boost", config.vocalFormantBoostDb, 0f..18f,
                "+${config.vocalFormantBoostDb.toInt()} dB", onFormantBoost
            )
        }

        RackSection("Known song / sample removal") {
            Text(
                if (hasProfile) {
                    "Profile is learned and ready to subtract."
                } else {
                    "Learn a profile from the A-B selection or from a reference file."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (hasProfile) ForensicOrange else ForensicTextMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLearnFromAb, enabled = !busy) { Text("Learn A-B") }
                OutlinedButton(onClick = onLearnFromFile, enabled = !busy) { Text("Learn file") }
            }

            // Manual tuning of the subtraction. The automatic alignment and volume match handle the
            // common case, but a partial imprint left over a track usually needs a nudge.
            ValueSlider(
                "Subtraction strength", attenuation, 0.3f..1f,
                "${(attenuation * 100).toInt()} %", onAttenuation
            )
            ValueSlider(
                "Volume match", matchGain, 0.5f..2.5f,
                "%.2fx".format(matchGain), onMatchGain
            )
            ValueSlider(
                "Time offset", offsetMs.toFloat(), -5000f..5000f,
                "%d ms".format(offsetMs), { onOffset(it.toLong()) }
            )
            SwitchRow("Protect speech formants (300-3400 Hz)", protectFormants, onProtectFormants)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApplyProfile,
                    enabled = hasProfile && !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForensicOrange.copy(alpha = 0.85f),
                        contentColor = Color(0xFF1A0A04)
                    )
                ) { Text("Subtract from whole track") }
                TextButton(onClick = onClearProfile, enabled = hasProfile) { Text("Clear") }
            }
        }

        Button(
            onClick = onApplyCleaned,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = ForensicCyan, contentColor = Color(0xFF00252B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Processing..." else "Apply rack and save working copy")
        }
        Text(
            "Every save writes a new file next to the source. The original recording is never modified.",
            style = MaterialTheme.typography.bodySmall,
            color = ForensicTextMuted
        )
    }
}

@Composable
private fun RackSection(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ForensicSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = ForensicCyan)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = ForensicTextPrimary)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = ForensicTextMuted)
            Text(display, style = MaterialTheme.typography.bodySmall, color = ForensicCyan)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ModeSelector(mode: VoiceSeparationMode, onMode: (VoiceSeparationMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("Mode", style = MaterialTheme.typography.bodySmall, color = ForensicTextMuted)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(mode.titleCz, style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VoiceSeparationMode.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.titleCz) },
                    onClick = {
                        expanded = false
                        onMode(candidate)
                    }
                )
            }
        }
    }
}
