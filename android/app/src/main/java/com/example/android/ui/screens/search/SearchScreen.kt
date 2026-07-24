package com.example.android.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.android.R
import com.example.android.data.remote.PublicProfileDto
import com.example.android.data.search.SearchHistoryEntity
import com.example.android.domain.home.Song
import com.example.android.ui.components.ModernPlaceholder
import com.example.android.ui.theme.AppDimens

@Composable
fun SearchScreen(
    state: SearchUiState,
    profiles: LazyPagingItems<PublicProfileDto>,
    songs: LazyPagingItems<Song>,
    history: List<SearchHistoryEntity>,
    onQueryChange: (String) -> Unit,
    onTypeChange: (SearchType) -> Unit,
    onCommitQuery: () -> Unit,
    onHistoryClick: (SearchHistoryEntity) -> Unit,
    onRemoveHistory: (SearchHistoryEntity) -> Unit,
    onSongClick: (Song) -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
            .padding(horizontal = AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) onCommitQuery()
                },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onCommitQuery() }),
            shape = MaterialTheme.shapes.large
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)) {
            FilterChip(
                selected = state.type == SearchType.Profiles,
                onClick = {
                    focusManager.clearFocus()
                    onTypeChange(SearchType.Profiles)
                },
                label = { Text(stringResource(R.string.search_profiles)) },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }
            )
            FilterChip(
                selected = state.type == SearchType.Songs,
                onClick = {
                    focusManager.clearFocus()
                    onTypeChange(SearchType.Songs)
                },
                label = { Text(stringResource(R.string.search_songs)) },
                leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) }
            )
        }

        if (state.query.isBlank()) {
            if (history.isEmpty()) {
                ModernPlaceholder(R.string.search_start_typing, Icons.Rounded.Search)
            } else {
                SearchHistory(
                    history = history,
                    onHistoryClick = {
                        focusManager.clearFocus()
                        onHistoryClick(it)
                    },
                    onRemoveHistory = onRemoveHistory
                )
            }
        } else if (state.type == SearchType.Profiles) {
            ProfileResults(profiles)
        } else {
            SongResults(songs) {
                focusManager.clearFocus()
                onSongClick(it)
            }
        }
    }
}

@Composable
private fun ColumnScope.SearchHistory(
    history: List<SearchHistoryEntity>,
    onHistoryClick: (SearchHistoryEntity) -> Unit,
    onRemoveHistory: (SearchHistoryEntity) -> Unit
) {
    Text(
        stringResource(R.string.search_history),
        style = MaterialTheme.typography.titleLarge
    )
    LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall),
        contentPadding = PaddingValues(bottom = AppDimens.spaceLarge)
    ) {
        items(
            count = history.size,
            key = { index -> "${history[index].searchType}:${history[index].query}" }
        ) { index ->
            val item = history[index]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHistoryClick(item) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = AppDimens.spaceMedium,
                        end = AppDimens.spaceSmall,
                        top = AppDimens.spaceSmall,
                        bottom = AppDimens.spaceSmall
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.query, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (item.searchType == SearchType.Songs.name) {
                                stringResource(R.string.search_songs)
                            } else {
                                stringResource(R.string.search_profiles)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onRemoveHistory(item) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.remove_search_history)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ProfileResults(profiles: LazyPagingItems<PublicProfileDto>) {
    PagedResultContainer(
        items = profiles,
        emptyLabel = R.string.search_no_profiles
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall),
            contentPadding = PaddingValues(bottom = AppDimens.spaceLarge)
        ) {
            items(
                count = profiles.itemCount,
                key = { index -> profiles[index]?.id ?: index }
            ) { index ->
                val profile = profiles[index] ?: return@items
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppDimens.spaceMedium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
                    ) {
                        ProfileAvatar(profile)
                        Text(
                            profile.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (profile.hasActivePremium) {
                            Icon(
                                Icons.Rounded.WorkspacePremium,
                                contentDescription = stringResource(R.string.premium_member),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            appendLoader(profiles.loadState.append)
        }
    }
}

@Composable
private fun ProfileAvatar(profile: PublicProfileDto) {
    SubcomposeAsyncImage(
        model = profile.avatarUrl,
        contentDescription = stringResource(R.string.profile_avatar),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(AppDimens.profileAvatarSize)
            .clip(MaterialTheme.shapes.extraLarge)
    ) {
        if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ColumnScope.SongResults(
    songs: LazyPagingItems<Song>,
    onSongClick: (Song) -> Unit
) {
    PagedResultContainer(items = songs, emptyLabel = R.string.search_no_songs) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall),
            contentPadding = PaddingValues(bottom = AppDimens.spaceLarge)
        ) {
            items(
                count = songs.itemCount,
                key = { index -> songs[index]?.id ?: index }
            ) { index ->
                val song = songs[index] ?: return@items
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
            appendLoader(songs.loadState.append)
        }
    }
}

@Composable
private fun <T : Any> ColumnScope.PagedResultContainer(
    items: LazyPagingItems<T>,
    emptyLabel: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    when {
        items.loadState.refresh is LoadState.Loading -> {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        items.loadState.refresh is LoadState.Error -> {
            ModernPlaceholder(R.string.search_failed, Icons.Rounded.Search)
        }
        items.itemCount == 0 -> {
            ModernPlaceholder(emptyLabel, Icons.Rounded.Search)
        }
        else -> content()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appendLoader(loadState: LoadState) {
    if (loadState is LoadState.Loading) {
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
