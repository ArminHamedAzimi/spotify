package com.example.android.ui.screens.player

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import com.example.android.playback.PlaybackUiState
import com.example.android.ui.theme.AppDimens

@Composable
fun PlayerScreen(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppDimens.spaceLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            AppDimens.spaceLarge,
            Alignment.CenterVertically
        )
    ) {
        Surface(
            modifier = Modifier.size(AppDimens.playerArtworkSize),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = AppDimens.cardElevation
        ) {
            SubcomposeAsyncImage(
                model = state.artworkUrl,
                contentDescription = stringResource(R.string.song_cover, state.title),
                contentScale = ContentScale.Crop,
                modifier = Modifier.clip(MaterialTheme.shapes.large)
            ) {
                if (painter.state is coil.compose.AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
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
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
        ) {
            Text(text = state.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = state.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = state.positionMillis.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..state.durationMillis.coerceAtLeast(1L).toFloat(),
                enabled = state.durationMillis > 0L
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = DateUtils.formatElapsedTime(state.positionMillis / MILLIS_PER_SECOND),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = DateUtils.formatElapsedTime(state.durationMillis / MILLIS_PER_SECOND),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.size(AppDimens.playerSecondaryControlSize)
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_song)
                )
            }
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(AppDimens.playerMainControlSize)
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppDimens.actionIconSize),
                        strokeWidth = AppDimens.borderWidth
                    )
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.pause else R.string.play
                        )
                    )
                }
            }
            IconButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.size(AppDimens.playerSecondaryControlSize)
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.next_song)
                )
            }
        }
    }
}

private const val MILLIS_PER_SECOND = 1_000L
