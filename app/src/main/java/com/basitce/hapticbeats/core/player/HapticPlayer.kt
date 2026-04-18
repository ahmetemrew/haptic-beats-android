package com.basitce.hapticbeats.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.basitce.hapticbeats.core.audio.HapticTimeline
import com.basitce.hapticbeats.core.haptics.VibrationManager

class HapticPlayer(
    private val context: Context,
    private val vibrationManager: VibrationManager
) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private var currentTimeline: HapticTimeline? = null
    private var currentUri: Uri? = null
    private var currentPatternKey: String? = null

    var isVibrationEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                vibrationManager.cancel()
            } else if (exoPlayer.isPlaying) {
                playTimelineFromCurrentPosition()
            }
        }

    var intensity: Float = 0.8f
        set(value) {
            field = value.coerceIn(0.2f, 1.2f)
            if (exoPlayer.isPlaying) {
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
                    playTimelineFromCurrentPosition()
                } else {
                    vibrationManager.cancel()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    vibrationManager.cancel()
                }
            }
        })
    }

    fun isCurrentSong(uri: Uri, patternKey: String?): Boolean {
        return currentUri == uri && currentPatternKey == patternKey
    }

    fun prepare(
        uri: Uri,
        timeline: HapticTimeline?,
        title: String,
        artist: String,
        patternKey: String?
    ) {
        currentTimeline = timeline
        currentPatternKey = patternKey

        if (currentUri == uri) {
            return
        }

        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        currentUri = uri
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    fun restartCurrent() {
        exoPlayer.seekTo(0)
        if (exoPlayer.isPlaying) {
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
        exoPlayer.seekTo(position.coerceAtLeast(0L))
        if (exoPlayer.isPlaying) {
            playTimelineFromCurrentPosition()
        }
    }

    fun release() {
        vibrationManager.cancel()
        exoPlayer.release()
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
            intensityScale = intensity
        )
    }
}
