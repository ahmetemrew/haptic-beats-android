package com.basitce.hapticbeats

import android.app.Application
import android.content.Context
import com.basitce.hapticbeats.core.audio.AudioAnalyzer
import com.basitce.hapticbeats.core.audio.HapticPatternStore
import com.basitce.hapticbeats.core.haptics.VibrationManager
import com.basitce.hapticbeats.core.localization.AppLanguageManager
import com.basitce.hapticbeats.core.player.HapticPlayer

class MyApplication : Application() {

    lateinit var audioAnalyzer: AudioAnalyzer
    lateinit var vibrationManager: VibrationManager
    lateinit var hapticPlayer: HapticPlayer

    val database by lazy { com.basitce.hapticbeats.core.data.AppDatabase.getDatabase(this) }
    val patternStore by lazy { HapticPatternStore(this) }
    val repository by lazy { com.basitce.hapticbeats.core.data.SongRepository(database.songDao(), patternStore) }

    override fun onCreate() {
        super.onCreate()
        AppLanguageManager.ensureLanguageApplied(this)

        val prefs = getSharedPreferences("hapticbeats_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("default_intensity")) {
            prefs.edit().putFloat("default_intensity", 1.0f).apply()
        }

        audioAnalyzer = AudioAnalyzer(this)
        vibrationManager = VibrationManager(this)
        hapticPlayer = HapticPlayer(this, vibrationManager).apply {
            intensity = prefs.getFloat("default_intensity", 1.0f)
            isAudioEnabled = prefs.getBoolean("audio_enabled", true)
            isVibrationEnabled = prefs.getBoolean("haptics_enabled", true)
        }
    }
}
