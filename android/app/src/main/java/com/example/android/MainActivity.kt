package com.example.android

import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatDelegate
import com.example.android.data.settings.ThemePreferences
import com.example.android.ui.navigation.SpotifyNavGraph
import com.example.android.ui.localization.AppLanguage
import com.example.android.ui.theme.SpotifyTheme
import com.example.android.ui.theme.ThemeMode

class MainActivity : AppCompatActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        val themePreferences = ThemePreferences(this)
        val savedThemeMode = themePreferences.getThemeMode()
        AppCompatDelegate.setDefaultNightMode(savedThemeMode.toAppCompatNightMode())
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(savedThemeMode) }
            val appLanguage = AppLanguage.current()
            SpotifyTheme(themeMode = themeMode) {
                SpotifyNavGraph(
                    themeMode = themeMode,
                    onThemeModeChange = { selectedMode ->
                        themePreferences.setThemeMode(selectedMode)
                        themeMode = selectedMode
                        AppCompatDelegate.setDefaultNightMode(
                            selectedMode.toAppCompatNightMode()
                        )
                    },
                    appLanguage = appLanguage,
                    onLanguageChange = AppLanguage::apply
                )
            }
        }
    }
}

private fun ThemeMode.toAppCompatNightMode(): Int = when (this) {
    ThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
    ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
}
