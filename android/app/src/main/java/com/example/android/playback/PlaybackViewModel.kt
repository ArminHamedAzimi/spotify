package com.example.android.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.android.domain.home.Song
import kotlinx.coroutines.flow.StateFlow

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
    val sleepTimerMinutes: Int? = null,
    val isShuffleEnabled: Boolean = false
) {
    val hasMedia: Boolean get() = mediaId != null
}

/**
 * UI-facing façade over the process-scoped [PlaybackController].
 * Keeps Compose / ViewModel wiring unchanged while notification actions stay alive.
 */
class PlaybackViewModel(
    application: Application,
    private val playback: PlaybackController
) : AndroidViewModel(application) {
    val uiState: StateFlow<PlaybackUiState> = playback.uiState
    val recentlyPlayed: StateFlow<List<Song>> = playback.recentlyPlayed

    fun play(song: Song) = playback.play(song)

    fun playFromPlaylist(song: Song, playlistId: String, shuffle: Boolean) =
        playback.playFromPlaylist(song, playlistId, shuffle)

    fun startPlaylist(playlistId: String, shuffle: Boolean) =
        playback.startPlaylist(playlistId, shuffle)

    fun playFromDownloads(song: Song, songs: List<Song>) =
        playback.playFromDownloads(song, songs)

    fun next() = playback.next()

    fun previous() = playback.previous()

    fun setShuffle(enabled: Boolean) = playback.setShuffle(enabled)

    fun setActiveUser(userId: String?) = playback.setActiveUser(userId)

    fun togglePlayPause() = playback.togglePlayPause()

    fun seekTo(positionMillis: Long) = playback.seekTo(positionMillis)

    fun setPlaybackSpeed(speed: Float) = playback.setPlaybackSpeed(speed)

    fun setSleepTimer(minutes: Int?) = playback.setSleepTimer(minutes)
}
