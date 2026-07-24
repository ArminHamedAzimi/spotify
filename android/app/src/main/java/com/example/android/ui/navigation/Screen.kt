package com.example.android.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.android.R

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    data object Home : Screen("home", R.string.tab_home, Icons.Filled.Home)
    data object Search : Screen("search", R.string.tab_search, Icons.Filled.Search)
    data object Downloads : Screen("downloads", R.string.tab_downloads, Icons.Filled.CloudDownload)
    data object Playlists : Screen("playlists", R.string.tab_playlists, Icons.Filled.LibraryMusic)
    data object Profile : Screen("profile", R.string.tab_profile, Icons.Filled.Person)
    data object Notifications : Screen("notifications", R.string.notifications, Icons.Filled.Notifications)
    data object Settings : Screen("settings", R.string.settings, Icons.Filled.Settings)
    data object Player : Screen("player", R.string.player, Icons.Filled.PlayCircle)
    data object PlaylistDetail : Screen("playlist/{playlistId}", R.string.tab_playlists, Icons.Filled.LibraryMusic) {
        fun route(id: String) = "playlist/$id"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Downloads,
    Screen.Playlists,
    Screen.Profile
)
