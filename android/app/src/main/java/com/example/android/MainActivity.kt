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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.android.ui.navigation.SpotifyNavGraph
import com.example.android.ui.localization.AppLanguage
import com.example.android.ui.theme.SpotifyTheme
import com.example.android.ui.theme.ThemeMode

class MainActivity : AppCompatActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.System) }
            val appLanguage = AppLanguage.current()
            SpotifyTheme(themeMode = themeMode) {
                SpotifyNavGraph(
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                    appLanguage = appLanguage,
                    onLanguageChange = AppLanguage::apply
                )
            }
        }
    }
}
