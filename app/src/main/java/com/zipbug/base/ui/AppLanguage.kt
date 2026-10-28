package com.zipbug.base.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Stores and applies the language selected for Zip_Bug's interface. */
object AppLanguage {
    const val ENGLISH = "en"
    const val BURMESE = "my"

    private const val PREFERENCES = "zipbug.settings"
    private const val LANGUAGE_KEY = "uiLanguage"

    fun initialize(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val language = preferences.getString(LANGUAGE_KEY, null) ?: ENGLISH.also {
            preferences.edit().putString(LANGUAGE_KEY, it).apply()
        }
        apply(language)
    }

    fun selected(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, ENGLISH) ?: ENGLISH

    fun set(context: Context, language: String) {
        require(language == ENGLISH || language == BURMESE) {
            "Unsupported app language: $language"
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_KEY, language)
            .apply()
        apply(language)
    }

    private fun apply(language: String) {
        val locales = LocaleListCompat.forLanguageTags(language)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != language) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
