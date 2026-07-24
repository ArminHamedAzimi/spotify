package com.example.android.ui.screens.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import com.example.android.domain.downloads.DownloadedSong
import com.example.android.ui.components.ModernPlaceholder
import com.example.android.ui.theme.AppDimens
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DownloadsScreen(
    songs: LazyPagingItems<DownloadedSong>,
    isPremium: Boolean,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (String) -> Unit
) {
    if (songs.loadState.refresh is LoadState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (songs.itemCount == 0) {
        ModernPlaceholder(R.string.downloads_empty, Icons.Rounded.CloudDownload)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)) {
                Text(
                    text = stringResource(R.string.downloaded_songs),
                    style = MaterialTheme.typography.headlineSmall
                )
                if (!isPremium) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = stringResource(R.string.download_playback_premium_required),
                            modifier = Modifier.padding(AppDimens.spaceMedium),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        items(
            count = songs.itemCount,
            key = { index -> songs[index]?.id ?: index }
        ) { index ->
            val downloaded = songs[index] ?: return@items
            val song = downloaded.toPlayableSong()
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        onRemoveSong(song.id)
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
                    val alignment = if (
                        dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                    ) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = AppDimens.spaceLarge),
                        contentAlignment = alignment
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.remove_download),
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
                        .clickable(enabled = isPremium) { onSongClick(song) }
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
                    Icon(
                        Icons.Rounded.CloudDownload,
                        contentDescription = stringResource(R.string.downloaded)
                    )
                }
            }
        }
        if (songs.loadState.append is LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.spaceMedium),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
