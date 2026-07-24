package com.example.android.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.android.R
import com.example.android.domain.home.Song
import com.example.android.ui.theme.AppDimens
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onSongClick: (Song) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = AppDimens.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceLarge)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppDimens.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceExtraSmall)
        ) {
            Text(
                text = stringResource(R.string.home_welcome),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.home_featured),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FeaturedCarousel(state.recentSongs, onSongClick)
        QuickActions()
        RecentSongsSection(
            state = state,
            onSongClick = onSongClick,
            onRetry = { viewModel.onEvent(HomeEvent.Refresh) }
        )
        PreviewSection(
            titleRes = R.string.home_popular,
            icon = Icons.AutoMirrored.Rounded.TrendingUp
        )
        PreviewSection(
            titleRes = R.string.home_global_playlists,
            icon = Icons.Rounded.Public
        )
        PreviewSection(
            titleRes = R.string.home_local_playlists,
            icon = Icons.Rounded.LibraryMusic
        )
    }
}

@Composable
private fun FeaturedCarousel(songs: List<Song>, onSongClick: (Song) -> Unit) {
    val featuredSongs = songs.take(FEATURED_SONG_COUNT)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = AppDimens.spaceLarge
        )
    ) {
        if (featuredSongs.isEmpty()) {
            item {
                FeaturedPlaceholder()
            }
        } else {
            items(featuredSongs, key = Song::id) { song ->
                FeaturedSong(song = song, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun FeaturedSong(song: Song, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(AppDimens.homeHeroWidth)
            .height(AppDimens.homeHeroHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row {
            SongArtwork(
                song = song,
                modifier = Modifier
                    .size(AppDimens.homeHeroHeight)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .padding(AppDimens.spaceMedium),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = TITLE_MAX_LINES
                )
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = ARTIST_MAX_LINES
                )
            }
        }
    }
}

@Composable
private fun FeaturedPlaceholder() {
    Card(
        modifier = Modifier
            .width(AppDimens.homeHeroWidth)
            .height(AppDimens.homeHeroHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.emptyStateIconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun QuickActions() {
    Column(
        modifier = Modifier.padding(horizontal = AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
    ) {
        SectionTitle(R.string.home_quick_actions)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
        ) {
            QuickAction(
                R.string.home_liked_songs,
                Icons.Rounded.Favorite,
                Modifier.weight(1f)
            )
            QuickAction(
                R.string.home_recently_played,
                Icons.Rounded.History,
                Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
        ) {
            QuickAction(
                R.string.home_my_playlists,
                Icons.Rounded.LibraryMusic,
                Modifier.weight(1f)
            )
            QuickAction(
                R.string.home_top_artists,
                Icons.Rounded.PersonSearch,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickAction(
    @StringRes titleRes: Int,
    icon: ImageVector,
    modifier: Modifier
) {
    Card(
        modifier = modifier.height(AppDimens.quickActionHeight),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimens.spaceMedium),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelLarge,
                maxLines = TITLE_MAX_LINES
            )
        }
    }
}

@Composable
private fun RecentSongsSection(
    state: HomeUiState,
    onSongClick: (Song) -> Unit,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)) {
        SectionTitle(
            titleRes = R.string.home_newest,
            modifier = Modifier.padding(horizontal = AppDimens.spaceLarge)
        )
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppDimens.homeSongCoverSize),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.recentSongs.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = AppDimens.spaceLarge
                    )
                ) {
                    items(state.recentSongs, key = Song::id) { song ->
                        SongCard(song = song, onClick = { onSongClick(song) })
                    }
                }
            }
            else -> {
                val message = when {
                    state.requiresAuthentication -> R.string.home_sign_in_for_songs
                    state.hasError -> R.string.home_load_error
                    else -> R.string.home_no_recent_songs
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.spaceLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
                ) {
                    Text(
                        text = stringResource(message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.hasError) {
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(AppDimens.homeSongCardWidth),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
    ) {
        Card(onClick = onClick, shape = MaterialTheme.shapes.medium) {
            SongArtwork(
                song = song,
                modifier = Modifier.size(AppDimens.homeSongCoverSize)
            )
        }
        Text(text = song.title, style = MaterialTheme.typography.titleSmall, maxLines = TITLE_MAX_LINES)
        Text(
            text = song.artistName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = ARTIST_MAX_LINES
        )
    }
}

@Composable
private fun SongArtwork(song: Song, modifier: Modifier) {
    SubcomposeAsyncImage(
        model = song.coverImageUrl,
        contentDescription = stringResource(R.string.song_cover, song.title),
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PreviewSection(@StringRes titleRes: Int, icon: ImageVector) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)) {
        SectionTitle(
            titleRes = titleRes,
            modifier = Modifier.padding(horizontal = AppDimens.spaceLarge)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = AppDimens.spaceLarge
            )
        ) {
            items(PREVIEW_CARD_COUNT) {
                Card(
                    modifier = Modifier
                        .width(AppDimens.homePreviewCardWidth)
                        .height(AppDimens.homePreviewCardHeight),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(AppDimens.spaceMedium),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.home_coming_soon),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier
    )
}

private const val FEATURED_SONG_COUNT = 3
private const val PREVIEW_CARD_COUNT = 3
private const val TITLE_MAX_LINES = 2
private const val ARTIST_MAX_LINES = 1
