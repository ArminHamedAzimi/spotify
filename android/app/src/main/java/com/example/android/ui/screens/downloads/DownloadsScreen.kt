package com.example.android.ui.screens.downloads

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.runtime.Composable
import com.example.android.R
import com.example.android.ui.components.ModernPlaceholder

@Composable
fun DownloadsScreen() {
    ModernPlaceholder(R.string.tab_downloads, Icons.Rounded.CloudDownload)
}
