package com.example.android.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.android.R
import com.example.android.data.remote.PlaylistDto
import com.example.android.data.remote.PublicProfileDto
import com.example.android.data.social.ConnectionType
import com.example.android.ui.theme.AppDimens

@Composable
fun PublicProfileScreen(
    state: SocialUiState,
    playlists: LazyPagingItems<PlaylistDto>,
    onBack: () -> Unit,
    onToggleFollow: (String) -> Unit,
    onMessage: (PublicProfileDto) -> Unit,
    onConnections: (String, ConnectionType) -> Unit,
    onPlaylist: (String) -> Unit
) {
    val profile = state.selectedProfile ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back))
                }
                Text(profile.name, style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
            ) {
                SocialAvatar(profile, AppDimens.profileHeroAvatarSize)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = MaterialTheme.typography.headlineSmall)
                    if (profile.hasActivePremium) {
                        Icon(
                            Icons.Rounded.WorkspacePremium,
                            stringResource(R.string.premium_member),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (profile.id != state.currentUserId) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
                    ) {
                        Button(
                            onClick = { onToggleFollow(profile.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                stringResource(
                                    if (profile.id in state.followedUserIds) R.string.unfollow
                                    else R.string.follow
                                )
                            )
                        }
                        FilledTonalButton(
                            onClick = { onMessage(profile) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.message))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceExtraLarge)) {
                    SocialCount(state.followerCount, R.string.followers) {
                        onConnections(profile.id, ConnectionType.Followers)
                    }
                    SocialCount(state.followingCount, R.string.following) {
                        onConnections(profile.id, ConnectionType.Following)
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.public_playlists), style = MaterialTheme.typography.titleLarge)
        }
        items(
            count = playlists.itemCount,
            key = { index -> playlists[index]?.id ?: index }
        ) { index ->
            val playlist = playlists[index] ?: return@items
            ListItem(
                headlineContent = { Text(playlist.title) },
                supportingContent = {
                    Text(stringResource(R.string.playlist_song_count, playlist.songCount))
                },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onPlaylist(playlist.id) }
            )
        }
        if (playlists.loadState.append is LoadState.Loading) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            } }
        }
    }
}

@Composable
fun ConnectionsScreen(
    title: String,
    users: LazyPagingItems<PublicProfileDto>,
    followedIds: Set<String>,
    currentUserId: String?,
    onBack: () -> Unit,
    onProfile: (PublicProfileDto) -> Unit,
    onToggleFollow: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back))
                }
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
        }
        items(count = users.itemCount, key = { users[it]?.id ?: it }) { index ->
            val profile = users[index] ?: return@items
            ListItem(
                leadingContent = { SocialAvatar(profile, AppDimens.profileAvatarSize) },
                headlineContent = { Text(profile.name) },
                trailingContent = {
                    if (profile.id != currentUserId) {
                        FilledTonalButton(onClick = { onToggleFollow(profile.id) }) {
                            Text(
                                stringResource(
                                    if (profile.id in followedIds) R.string.unfollow else R.string.follow
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onProfile(profile) }
            )
        }
    }
}

@Composable
private fun SocialCount(count: Int, label: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(count.toString(), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SocialAvatar(profile: PublicProfileDto, size: androidx.compose.ui.unit.Dp) {
    SubcomposeAsyncImage(
        model = profile.avatarUrl,
        contentDescription = stringResource(R.string.profile_avatar),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(MaterialTheme.shapes.extraLarge)
    ) {
        if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, contentDescription = null)
            }
        }
    }
}
