package com.example.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.android.R
import com.example.android.playback.PlaybackUiState
import com.example.android.ui.theme.AppDimens

@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val swipeThreshold = with(LocalDensity.current) {
        AppDimens.playerSwipeThreshold.toPx()
    }
    Surface(
        modifier = Modifier.pointerInput(onOpenPlayer, swipeThreshold) {
            var accumulatedDrag = 0f
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount
                },
                onDragEnd = {
                    if (accumulatedDrag <= -swipeThreshold) onOpenPlayer()
                    accumulatedDrag = 0f
                },
                onDragCancel = { accumulatedDrag = 0f }
            )
        },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = AppDimens.cardElevation
    ) {
        Column {
            val progress = if (state.durationMillis > 0L) {
                state.positionMillis.toFloat() / state.durationMillis.toFloat()
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimens.miniPlayerHeight)
                    .clickable(onClick = onOpenPlayer)
                    .padding(horizontal = AppDimens.spaceMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
            ) {
                SubcomposeAsyncImage(
                    model = state.artworkUrl,
                    contentDescription = stringResource(R.string.song_cover, state.title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AppDimens.miniPlayerArtworkSize)
                        .clip(CircleShape)
                ) {
                    if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
                        SubcomposeAsyncImageContent()
                    } else {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Text(
                        text = state.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.previous_song)
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.pause else R.string.play
                        )
                    )
                }
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.next_song)
                    )
                }
            }
        }
    }
}
