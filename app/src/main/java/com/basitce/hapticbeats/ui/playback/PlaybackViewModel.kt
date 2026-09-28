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
import com.basitce.hapticbeats.core.player.RepeatMode
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
    val albumArtUri: String? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val intensity: Float = 1.0f,
    val isAnalyzing: Boolean = false,
    val isAudioEnabled: Boolean = true,
    val isHapticsEnabled: Boolean = true,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val analysisState: String = SongAnalysisState.MISSING,
    val previewBars: List<Int> = emptyList(),
    val currentHapticAmplitude: Int = 0
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
            intensity = prefs.getFloat("default_intensity", 1.0f),
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
        hapticPlayer.onSongEnded = {
            playNext()
        }
        startProgressUpdater()
    }

    fun loadSong(song: Song, queue: List<Song> = emptyList()) {
        if (queue.isNotEmpty()) {
            hapticPlayer.setQueue(queue, song)
        }
        viewModelScope.launch {
            val uri = Uri.parse(song.uri)
            val cachedTimeline = repository.loadTimeline(song)

            _uiState.value = _uiState.value.copy(
                selectedSong = song,
                title = song.title,
                artist = song.artist,
                albumArtUri = song.albumArtUri,
                duration = song.duration,
                currentPosition = 0L,
                hasNext = hapticPlayer.hasNext(),
                hasPrevious = hapticPlayer.hasPrevious(),
                isShuffleEnabled = hapticPlayer.isShuffleEnabled,
                repeatMode = hapticPlayer.repeatMode
            )

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
                    patternKey = song.patternKey,
                    albumArtUri = song.albumArtUri,
                    song = song
                )
                _uiState.value = _uiState.value.copy(
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
                    patternKey = null,
                    albumArtUri = song.albumArtUri,
                    song = song
                )
            }

            _uiState.value = _uiState.value.copy(
                previewBars = emptyList(),
                isAnalyzing = true,
                analysisState = SongAnalysisState.ANALYZING
            )

            play()
            startDeferredAnalysis(song, uri)
        }
    }

    fun playNext() {
        val next = hapticPlayer.getNextSong()
        if (next != null) {
            loadSong(next)
        }
    }

    fun playPrevious() {
        val prev = hapticPlayer.getPreviousSong()
        if (prev != null) {
            loadSong(prev)
        }
    }

    fun toggleShuffle() {
        val newState = !hapticPlayer.isShuffleEnabled
        hapticPlayer.isShuffleEnabled = newState
        _uiState.value = _uiState.value.copy(
            isShuffleEnabled = newState,
            hasNext = hapticPlayer.hasNext(),
            hasPrevious = hapticPlayer.hasPrevious()
        )
    }

    fun toggleRepeat() {
        val nextMode = when (hapticPlayer.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        hapticPlayer.repeatMode = nextMode
        _uiState.value = _uiState.value.copy(
            repeatMode = nextMode,
            hasNext = hapticPlayer.hasNext(),
            hasPrevious = hapticPlayer.hasPrevious()
        )
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
        val clampedValue = intensity.coerceIn(0.2f, 1.5f)
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
        val clampedIntensity = intensity.coerceIn(0.2f, 1.5f)
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
                hapticPlayer.updateTimeline(timeline)
                _uiState.value = _uiState.value.copy(
                    selectedSong = readySong,
                    previewBars = timeline.previewBars(),
                    isAnalyzing = false,
                    analysisState = SongAnalysisState.READY
                )
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
            patternKey = null,
            albumArtUri = song.albumArtUri,
            song = song
        )
        _uiState.value = _uiState.value.copy(
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
            hapticPlayer.updateTimeline(timeline)
            _uiState.value = _uiState.value.copy(
                previewBars = timeline?.previewBars().orEmpty(),
                isAnalyzing = timeline == null && _uiState.value.isAnalyzing
            )
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
                    val pos = hapticPlayer.exoPlayer.currentPosition
                    val duration = if (hapticPlayer.exoPlayer.duration > 0) {
                        hapticPlayer.exoPlayer.duration
                    } else {
                        _uiState.value.duration
                    }
                    val amplitude = if (hapticPlayer.isVibrationEnabled) {
                        hapticPlayer.currentTimeline?.amplitudeAt(pos) ?: 0
                    } else {
                        0
                    }
                    _uiState.value = _uiState.value.copy(
                        currentPosition = pos,
                        duration = duration,
                        isPlaying = hapticPlayer.exoPlayer.isPlaying,
                        hasNext = hapticPlayer.hasNext(),
                        hasPrevious = hapticPlayer.hasPrevious(),
                        currentHapticAmplitude = amplitude
                    )
                }
                delay(50)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        analysisJob?.cancel()
        // DO NOT release hapticPlayer here; it is application-scoped and must keep playing in background!
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
