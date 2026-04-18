package com.basitce.hapticbeats.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.basitce.hapticbeats.core.data.SongRepository
import com.basitce.hapticbeats.core.localization.AppLanguageManager
import com.basitce.hapticbeats.core.localization.AppLanguageOption
import com.basitce.hapticbeats.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isPremium: Boolean = false,
    val defaultIntensity: Float = 0.8f,
    val isAudioEnabled: Boolean = true,
    val isHapticsEnabled: Boolean = true,
    val isVisualHapticsEnabled: Boolean = false,
    val selectedLanguageTag: String = AppLanguageManager.DEFAULT_LANGUAGE_TAG,
    val availableLanguages: List<AppLanguageOption> = AppLanguageManager.supportedLanguages
)

class SettingsViewModel(
    application: Application,
    private val billingManager: com.basitce.hapticbeats.core.billing.BillingManager,
    private val hapticPlayer: com.basitce.hapticbeats.core.player.HapticPlayer,
    private val repository: SongRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("hapticbeats_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeBilling()
    }

    private fun observeBilling() {
        viewModelScope.launch {
            billingManager.isPremium.collect { isBillingPremium ->
                val isPromoPremium = prefs.getBoolean("is_premium_promo", false)
                val finalPremiumStatus = isBillingPremium || isPromoPremium

                _uiState.value = _uiState.value.copy(isPremium = finalPremiumStatus)
                prefs.edit().putBoolean("is_premium", isBillingPremium).apply()
            }
        }
    }

    fun purchasePremium(activity: Activity) {
        billingManager.launchPurchaseFlow(activity)
    }

    fun toggleAudio(isEnabled: Boolean) {
        val (audioEnabled, hapticsEnabled) = enforceAtLeastOneOutput(
            audioEnabled = isEnabled,
            hapticsEnabled = _uiState.value.isHapticsEnabled
        )
        persistOutputState(audioEnabled, hapticsEnabled)
    }

    fun toggleHaptics(isEnabled: Boolean) {
        val (audioEnabled, hapticsEnabled) = enforceAtLeastOneOutput(
            audioEnabled = _uiState.value.isAudioEnabled,
            hapticsEnabled = isEnabled
        )
        persistOutputState(audioEnabled, hapticsEnabled)
    }

    fun toggleVisualHaptics(isEnabled: Boolean) {
        prefs.edit().putBoolean("visual_haptics_enabled", isEnabled).apply()
        _uiState.value = _uiState.value.copy(isVisualHapticsEnabled = isEnabled)
    }

    fun submitPromoCode(code: String) {
        if (code == "ciki50k" || code == "çiki50k" || code == "Ã§iki50k") {
            prefs.edit().putBoolean("is_premium_promo", true).apply()
            _uiState.value = _uiState.value.copy(isPremium = true)
        }
    }

    fun setLanguage(languageTag: String) {
        val safeLanguageTag = AppLanguageManager.updateLanguage(getApplication(), languageTag)
        _uiState.value = _uiState.value.copy(selectedLanguageTag = safeLanguageTag)
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setIntensity(intensity: Float) {
        prefs.edit().putFloat("default_intensity", intensity).apply()
        _uiState.value = _uiState.value.copy(defaultIntensity = intensity)
        hapticPlayer.intensity = intensity
    }

    fun clearCache() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.clearPatterns()
        }
    }

    fun reanalyzeAll() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.markAllForReanalysis()
        }
    }

    private fun loadSettings() {
        val isPromoPremium = prefs.getBoolean("is_premium_promo", false)
        val isBillingPremium = prefs.getBoolean("is_premium", false)
        val themeModeName = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeName)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
        val (audioEnabled, hapticsEnabled) = enforceAtLeastOneOutput(
            audioEnabled = prefs.getBoolean("audio_enabled", true),
            hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
        )

        _uiState.value = SettingsUiState(
            themeMode = themeMode,
            isPremium = isPromoPremium || isBillingPremium,
            defaultIntensity = prefs.getFloat("default_intensity", 0.8f),
            isAudioEnabled = audioEnabled,
            isHapticsEnabled = hapticsEnabled,
            isVisualHapticsEnabled = prefs.getBoolean("visual_haptics_enabled", false),
            selectedLanguageTag = AppLanguageManager.storedLanguageTag(getApplication())
        )

        prefs.edit()
            .putBoolean("audio_enabled", audioEnabled)
            .putBoolean("haptics_enabled", hapticsEnabled)
            .apply()
        hapticPlayer.intensity = _uiState.value.defaultIntensity
        hapticPlayer.isAudioEnabled = _uiState.value.isAudioEnabled
        hapticPlayer.isVibrationEnabled = _uiState.value.isHapticsEnabled
    }

    private fun persistOutputState(audioEnabled: Boolean, hapticsEnabled: Boolean) {
        prefs.edit()
            .putBoolean("audio_enabled", audioEnabled)
            .putBoolean("haptics_enabled", hapticsEnabled)
            .apply()
        hapticPlayer.isAudioEnabled = audioEnabled
        hapticPlayer.isVibrationEnabled = hapticsEnabled
        _uiState.value = _uiState.value.copy(
            isAudioEnabled = audioEnabled,
            isHapticsEnabled = hapticsEnabled
        )
    }

    private fun enforceAtLeastOneOutput(
        audioEnabled: Boolean,
        hapticsEnabled: Boolean
    ): Pair<Boolean, Boolean> {
        if (audioEnabled || hapticsEnabled) return audioEnabled to hapticsEnabled
        return true to false
    }
}

class SettingsViewModelFactory(
    private val application: Application,
    private val billingManager: com.basitce.hapticbeats.core.billing.BillingManager,
    private val hapticPlayer: com.basitce.hapticbeats.core.player.HapticPlayer,
    private val repository: SongRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application, billingManager, hapticPlayer, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
