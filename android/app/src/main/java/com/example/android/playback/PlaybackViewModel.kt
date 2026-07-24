package com.example.android.playback

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.android.domain.home.Song
import com.example.android.data.downloads.DownloadRepository
import com.example.android.data.playlists.PlaylistRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PlaybackUiState(
    val isConnected: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val audioUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val playbackSpeed: Float = PlaybackConfig.playbackSpeeds.first(),
    val sleepTimerMinutes: Int? = null
) {
    val hasMedia: Boolean get() = mediaId != null
}

class PlaybackViewModel(
    application: Application,
    private val downloads: DownloadRepository,
    private val playlists: PlaylistRepository
) : AndroidViewModel(application) {
    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var selectedSleepTimerMinutes: Int? = null
    private var activeUserId: String? = null
    private var currentSong: Song? = null
    private val history = ArrayDeque<Song>()
    private var source: PlaybackSource = PlaybackSource.General
    private var advancing = false

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
            if (player.playbackState == Player.STATE_ENDED && !advancing) next()
        }
    }

    init {
        val token = SessionToken(
            application,
            ComponentName(application, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(application, token).buildAsync()
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.getOrNull()?.let { connectedController ->
                    controller = connectedController
                    connectedController.addListener(listener)
                    updateState(connectedController)
                    startProgressUpdates()
                }
            },
            ContextCompat.getMainExecutor(application)
        )
    }

    fun play(song: Song) {
        selectSong(song, PlaybackSource.General)
    }

    fun playFromPlaylist(song: Song, playlistId: String, shuffle: Boolean) {
        selectSong(song, PlaybackSource.Playlist(playlistId, shuffle))
    }

    fun startPlaylist(playlistId: String, shuffle: Boolean) {
        viewModelScope.launch {
            runCatching { playlists.playlistNext(playlistId, null, shuffle) }
                .onSuccess { selectSong(it, PlaybackSource.Playlist(playlistId, shuffle)) }
                .onFailure { Log.e(PLAYBACK_LOG_TAG, "Unable to start playlist", it) }
        }
    }

    fun playFromDownloads(song: Song, songs: List<Song>) {
        selectSong(song, PlaybackSource.Downloads(songs))
    }

    private fun selectSong(song: Song, newSource: PlaybackSource, recordHistory: Boolean = true) {
        if (recordHistory) currentSong?.let(history::addLast)
        currentSong = song
        source = newSource
        val activeController = controller ?: return
        val playableSong = downloads.resolve(song, activeUserId)
        viewModelScope.launch {
            try {
                if (
                    activeController.currentMediaItem?.mediaId == playableSong.id &&
                    activeController.currentMediaItem?.localConfiguration?.uri?.toString() ==
                    playableSong.audioUrl
                ) {
                    if (activeController.playbackState == Player.STATE_ENDED) {
                        activeController.seekToDefaultPosition()
                    }
                    activeController.play()
                    return@launch
                }
                if (activeController.currentMediaItem != null) {
                    animateVolume(
                        activeController,
                        activeController.volume,
                        PlaybackConfig.silentVolume
                    )
                }
                activeController.setMediaItem(playableSong.toMediaItem())
                activeController.prepare()
                activeController.volume = PlaybackConfig.silentVolume
                activeController.play()
                animateVolume(
                    activeController,
                    PlaybackConfig.silentVolume,
                    PlaybackConfig.fullVolume
                )
            } catch (error: Exception) {
                activeController.volume = PlaybackConfig.fullVolume
                Log.e(PLAYBACK_LOG_TAG, "Unable to start selected song", error)
                updateState(activeController)
            }
        }
    }

    fun next() {
        val current = currentSong ?: return
        if (advancing) return
        advancing = true
        viewModelScope.launch {
            try {
                val next = when (val activeSource = source) {
                    is PlaybackSource.Playlist -> playlists.playlistNext(
                        activeSource.id, current.id, activeSource.shuffle
                    )
                    is PlaybackSource.Downloads -> {
                        val index = activeSource.songs.indexOfFirst { it.id == current.id }
                        activeSource.songs.getOrNull(index + 1) ?: activeSource.songs.firstOrNull()
                    }
                    PlaybackSource.General -> playlists.randomNext(current.id)
                }
                next?.let { selectSong(it, source) }
            } catch (error: Exception) {
                Log.e(PLAYBACK_LOG_TAG, "Unable to load next song", error)
            } finally {
                advancing = false
            }
        }
    }

    fun previous() {
        val previous = history.removeLastOrNull() ?: return
        selectSong(previous, source, recordHistory = false)
    }

    fun setShuffle(enabled: Boolean) {
        source = when (val active = source) {
            is PlaybackSource.Playlist -> active.copy(shuffle = enabled)
            else -> active
        }
    }

    fun shuffleNext() {
        source = when (val active = source) {
            is PlaybackSource.Playlist -> active.copy(shuffle = true)
            else -> active
        }
        next()
    }

    fun setActiveUser(userId: String?) {
        if (activeUserId != userId) {
            val currentUri = controller?.currentMediaItem
                ?.localConfiguration
                ?.uri
            if (currentUri?.scheme == android.content.ContentResolver.SCHEME_FILE) {
                controller?.stop()
                controller?.clearMediaItems()
            }
        }
        activeUserId = userId
    }

    fun togglePlayPause() {
        controller?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekToDefaultPosition()
                }
                player.play()
            }
        }
    }

    fun seekTo(positionMillis: Long) {
        controller?.seekTo(positionMillis)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (speed !in PlaybackConfig.playbackSpeeds) return
        controller?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun setSleepTimer(minutes: Int?) {
        if (minutes != null && minutes !in PlaybackConfig.sleepTimerMinutes) return
        sleepTimerJob?.cancel()
        selectedSleepTimerMinutes = minutes
        _uiState.update { it.copy(sleepTimerMinutes = minutes) }
        if (minutes == null) return
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * PlaybackConfig.millisPerMinute)
            controller?.pause()
            selectedSleepTimerMinutes = null
            _uiState.update { it.copy(sleepTimerMinutes = null) }
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onCleared()
    }

    private fun Song.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artistName)
                    .setArtworkUri(android.net.Uri.parse(coverImageUrl))
                    .build()
            )
            .build()
    }

    private fun updateState(player: Player) {
        val metadata = player.mediaMetadata
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        _uiState.value = PlaybackUiState(
            isConnected = true,
            mediaId = player.currentMediaItem?.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            artworkUrl = metadata.artworkUri?.toString(),
            audioUrl = player.currentMediaItem?.localConfiguration?.uri?.toString(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMillis = player.currentPosition.coerceAtLeast(0L),
            durationMillis = duration,
            playbackSpeed = player.playbackParameters.speed,
            sleepTimerMinutes = selectedSleepTimerMinutes
        )
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                controller?.let(::updateState)
                delay(PlaybackConfig.progressUpdateMillis)
            }
        }
    }

    private suspend fun animateVolume(
        player: Player,
        from: Float,
        to: Float
    ) = suspendCancellableCoroutine { continuation ->
        val animator = ValueAnimator.ofFloat(from, to).apply {
            duration = PlaybackConfig.fadeDurationMillis
            addUpdateListener { player.volume = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            })
            start()
        }
        continuation.invokeOnCancellation { animator.cancel() }
    }
}

private const val PLAYBACK_LOG_TAG = "PlaybackViewModel"

private sealed interface PlaybackSource {
    data object General : PlaybackSource
    data class Playlist(val id: String, val shuffle: Boolean) : PlaybackSource
    data class Downloads(val songs: List<Song>) : PlaybackSource
}
