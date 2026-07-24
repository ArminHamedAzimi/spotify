package com.example.android.ui.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val languageTag: String) {
    System(""),
    English("en"),
    Persian("fa");

    fun apply() {
        val locales = if (this == System) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        fun current(): AppLanguage {
            return when (AppCompatDelegate.getApplicationLocales()[0]?.language) {
                English.languageTag -> English
                Persian.languageTag -> Persian
                else -> System
            }
        }
    }
}
