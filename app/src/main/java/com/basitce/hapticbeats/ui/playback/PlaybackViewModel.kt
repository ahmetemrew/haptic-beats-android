package com.basitce.hapticbeats.ui.playback

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.basitce.hapticbeats.core.audio.AudioAnalyzer
import com.basitce.hapticbeats.core.audio.HapticTimeline
import com.basitce.hapticbeats.core.data.Song
import com.basitce.hapticbeats.core.data.SongAnalysisState
import com.basitce.hapticbeats.core.data.SongRepository
import com.basitce.hapticbeats.core.player.HapticPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val selectedSong: Song? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val intensity: Float = 0.8f,
    val isAnalyzing: Boolean = false,
    val isAudioEnabled: Boolean = true,
    val isHapticsEnabled: Boolean = true,
    val analysisState: String = SongAnalysisState.MISSING,
    val previewBars: List<Int> = emptyList()
) {
    val canPlay: Boolean
        get() = selectedSong != null
}

class PlaybackViewModel(
    application: Application,
    private val repository: SongRepository,
    private val hapticPlayer: HapticPlayer,
    private val audioAnalyzer: AudioAnalyzer
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("hapticbeats_prefs", Context.MODE_PRIVATE)
    private var analysisJob: Job? = null
    private var analysisTargetUri: String? = null

    private val _uiState = MutableStateFlow(
        PlaybackUiState(
            intensity = prefs.getFloat("default_intensity", 0.8f),
            isAudioEnabled = prefs.getBoolean("audio_enabled", true),
            isHapticsEnabled = prefs.getBoolean("haptics_enabled", true)
        )
    )
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    init {
        applyOutputSettings(
            audioEnabled = _uiState.value.isAudioEnabled,
            hapticsEnabled = _uiState.value.isHapticsEnabled,
            intensity = _uiState.value.intensity
        )
        startProgressUpdater()
    }

    fun loadSong(song: Song) {
        viewModelScope.launch {
            val uri = Uri.parse(song.uri)
            val cachedTimeline = repository.loadTimeline(song)

            if (!uiState.value.isHapticsEnabled) {
                analysisJob?.cancel()
                analysisTargetUri = null
                prepareAudioOnly(song, uri, cachedTimeline)
                return@launch
            }

            if (
                cachedTimeline != null &&
                song.analysisState == SongAnalysisState.READY &&
                hapticPlayer.isCurrentSong(uri, song.patternKey)
            ) {
                hapticPlayer.restartCurrent()
                _uiState.value = _uiState.value.copy(
                    selectedSong = song,
                    title = song.title,
                    artist = song.artist,
                    duration = song.duration,
                    currentPosition = 0L,
                    previewBars = cachedTimeline.previewBars(),
                    isAnalyzing = false,
                    analysisState = SongAnalysisState.READY
                )
                play()
                return@launch
            }

            if (cachedTimeline != null && song.analysisState == SongAnalysisState.READY) {
                hapticPlayer.prepare(
                    uri = uri,
                    timeline = cachedTimeline,
                    title = song.title,
                    artist = song.artist,
                    patternKey = song.patternKey
                )
                _uiState.value = _uiState.value.copy(
                    selectedSong = song,
                    title = song.title,
                    artist = song.artist,
                    duration = song.duration,
                    currentPosition = 0L,
                    previewBars = cachedTimeline.previewBars(),
                    isAnalyzing = false,
                    analysisState = SongAnalysisState.READY
                )
                play()
                return@launch
            }

            if (hapticPlayer.isCurrentSong(uri, null)) {
                hapticPlayer.restartCurrent()
            } else {
                hapticPlayer.prepare(
                    uri = uri,
                    timeline = null,
                    title = song.title,
                    artist = song.artist,
                    patternKey = null
                )
            }

            _uiState.value = _uiState.value.copy(
                selectedSong = song,
                title = song.title,
                artist = song.artist,
                duration = song.duration,
                currentPosition = 0L,
                previewBars = emptyList(),
                isAnalyzing = true,
                analysisState = SongAnalysisState.ANALYZING
            )

            play()
            startDeferredAnalysis(song, uri)
        }
    }

    fun play() {
        if (!_uiState.value.canPlay) return
        hapticPlayer.play()
        _uiState.value = _uiState.value.copy(isPlaying = true)
    }

    fun pause() {
        hapticPlayer.pause()
        _uiState.value = _uiState.value.copy(isPlaying = false)
    }

    fun seekTo(position: Long) {
        val safePosition = position.coerceAtLeast(0L)
        hapticPlayer.seekTo(safePosition)
        _uiState.value = _uiState.value.copy(currentPosition = safePosition)
    }

    fun toggleAudio() {
        val current = _uiState.value
        val nextState = enforceAtLeastOneOutput(
            audioEnabled = !current.isAudioEnabled,
            hapticsEnabled = current.isHapticsEnabled
        )
        applyOutputSettings(
            audioEnabled = nextState.first,
            hapticsEnabled = nextState.second,
            intensity = current.intensity
        )
    }

    fun toggleHaptics() {
        val current = _uiState.value
        val nextState = enforceAtLeastOneOutput(
            audioEnabled = current.isAudioEnabled,
            hapticsEnabled = !current.isHapticsEnabled
        )
        applyOutputSettings(
            audioEnabled = nextState.first,
            hapticsEnabled = nextState.second,
            intensity = current.intensity
        )
    }

    fun setIntensity(intensity: Float) {
        val clampedValue = intensity.coerceIn(0.2f, 1.2f)
        applyOutputSettings(
            audioEnabled = _uiState.value.isAudioEnabled,
            hapticsEnabled = _uiState.value.isHapticsEnabled,
            intensity = clampedValue
        )
    }

    fun applyOutputSettings(
        audioEnabled: Boolean,
        hapticsEnabled: Boolean,
        intensity: Float
    ) {
        val (safeAudioEnabled, safeHapticsEnabled) = enforceAtLeastOneOutput(audioEnabled, hapticsEnabled)
        val clampedIntensity = intensity.coerceIn(0.2f, 1.2f)
        prefs.edit()
            .putBoolean("audio_enabled", safeAudioEnabled)
            .putBoolean("haptics_enabled", safeHapticsEnabled)
            .putFloat("default_intensity", clampedIntensity)
            .apply()

        hapticPlayer.isAudioEnabled = safeAudioEnabled
        hapticPlayer.isVibrationEnabled = safeHapticsEnabled
        hapticPlayer.intensity = clampedIntensity

        _uiState.value = _uiState.value.copy(
            isAudioEnabled = safeAudioEnabled,
            isHapticsEnabled = safeHapticsEnabled,
            intensity = clampedIntensity
        )

        if (safeHapticsEnabled) {
            attachExistingTimeline()
            _uiState.value.selectedSong?.let { selectedSong ->
                if (selectedSong.analysisState != SongAnalysisState.READY && _uiState.value.previewBars.isEmpty()) {
                    startDeferredAnalysis(selectedSong, Uri.parse(selectedSong.uri))
                }
            }
        } else {
            analysisJob?.cancel()
            analysisTargetUri = null
            clearTimelinePreview()
        }
    }

    private fun startDeferredAnalysis(song: Song, uri: Uri) {
        if (!uiState.value.isHapticsEnabled) return
        if (analysisTargetUri == song.uri && analysisJob?.isActive == true) return

        analysisJob?.cancel()
        analysisTargetUri = song.uri
        analysisJob = viewModelScope.launch {
            var workingSong = repository.markAnalyzing(song)
            if (_uiState.value.selectedSong?.uri == workingSong.uri) {
                _uiState.value = _uiState.value.copy(
                    selectedSong = workingSong,
                    analysisState = SongAnalysisState.ANALYZING,
                    isAnalyzing = true
                )
            }

            val timeline = audioAnalyzer.analyzeAudio(uri)
            if (!isActive) return@launch

            if (timeline.isEmpty()) {
                workingSong = repository.markFailed(workingSong)
                if (_uiState.value.selectedSong?.uri == workingSong.uri) {
                    _uiState.value = _uiState.value.copy(
                        selectedSong = workingSong,
                        previewBars = emptyList(),
                        isAnalyzing = false,
                        analysisState = SongAnalysisState.FAILED
                    )
                }
                analysisTargetUri = null
                return@launch
            }

            val readySong = repository.saveTimeline(workingSong, timeline)
            if (_uiState.value.selectedSong?.uri == readySong.uri) {
                hapticPlayer.prepare(
                    uri = uri,
                    timeline = timeline,
                    title = readySong.title,
                    artist = readySong.artist,
                    patternKey = readySong.patternKey
                )
                _uiState.value = _uiState.value.copy(
                    selectedSong = readySong,
                    title = readySong.title,
                    artist = readySong.artist,
                    duration = timeline.durationMs.takeIf { it > 0 } ?: readySong.duration,
                    previewBars = timeline.previewBars(),
                    isAnalyzing = false,
                    analysisState = SongAnalysisState.READY
                )
                if (_uiState.value.isHapticsEnabled && hapticPlayer.exoPlayer.isPlaying) {
                    hapticPlayer.seekTo(hapticPlayer.exoPlayer.currentPosition)
                }
            }
            analysisTargetUri = null
        }
    }

    private fun prepareAudioOnly(
        song: Song,
        uri: Uri,
        cachedTimeline: HapticTimeline?
    ) {
        if (hapticPlayer.isCurrentSong(uri, null)) {
            hapticPlayer.restartCurrent()
            _uiState.value = _uiState.value.copy(
                selectedSong = song,
                title = song.title,
                artist = song.artist,
                duration = song.duration,
                currentPosition = 0L,
                previewBars = emptyList(),
                isAnalyzing = false,
                analysisState = song.analysisState
            )
            play()
            return
        }

        hapticPlayer.prepare(
            uri = uri,
            timeline = null,
            title = song.title,
            artist = song.artist,
            patternKey = null
        )
        _uiState.value = _uiState.value.copy(
            selectedSong = song,
            title = song.title,
            artist = song.artist,
            duration = song.duration,
            currentPosition = 0L,
            previewBars = cachedTimeline?.previewBars().orEmpty(),
            isAnalyzing = false,
            analysisState = song.analysisState
        )
        play()
    }

    private fun attachExistingTimeline() {
        val song = _uiState.value.selectedSong ?: return
        viewModelScope.launch {
            val timeline = repository.loadTimeline(song)
            hapticPlayer.prepare(
                uri = Uri.parse(song.uri),
                timeline = timeline,
                title = song.title,
                artist = song.artist,
                patternKey = if (timeline != null && song.analysisState == SongAnalysisState.READY) song.patternKey else null
            )
            _uiState.value = _uiState.value.copy(
                previewBars = timeline?.previewBars().orEmpty(),
                isAnalyzing = timeline == null && _uiState.value.isAnalyzing
            )
            if (timeline != null && hapticPlayer.exoPlayer.isPlaying) {
                hapticPlayer.seekTo(hapticPlayer.exoPlayer.currentPosition)
            }
        }
    }

    private fun clearTimelinePreview() {
        _uiState.value = _uiState.value.copy(
            previewBars = emptyList(),
            isAnalyzing = false
        )
    }

    private fun enforceAtLeastOneOutput(
        audioEnabled: Boolean,
        hapticsEnabled: Boolean
    ): Pair<Boolean, Boolean> {
        if (audioEnabled || hapticsEnabled) return audioEnabled to hapticsEnabled
        return true to false
    }

    private fun startProgressUpdater() {
        viewModelScope.launch {
            while (isActive) {
                val hasSong = _uiState.value.selectedSong != null
                if (hasSong) {
                    _uiState.value = _uiState.value.copy(
                        currentPosition = hapticPlayer.exoPlayer.currentPosition,
                        isPlaying = hapticPlayer.exoPlayer.isPlaying
                    )
                }
                delay(100)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        analysisJob?.cancel()
        hapticPlayer.release()
    }
}

class PlaybackViewModelFactory(
    private val application: Application,
    private val repository: SongRepository,
    private val hapticPlayer: HapticPlayer,
    private val audioAnalyzer: AudioAnalyzer
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaybackViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlaybackViewModel(application, repository, hapticPlayer, audioAnalyzer) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
