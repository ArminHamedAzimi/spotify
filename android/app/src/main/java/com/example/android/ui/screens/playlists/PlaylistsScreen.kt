package com.example.android.ui.screens.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.android.R
import com.example.android.domain.home.Song
import com.example.android.ui.theme.AppDimens

@Composable
fun PlaylistsScreen(state: PlaylistsUiState, onOpen: (String) -> Unit) {
    if (state.isLoading && state.playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
    ) {
        item { Text(stringResource(R.string.my_playlists), style = MaterialTheme.typography.headlineSmall) }
        items(state.playlists, key = { it.id }) { playlist ->
            ListItem(
                headlineContent = { Text(playlist.title) },
                supportingContent = { Text(stringResource(R.string.playlist_song_count, playlist.songs.size)) },
                modifier = Modifier.clickable { onOpen(playlist.id) }
            )
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    state: PlaylistsUiState,
    onPlay: (Song, String, Boolean) -> Unit,
    onStart: (String, Boolean) -> Unit,
    onRemove: (String, String) -> Unit
) {
    val playlist = state.selected
    if (state.isLoading || playlist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
    ) {
        item {
            Text(playlist.title, style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)) {
                Button(onClick = { onStart(playlist.id, false) }) {
                    Icon(Icons.Rounded.PlayArrow, null); Text(stringResource(R.string.play))
                }
                FilledTonalButton(onClick = { onStart(playlist.id, true) }) {
                    Icon(Icons.Rounded.Shuffle, null); Text(stringResource(R.string.shuffle))
                }
            }
        }
        items(state.songs, key = Song::id) { song ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        onRemove(playlist.id, song.id)
                        true
                    } else {
                        false
                    }
                }
            )
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = true,
                enableDismissFromEndToStart = true,
                backgroundContent = {
                    val alignment =
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.shapes.medium
                            )
                            .padding(horizontal = AppDimens.spaceLarge),
                        contentAlignment = alignment
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.remove_from_playlist),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            ) {
                ListItem(
                    headlineContent = { Text(song.title) },
                    supportingContent = { Text(song.artistName) },
                    modifier = Modifier.clickable { onPlay(song, playlist.id, false) }
                )
            }
        }
    }
}
