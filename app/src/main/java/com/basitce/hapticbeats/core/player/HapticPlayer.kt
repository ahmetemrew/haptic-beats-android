package com.basitce.hapticbeats.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.basitce.hapticbeats.core.audio.HapticTimeline
import com.basitce.hapticbeats.core.audio.HapticTone
import com.basitce.hapticbeats.core.data.Song
import com.basitce.hapticbeats.core.haptics.HapticProfile
import com.basitce.hapticbeats.core.haptics.VibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class RepeatMode {
    OFF, ALL, ONE
}

class HapticPlayer(
    private val context: Context,
    private val vibrationManager: VibrationManager
) {
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        setAudioAttributes(audioAttributes, true)
        setHandleAudioBecomingNoisy(true)
    }

    var currentTimeline: HapticTimeline? = null
        private set
    private var currentUri: Uri? = null
    private var currentPatternKey: String? = null
    var currentSong: Song? = null
        private set

    private val playlist = mutableListOf<Song>()
    private val shuffledIndices = mutableListOf<Int>()
    private var currentPlaylistIndex: Int = -1

    var repeatMode: RepeatMode = RepeatMode.OFF
    var isShuffleEnabled: Boolean = false
        set(value) {
            field = value
            if (value) {
                reshuffle()
            }
        }

    var onSongEnded: (() -> Unit)? = null

    var isVibrationEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                vibrationManager.cancel()
            } else if (exoPlayer.isPlaying) {
                playTimelineFromCurrentPosition()
            }
        }

    var intensity: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 1.5f)
            if (exoPlayer.isPlaying && isVibrationEnabled) {
                playTimelineFromCurrentPosition()
            }
        }

    var hapticProfile: HapticProfile = HapticProfile.BALANCED
        set(value) {
            field = value
            if (exoPlayer.isPlaying && isVibrationEnabled) {
                playTimelineFromCurrentPosition()
            }
        }

    var useHardwarePrimitives: Boolean = true
        set(value) {
            field = value
            if (exoPlayer.isPlaying && isVibrationEnabled) {
                playTimelineFromCurrentPosition()
            }
        }

    var isAudioEnabled: Boolean = true
        set(value) {
            field = value
            exoPlayer.volume = if (value) 1.0f else 0.0f
        }

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (isVibrationEnabled) {
                        playTimelineFromCurrentPosition()
                    }
                } else {
                    vibrationManager.cancel()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    vibrationManager.cancel()
                    handleSongEnded()
                }
            }
        })
    }

    fun setQueue(songs: List<Song>, startingSong: Song? = null) {
        playlist.clear()
        playlist.addAll(songs)
        if (isShuffleEnabled) {
            reshuffle()
        }
        if (startingSong != null) {
            currentPlaylistIndex = playlist.indexOfFirst { it.uri == startingSong.uri }
        }
    }

    private fun reshuffle() {
        shuffledIndices.clear()
        shuffledIndices.addAll(playlist.indices)
        shuffledIndices.shuffle(Random)
        currentSong?.let { activeSong ->
            val activeIndex = playlist.indexOfFirst { it.uri == activeSong.uri }
            if (activeIndex != -1) {
                shuffledIndices.remove(activeIndex)
                shuffledIndices.add(0, activeIndex)
            }
        }
    }

    fun hasNext(): Boolean {
        if (playlist.isEmpty()) return false
        if (repeatMode != RepeatMode.OFF) return true
        return if (isShuffleEnabled) {
            val shufflePos = shuffledIndices.indexOf(currentPlaylistIndex)
            shufflePos < shuffledIndices.size - 1
        } else {
            currentPlaylistIndex < playlist.size - 1
        }
    }

    fun hasPrevious(): Boolean {
        if (playlist.isEmpty()) return false
        if (repeatMode != RepeatMode.OFF) return true
        return if (isShuffleEnabled) {
            val shufflePos = shuffledIndices.indexOf(currentPlaylistIndex)
            shufflePos > 0
        } else {
            currentPlaylistIndex > 0
        }
    }

    fun getNextSong(): Song? {
        if (playlist.isEmpty()) return null
        if (repeatMode == RepeatMode.ONE && currentSong != null) {
            return currentSong
        }
        if (isShuffleEnabled && shuffledIndices.isNotEmpty()) {
            val shufflePos = shuffledIndices.indexOf(currentPlaylistIndex)
            val nextShufflePos = if (shufflePos + 1 < shuffledIndices.size) {
                shufflePos + 1
            } else if (repeatMode == RepeatMode.ALL) {
                0
            } else {
                return null
            }
            val targetIndex = shuffledIndices[nextShufflePos]
            return playlist.getOrNull(targetIndex)
        } else {
            val nextIndex = if (currentPlaylistIndex + 1 < playlist.size) {
                currentPlaylistIndex + 1
            } else if (repeatMode == RepeatMode.ALL) {
                0
            } else {
                return null
            }
            return playlist.getOrNull(nextIndex)
        }
    }

    fun getPreviousSong(): Song? {
        if (playlist.isEmpty()) return null
        if (isShuffleEnabled && shuffledIndices.isNotEmpty()) {
            val shufflePos = shuffledIndices.indexOf(currentPlaylistIndex)
            val prevShufflePos = if (shufflePos > 0) {
                shufflePos - 1
            } else if (repeatMode == RepeatMode.ALL) {
                shuffledIndices.size - 1
            } else {
                0
            }
            val targetIndex = shuffledIndices[prevShufflePos]
            return playlist.getOrNull(targetIndex)
        } else {
            val prevIndex = if (currentPlaylistIndex > 0) {
                currentPlaylistIndex - 1
            } else if (repeatMode == RepeatMode.ALL) {
                playlist.size - 1
            } else {
                0
            }
            return playlist.getOrNull(prevIndex)
        }
    }

    private fun handleSongEnded() {
        if (repeatMode == RepeatMode.ONE) {
            restartCurrent()
            play()
            return
        }
        val next = getNextSong()
        if (next != null) {
            onSongEnded?.invoke()
        }
    }

    fun isCurrentSong(uri: Uri, patternKey: String?): Boolean {
        return currentUri == uri && (patternKey == null || currentPatternKey == patternKey)
    }

    fun prepare(
        uri: Uri,
        timeline: HapticTimeline?,
        title: String,
        artist: String,
        patternKey: String?,
        albumArtUri: String? = null,
        song: Song? = null
    ) {
        currentTimeline = timeline
        currentPatternKey = patternKey
        currentSong = song
        if (song != null) {
            currentPlaylistIndex = playlist.indexOfFirst { it.uri == song.uri }
        }

        if (currentUri == uri) {
            return
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)

        albumArtUri?.let {
            metadataBuilder.setArtworkUri(Uri.parse(it))
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        currentUri = uri
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    fun updateTimeline(timeline: HapticTimeline?) {
        currentTimeline = timeline
        if (exoPlayer.isPlaying && isVibrationEnabled) {
            playTimelineFromCurrentPosition()
        }
    }

    fun restartCurrent() {
        exoPlayer.seekTo(0)
        if (exoPlayer.isPlaying && isVibrationEnabled) {
            playTimelineFromCurrentPosition()
        }
    }

    fun play() {
        exoPlayer.play()
        playTimelineFromCurrentPosition()
    }

    fun pause() {
        exoPlayer.pause()
        vibrationManager.cancel()
    }

    fun seekTo(position: Long) {
        val safePosition = position.coerceAtLeast(0L)
        exoPlayer.seekTo(safePosition)
        if (exoPlayer.isPlaying && isVibrationEnabled) {
            playTimelineFromCurrentPosition()
        }
    }

    private fun playTimelineFromCurrentPosition() {
        if (!isVibrationEnabled) {
            vibrationManager.cancel()
            return
        }
        val timeline = currentTimeline ?: run {
            vibrationManager.cancel()
            return
        }
        vibrationManager.playTimeline(
            timeline = timeline,
            startOffsetMs = exoPlayer.currentPosition,
            intensityScale = intensity,
            profile = hapticProfile
        )
    }

    fun release() {
        vibrationManager.cancel()
        exoPlayer.release()
    }
}
