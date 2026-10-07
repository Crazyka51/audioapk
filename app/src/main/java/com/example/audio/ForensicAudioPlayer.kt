package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import com.example.dsp.BiquadFilter
import com.example.dsp.FilterType
import com.example.dsp.SpectralNoiseGate
import com.example.dsp.WhisperCompressor
import com.example.model.DspConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLooping: Boolean = false,
    val speed: Float = 1.0f
)

class ForensicAudioPlayer(private val context: Context) {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var currentFile: File? = null
    private var dspConfig: DspConfig = DspConfig()

    fun loadAudio(file: File, initialConfig: DspConfig = DspConfig()) {
        stop()
        currentFile = file
        this.dspConfig = initialConfig

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                prepare()
                setOnCompletionListener {
                    if (dspConfig.abLoopEnabled && dspConfig.loopEndMs > dspConfig.loopStartMs) {
                        seekTo(dspConfig.loopStartMs)
                        play()
                    } else {
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            currentPositionMs = duration.toLong()
                        )
                    }
                }
            }

            val dur = mediaPlayer?.duration?.toLong() ?: 0L
            _playbackState.value = PlaybackState(
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = dur,
                isLooping = dspConfig.abLoopEnabled,
                speed = dspConfig.playbackSpeed
            )
            applyPlaybackSpeed(dspConfig.playbackSpeed)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) {
            // If AB loop is active and current position is outside bounds, move to A
            if (dspConfig.abLoopEnabled && dspConfig.loopEndMs > dspConfig.loopStartMs) {
                val current = player.currentPosition.toLong()
                if (current < dspConfig.loopStartMs || current >= dspConfig.loopEndMs) {
                    player.seekTo(dspConfig.loopStartMs.toInt())
                }
            }

            player.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            startProgressTracker()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
        stopProgressTracker()
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        val dur = _playbackState.value.durationMs
        val clamped = positionMs.coerceIn(0L, dur)
        mediaPlayer?.seekTo(clamped.toInt())
        _playbackState.value = _playbackState.value.copy(currentPositionMs = clamped)
    }

    fun setSpeed(speed: Float) {
        dspConfig = dspConfig.copy(playbackSpeed = speed)
        applyPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(speed = speed)
    }

    private fun applyPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { player ->
                    val params = player.playbackParams ?: PlaybackParams()
                    params.speed = speed
                    params.pitch = 1.0f // Preserve pitch (no chipmunk/demon effect)
                    player.playbackParams = params
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateDspConfig(config: DspConfig) {
        this.dspConfig = config
        _playbackState.value = _playbackState.value.copy(isLooping = config.abLoopEnabled)
        if (config.playbackSpeed != _playbackState.value.speed) {
            setSpeed(config.playbackSpeed)
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val current = mediaPlayer?.currentPosition?.toLong() ?: 0L
                val duration = mediaPlayer?.duration?.toLong() ?: _playbackState.value.durationMs

                // Handle A-B Loop check
                if (dspConfig.abLoopEnabled && dspConfig.loopEndMs > dspConfig.loopStartMs) {
                    if (current >= dspConfig.loopEndMs) {
                        mediaPlayer?.seekTo(dspConfig.loopStartMs.toInt())
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = dspConfig.loopStartMs,
                            durationMs = duration
                        )
                        delay(20)
                        continue
                    }
                }

                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = current,
                    durationMs = duration,
                    isPlaying = true
                )
                delay(30) // ~30 FPS UI update for smooth playhead
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun stop() {
        stopProgressTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        }
    }

    fun release() {
        stop()
    }
}
