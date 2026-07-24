package com.example.android.ui.screens.player

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.android.R
import com.example.android.playback.PlaybackConfig
import com.example.android.playback.PlaybackUiState
import com.example.android.ui.theme.AppDimens
import com.example.android.ui.theme.PlayerVisuals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

@Composable
fun PlayerScreen(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onSleepTimerChange: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val fallbackPrimary = MaterialTheme.colorScheme.primaryContainer
    val context = LocalContext.current
    var dominantArtworkColor by remember(state.artworkUrl) {
        mutableStateOf(fallbackPrimary)
    }
    val animatedArtworkColor by animateColorAsState(
        targetValue = dominantArtworkColor,
        animationSpec = tween(PlayerVisuals.gradientAnimationDurationMillis),
        label = "dominantArtworkColor"
    )
    val swipeThreshold = with(LocalDensity.current) {
        AppDimens.playerSwipeThreshold.toPx()
    }
    val dismissConnection = remember(onDismiss, swipeThreshold) {
        object : NestedScrollConnection {
            private var downwardDrag = 0f
            private var dismissed = false

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    downwardDrag += available.y
                    if (!dismissed && downwardDrag >= swipeThreshold) {
                        dismissed = true
                        onDismiss()
                    }
                } else if (available.y < 0f) {
                    downwardDrag = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                downwardDrag = 0f
                dismissed = false
                return Velocity.Zero
            }
        }
    }
    var showSleepTimer by remember { mutableStateOf(false) }
    LaunchedEffect(state.artworkUrl) {
        val artworkUrl = state.artworkUrl ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(artworkUrl)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult
            ?: return@LaunchedEffect
        dominantArtworkColor = withContext(Dispatchers.Default) {
            runCatching {
                val bitmap = result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
                val palette = Palette.from(bitmap).generate()
                Color(
                    palette.dominantSwatch?.rgb
                        ?: fallbackPrimary.toArgb()
                )
            }.getOrDefault(fallbackPrimary)
        }
    }
    if (showSleepTimer) {
        SleepTimerSheet(
            selectedMinutes = state.sleepTimerMinutes,
            onDismiss = { showSleepTimer = false },
            onSelected = {
                onSleepTimerChange(it)
                showSleepTimer = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        animatedArtworkColor.copy(
                            alpha = PlayerVisuals.gradientStrongAlpha
                        ),
                        animatedArtworkColor.copy(
                            alpha = PlayerVisuals.gradientMediumAlpha
                        ),
                        animatedArtworkColor.copy(
                            alpha = PlayerVisuals.gradientSoftAlpha
                        ),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .nestedScroll(dismissConnection)
            .verticalScroll(rememberScrollState())
            .padding(AppDimens.spaceLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceLarge)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back)
                )
            }
            Text(
                text = stringResource(R.string.player),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        RotatingDisc(
            state = state
        )
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
        AudioVisualizer(isPlaying = state.isPlaying)
        PlaybackProgress(state = state, onSeek = onSeek)
        PlaybackControls(state = state, onTogglePlayPause = onTogglePlayPause)
        PlayerOptions(
            playbackSpeed = state.playbackSpeed,
            sleepTimerMinutes = state.sleepTimerMinutes,
            onPlaybackSpeedChange = onPlaybackSpeedChange,
            onOpenSleepTimer = { showSleepTimer = true }
        )
    }
}

@Composable
private fun RotatingDisc(
    state: PlaybackUiState
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state.mediaId) { rotation.snapTo(0f) }
    LaunchedEffect(state.isPlaying, state.mediaId) {
        while (state.isPlaying) {
            rotation.animateTo(
                targetValue = PlayerVisuals.discRotationDegrees,
                animationSpec = tween(
                    durationMillis = PlayerVisuals.discRotationDurationMillis,
                    easing = LinearEasing
                )
            )
            rotation.snapTo(0f)
        }
    }
    Surface(
        modifier = Modifier
            .size(AppDimens.playerArtworkSize)
            .graphicsLayer { rotationZ = rotation.value },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.scrim,
        tonalElevation = AppDimens.cardElevation
    ) {
        Box(contentAlignment = Alignment.Center) {
            SubcomposeAsyncImage(
                model = state.artworkUrl,
                contentDescription = stringResource(R.string.song_cover, state.title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AppDimens.playerDiscCoverSize)
                    .graphicsLayer { shape = CircleShape; clip = true }
            ) {
                val success = painter.state as? coil.compose.AsyncImagePainter.State.Success
                if (success != null) {
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
            Surface(
                modifier = Modifier.size(AppDimens.playerDiscHoleSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = AppDimens.cardElevation
            ) {}
        }
    }
}

@Composable
private fun AudioVisualizer(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "audioVisualizer")
    val animatedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = PlayerVisuals.fullTurnRadians,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PlayerVisuals.visualizerDurationMillis,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "audioVisualizerPhase"
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.playerVisualizerHeight)
    ) {
        val phase = if (isPlaying) animatedPhase else 0f
        val slotWidth = size.width / PlayerVisuals.visualizerBarCount
        val barWidth = slotWidth * PlayerVisuals.visualizerBarWidthFraction
        repeat(PlayerVisuals.visualizerBarCount) { index ->
            val wave = (sin(
                phase + index * PlayerVisuals.visualizerPhaseOffset
            ) + sin(
                phase * PlayerVisuals.visualizerWaveOffset + index
            ) + 2f) / 4f
            val barHeight = size.height * (
                PlayerVisuals.visualizerMinimumHeightFraction +
                    wave * PlayerVisuals.visualizerHeightRangeFraction
                )
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = index * slotWidth + (slotWidth - barWidth) / 2f,
                    y = (size.height - barHeight) / 2f
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
private fun PlaybackProgress(state: PlaybackUiState, onSeek: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val bufferedProgress = if (state.durationMillis > 0L) {
            state.bufferedPositionMillis.toFloat() / state.durationMillis.toFloat()
        } else {
            0f
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { bufferedProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimens.playerBufferedTrackHeight),
                color = MaterialTheme.colorScheme.primary.copy(
                    alpha = PlayerVisuals.bufferedTrackAlpha
                ),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Slider(
                value = state.positionMillis.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..state.durationMillis.coerceAtLeast(1L).toFloat(),
                enabled = state.durationMillis > 0L,
                colors = SliderDefaults.colors(
                    inactiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent
                )
            )
        }
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
}

@Composable
private fun PlaybackControls(state: PlaybackUiState, onTogglePlayPause: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.size(AppDimens.playerSecondaryControlSize)
        ) {
            Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous_song))
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
                    stringResource(if (state.isPlaying) R.string.pause else R.string.play)
                )
            }
        }
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.size(AppDimens.playerSecondaryControlSize)
        ) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.next_song))
        }
    }
}

@Composable
private fun PlayerOptions(
    playbackSpeed: Float,
    sleepTimerMinutes: Int?,
    onPlaybackSpeedChange: (Float) -> Unit,
    onOpenSleepTimer: () -> Unit
) {
    HorizontalDivider()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
    ) {
        Text(
            text = stringResource(R.string.playback_speed),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSmall)
        ) {
            PlaybackConfig.playbackSpeeds.forEach { speed ->
                FilterChip(
                    selected = playbackSpeed == speed,
                    onClick = { onPlaybackSpeedChange(speed) },
                    label = { Text(stringResource(speed.labelResource())) }
                )
            }
        }
        AssistChip(
            onClick = onOpenSleepTimer,
            leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
            label = {
                Text(
                    sleepTimerMinutes?.let {
                        stringResource(R.string.sleep_timer_active, it)
                    } ?: stringResource(R.string.sleep_timer)
                )
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SleepTimerSheet(
    selectedMinutes: Int?,
    onDismiss: () -> Unit,
    onSelected: (Int?) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.spaceLarge),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMedium)
        ) {
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.sleep_timer_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilterChip(
                selected = selectedMinutes == null,
                onClick = { onSelected(null) },
                label = { Text(stringResource(R.string.sleep_timer_off)) }
            )
            PlaybackConfig.sleepTimerMinutes.forEach { minutes ->
                FilterChip(
                    selected = selectedMinutes == minutes,
                    onClick = { onSelected(minutes) },
                    label = { Text(stringResource(R.string.sleep_timer_minutes, minutes)) }
                )
            }
        }
    }
}

private fun Float.labelResource(): Int = when (this) {
    1.5f -> R.string.speed_one_half
    2f -> R.string.speed_two
    else -> R.string.speed_one
}

private const val MILLIS_PER_SECOND = 1_000L
