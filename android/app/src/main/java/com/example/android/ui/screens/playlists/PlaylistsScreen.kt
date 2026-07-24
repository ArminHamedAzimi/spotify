package com.example.android.ui.screens.playlists

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.Composable
import com.example.android.R
import com.example.android.ui.components.ModernPlaceholder

@Composable
fun PlaylistsScreen() {
    ModernPlaceholder(R.string.tab_playlists, Icons.Rounded.LibraryMusic)
}
