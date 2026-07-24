package com.example.android.ui.screens.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import com.example.android.R
import com.example.android.ui.components.ModernPlaceholder

@Composable
fun SearchScreen() {
    ModernPlaceholder(R.string.tab_search, Icons.Rounded.Search)
}
