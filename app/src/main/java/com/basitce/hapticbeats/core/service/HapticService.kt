package com.basitce.hapticbeats.core.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.ui.PlayerNotificationManager
import com.basitce.hapticbeats.MyApplication
import com.basitce.hapticbeats.R

class HapticService : Service() {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "HapticBeatsChannel"
    }

    private lateinit var playerNotificationManager: PlayerNotificationManager

    override fun onCreate() {
        super.onCreate()
        val app = application as MyApplication
        val player = app.hapticPlayer.exoPlayer

        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            101,
            NOTIFICATION_CHANNEL_ID
        )
        .setChannelNameResourceId(R.string.app_name)
        .setChannelDescriptionResourceId(R.string.app_name)
        .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
            override fun getCurrentContentTitle(player: Player): CharSequence {
                return player.mediaMetadata.title ?: getString(R.string.app_name)
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                val intent = Intent(this@HapticService, com.basitce.hapticbeats.MainActivity::class.java)
                return PendingIntent.getActivity(
                    this@HapticService, 
                    0, 
                    intent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            override fun getCurrentContentText(player: Player): CharSequence? {
                return player.mediaMetadata.artist ?: getString(R.string.notification_fallback_artist)
            }

            override fun getCurrentLargeIcon(
                player: Player,
                callback: PlayerNotificationManager.BitmapCallback
            ): android.graphics.Bitmap? {
                return null
            }
        })
        .build()

        playerNotificationManager.setPlayer(player)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        playerNotificationManager.setPlayer(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
