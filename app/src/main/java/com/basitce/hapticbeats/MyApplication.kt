package com.basitce.hapticbeats

import android.app.Application
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

    lateinit var billingManager: com.basitce.hapticbeats.core.billing.BillingManager

    override fun onCreate() {
        super.onCreate()
        AppLanguageManager.ensureLanguageApplied(this)

        audioAnalyzer = AudioAnalyzer(this)
        vibrationManager = VibrationManager(this)
        hapticPlayer = HapticPlayer(this, vibrationManager)

        billingManager = com.basitce.hapticbeats.core.billing.BillingManager(this) { isPremium ->
            val prefs = getSharedPreferences("hapticbeats_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_premium", isPremium).apply()
        }
        billingManager.startConnection()
    }
}
