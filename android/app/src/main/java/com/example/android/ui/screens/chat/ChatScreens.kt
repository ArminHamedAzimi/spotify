package com.example.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import com.example.android.R
import com.example.android.data.chat.ChatMessageEntity
import com.example.android.data.remote.ConversationDto
import com.example.android.data.remote.PublicProfileDto
import com.example.android.domain.home.Song
import com.example.android.ui.theme.AppDimens
import com.example.android.ui.theme.ChatVisuals
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun ConversationsScreen(
    conversations: LazyPagingItems<ConversationDto>,
    onBack: () -> Unit,
    onConversation: (PublicProfileDto) -> Unit
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
                Text(stringResource(R.string.messages), style = MaterialTheme.typography.headlineSmall)
            }
        }
        items(conversations.itemCount, key = { conversations[it]?.id ?: it }) { index ->
            val item = conversations[index] ?: return@items
            ListItem(
                leadingContent = {
                    AsyncImage(
                        model = item.otherUser.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(AppDimens.profileAvatarSize)
                            .clip(MaterialTheme.shapes.extraLarge)
                    )
                },
                headlineContent = { Text(item.otherUser.name) },
                supportingContent = {
                    Text(
                        item.lastMessage?.body?.ifBlank {
                            stringResource(R.string.shared_song)
                        } ?: stringResource(R.string.start_conversation),
                        maxLines = 1
                    )
                },
                trailingContent = {
                    if (item.unreadCount > 0) {
                        Badge { Text(item.unreadCount.toString()) }
                    }
                },
                modifier = Modifier.clip(MaterialTheme.shapes.large)
                    .clickable { onConversation(item.otherUser) }
            )
        }
    }
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    currentSongId: String?,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onShareSong: (String) -> Unit,
    onPlaySong: (Song) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.spaceSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back))
            }
            Surface(
                modifier = Modifier.size(AppDimens.profileAvatarSize),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                AsyncImage(
                    model = state.otherUser?.avatarUrl,
                    contentDescription = stringResource(R.string.profile_avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                state.otherUser?.name.orEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimens.spaceSmall),
                style = MaterialTheme.typography.titleLarge
            )
        }
        if (state.isTyping) {
            Text(
                stringResource(R.string.is_typing),
                modifier = Modifier.padding(horizontal = AppDimens.spaceLarge),
                color = MaterialTheme.colorScheme.primary
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(AppDimens.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
        ) {
            items(state.messages, key = ChatMessageEntity::clientMessageId) { message ->
                MessageBubble(
                    message,
                    mine = message.senderId == state.currentUserId,
                    otherUserAvatarUrl = state.otherUser?.avatarUrl,
                    onPlaySong = onPlaySong
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
        ) {
            IconButton(
                onClick = { currentSongId?.let(onShareSong) },
                enabled = currentSongId != null
            ) {
                Icon(Icons.Rounded.Share, stringResource(R.string.share_current_song))
            }
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.message_hint)) },
                singleLine = true
            )
            FilledIconButton(onClick = onSend, enabled = state.draft.isNotBlank()) {
                Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.send_message))
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    mine: Boolean,
    otherUserAvatarUrl: String?,
    onPlaySong: (Song) -> Unit
) {
    val sharedSong = message.toSharedSong()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            Surface(
                modifier = Modifier
                    .padding(end = AppDimens.spaceSmall)
                    .size(AppDimens.profileAvatarSize),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                AsyncImage(
                    model = otherUserAvatarUrl,
                    contentDescription = stringResource(R.string.profile_avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(ChatVisuals.messageWidthFraction)
                .clip(MaterialTheme.shapes.large)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer
                )
                .padding(AppDimens.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
        ) {
            if (sharedSong != null) {
                SharedSongMiniCard(
                    song = sharedSong,
                    onPlay = { onPlaySong(sharedSong) }
                )
            } else if (message.messageType == "song") {
                Text(
                    stringResource(R.string.shared_song),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (message.body.isNotBlank()) {
                Text(message.body, style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceExtraSmall)
            ) {
                Text(
                    formatMessageTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mine) {
                    Icon(
                        when (message.status) {
                            "read" -> Icons.Rounded.DoneAll
                            "delivered" -> Icons.Rounded.DoneAll
                            "sent" -> Icons.Rounded.Done
                            else -> Icons.Rounded.Schedule
                        },
                        contentDescription = stringResource(R.string.message_status),
                        tint = if (message.status == "read") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(AppDimens.actionIconSize)
                    )
                }
            }
        }
    }
}

@Composable
fun SharedSongMiniCard(
    song: Song,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = ChatVisuals.songCardSurfaceAlpha
        ),
        tonalElevation = AppDimens.cardElevation
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
        ) {
            AsyncImage(
                model = song.coverImageUrl,
                contentDescription = stringResource(R.string.song_cover, song.title),
                modifier = Modifier
                    .size(AppDimens.miniPlayerArtworkSize)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.shared_song_card_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    song.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(AppDimens.spaceSmall)
                )
            }
        }
    }
}

private fun ChatMessageEntity.toSharedSong(): Song? {
    val id = songId ?: return null
    val audioUrl = songAudioUrl ?: return null
    return Song(
        id = id,
        title = songTitle.orEmpty(),
        artistName = songArtist.orEmpty(),
        coverImageUrl = songCoverUrl.orEmpty(),
        audioUrl = audioUrl,
        duration = songDuration
    )
}

private fun formatMessageTime(createdAt: String): String {
    val parsed = CHAT_TIME_PATTERNS.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(createdAt)
        }.getOrNull()
    }
    return parsed?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
    } ?: createdAt
}

private val CHAT_TIME_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss'Z'"
)
