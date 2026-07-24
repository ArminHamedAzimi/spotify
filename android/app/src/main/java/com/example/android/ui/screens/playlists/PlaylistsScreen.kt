package com.example.android.ui.screens.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
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
        item {
            Text(
                stringResource(R.string.my_playlists),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        items(state.playlists, key = { it.id }) { playlist ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onOpen(playlist.id) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = AppDimens.cardElevation
            ) {
                Row(
                    modifier = Modifier.padding(AppDimens.spaceMedium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
                ) {
                    Surface(
                        modifier = Modifier.size(AppDimens.quickActionHeight),
                        shape = MaterialTheme.shapes.medium,
                        color = if (playlist.isLiked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playlist.isLiked) {
                                    Icons.Rounded.Favorite
                                } else {
                                    Icons.Rounded.MusicNote
                                },
                                contentDescription = null,
                                tint = if (playlist.isLiked) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceExtraSmall)
                    ) {
                        Text(playlist.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(
                                R.string.playlist_song_count,
                                playlist.songs.size
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (playlist.description.isNotBlank()) {
                            Text(
                                playlist.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    state: PlaylistsUiState,
    onPlay: (Song, String, Boolean) -> Unit,
    onStart: (String, Boolean) -> Unit,
    onRemove: (String, String) -> Unit,
    onBack: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back)
                    )
                }
                Text(
                    playlist.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Row(
                modifier = Modifier.padding(top = AppDimens.spaceMedium),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
            ) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onPlay(song, playlist.id, false) }
                        .padding(AppDimens.spaceSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
                ) {
                    SubcomposeAsyncImage(
                        model = song.coverImageUrl,
                        contentDescription = stringResource(R.string.song_cover, song.title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(AppDimens.miniPlayerArtworkSize)
                            .clip(MaterialTheme.shapes.small)
                    ) {
                        if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
                            SubcomposeAsyncImageContent()
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null)
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            song.artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
