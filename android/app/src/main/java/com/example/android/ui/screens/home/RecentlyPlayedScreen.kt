package com.example.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
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
import com.example.android.ui.components.ModernPlaceholder
import com.example.android.ui.theme.AppDimens

@Composable
fun RecentlyPlayedScreen(
    songs: List<Song>,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            RecentlyPlayedHeader(onBack)
            ModernPlaceholder(R.string.home_no_recently_played, Icons.Rounded.History)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
    ) {
        item { RecentlyPlayedHeader(onBack) }
        items(songs, key = Song::id) { song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
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

@Composable
private fun RecentlyPlayedHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.navigate_back)
            )
        }
        Text(
            stringResource(R.string.home_recently_played),
            style = MaterialTheme.typography.headlineSmall
        )
    }
}
