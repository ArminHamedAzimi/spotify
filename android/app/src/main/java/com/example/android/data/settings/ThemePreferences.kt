package com.example.android.data.settings

import android.content.Context
import com.example.android.ui.theme.ThemeMode

class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getThemeMode(): ThemeMode {
        val savedValue = preferences.getString(KEY_THEME_MODE, null)
        return ThemeMode.entries.firstOrNull { it.name == savedValue }
            ?: ThemeMode.System
    }

    fun setThemeMode(themeMode: ThemeMode) {
        preferences.edit()
            .putString(KEY_THEME_MODE, themeMode.name)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "appearance_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
