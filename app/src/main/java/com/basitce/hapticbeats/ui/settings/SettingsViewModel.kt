package com.basitce.hapticbeats.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.basitce.hapticbeats.core.data.SongRepository
import com.basitce.hapticbeats.core.haptics.HapticProfile
import com.basitce.hapticbeats.core.localization.AppLanguageManager
import com.basitce.hapticbeats.core.localization.AppLanguageOption
import com.basitce.hapticbeats.core.player.HapticPlayer
import com.basitce.hapticbeats.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultIntensity: Float = 1.0f,
    val hapticProfile: HapticProfile = HapticProfile.BALANCED,
    val isAudioEnabled: Boolean = true,
    val isHapticsEnabled: Boolean = true,
    val isVisualHapticsEnabled: Boolean = true,
    val selectedLanguageTag: String = AppLanguageManager.DEFAULT_LANGUAGE_TAG,
    val availableLanguages: List<AppLanguageOption> = AppLanguageManager.supportedLanguages
)

class SettingsViewModel(
    application: Application,
    private val hapticPlayer: HapticPlayer,
    private val repository: SongRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("hapticbeats_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
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

    fun setLanguage(languageTag: String) {
        val safeLanguageTag = AppLanguageManager.updateLanguage(getApplication(), languageTag)
        _uiState.value = _uiState.value.copy(selectedLanguageTag = safeLanguageTag)
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.2f, 1.5f)
        prefs.edit().putFloat("default_intensity", clamped).apply()
        _uiState.value = _uiState.value.copy(defaultIntensity = clamped)
        hapticPlayer.intensity = clamped
    }

    fun setHapticProfile(profile: HapticProfile) {
        prefs.edit().putString("haptic_profile", profile.name).apply()
        _uiState.value = _uiState.value.copy(hapticProfile = profile)
        hapticPlayer.hapticProfile = profile
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearPatterns()
        }
    }

    fun reanalyzeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markAllForReanalysis()
        }
    }

    private fun loadSettings() {
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
        val defaultIntensity = prefs.getFloat("default_intensity", 1.0f)
        val profileName = prefs.getString("haptic_profile", HapticProfile.BALANCED.name) ?: HapticProfile.BALANCED.name
        val hapticProfile = try {
            HapticProfile.valueOf(profileName)
        } catch (_: Exception) {
            HapticProfile.BALANCED
        }

        _uiState.value = SettingsUiState(
            themeMode = themeMode,
            defaultIntensity = defaultIntensity,
            hapticProfile = hapticProfile,
            isAudioEnabled = audioEnabled,
            isHapticsEnabled = hapticsEnabled,
            isVisualHapticsEnabled = prefs.getBoolean("visual_haptics_enabled", true),
            selectedLanguageTag = AppLanguageManager.storedLanguageTag(getApplication())
        )

        prefs.edit()
            .putBoolean("audio_enabled", audioEnabled)
            .putBoolean("haptics_enabled", hapticsEnabled)
            .putFloat("default_intensity", defaultIntensity)
            .putString("haptic_profile", hapticProfile.name)
            .apply()

        hapticPlayer.intensity = defaultIntensity
        hapticPlayer.hapticProfile = hapticProfile
        hapticPlayer.isAudioEnabled = audioEnabled
        hapticPlayer.isVibrationEnabled = hapticsEnabled
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
    private val hapticPlayer: HapticPlayer,
    private val repository: SongRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application, hapticPlayer, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
