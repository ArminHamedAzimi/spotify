package com.example.android.ui.screens.downloads

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.android.ui.components.ModernPlaceholder
import com.example.android.ui.theme.AppDimens

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onSongClick: (Song) -> Unit
) {
    if (state.songs.isEmpty()) {
        ModernPlaceholder(R.string.downloads_empty, Icons.Rounded.CloudDownload)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
    ) {
        item {
            Text(
                text = stringResource(R.string.downloaded_songs),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        items(state.songs, key = { it.id }) { downloaded ->
            val song = downloaded.toPlayableSong()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSongClick(song) }
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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
}
