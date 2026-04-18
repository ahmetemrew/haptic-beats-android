package com.basitce.hapticbeats.core.localization

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.basitce.hapticbeats.R

data class AppLanguageOption(
    val tag: String,
    @StringRes val labelResId: Int
)

object AppLanguageManager {
    private const val PREFS_NAME = "hapticbeats_prefs"
    const val PREF_KEY = "app_language"
    const val DEFAULT_LANGUAGE_TAG = "tr"

    val supportedLanguages = listOf(
        AppLanguageOption(tag = "tr", labelResId = R.string.language_turkish),
        AppLanguageOption(tag = "en", labelResId = R.string.language_english),
        AppLanguageOption(tag = "zh-CN", labelResId = R.string.language_chinese_simplified),
        AppLanguageOption(tag = "hi", labelResId = R.string.language_hindi),
        AppLanguageOption(tag = "es", labelResId = R.string.language_spanish),
        AppLanguageOption(tag = "fr", labelResId = R.string.language_french),
        AppLanguageOption(tag = "ar", labelResId = R.string.language_arabic),
        AppLanguageOption(tag = "bn", labelResId = R.string.language_bengali),
        AppLanguageOption(tag = "pt", labelResId = R.string.language_portuguese),
        AppLanguageOption(tag = "ru", labelResId = R.string.language_russian),
        AppLanguageOption(tag = "ur", labelResId = R.string.language_urdu),
        AppLanguageOption(tag = "id", labelResId = R.string.language_indonesian)
    )

    private val supportedTags = supportedLanguages.map { it.tag }.toSet()

    fun storedLanguageTag(context: Context): String {
        val prefs = prefs(context)
        return normalizeTag(prefs.getString(PREF_KEY, DEFAULT_LANGUAGE_TAG))
    }

    fun updateLanguage(context: Context, languageTag: String): String {
        val safeLanguageTag = normalizeTag(languageTag)
        prefs(context).edit().putString(PREF_KEY, safeLanguageTag).apply()
        applyLanguage(safeLanguageTag)
        return safeLanguageTag
    }

    fun ensureLanguageApplied(context: Context) {
        applyLanguage(storedLanguageTag(context))
    }

    fun normalizeTag(languageTag: String?): String {
        val normalizedTag = languageTag?.trim().orEmpty()
        return normalizedTag.takeIf { it in supportedTags } ?: DEFAULT_LANGUAGE_TAG
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun applyLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}
