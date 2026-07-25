package com.example.android.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.android.MainActivity
import com.example.android.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.koin.android.ext.android.getKoin

/**
 * Background media playback with a system notification and lock-screen controls
 * (Play / Pause / Next), including headset and System UI media actions.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var cache: SimpleCache? = null

    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(PLAYBACK_NOTIFICATION_ID)
            .setChannelId(PLAYBACK_CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification_music)
        setMediaNotificationProvider(notificationProvider)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)

        val playbackCache = SimpleCache(
            cacheDir.resolve(CACHE_DIRECTORY),
            LeastRecentlyUsedCacheEvictor(PlaybackConfig.cacheSizeBytes),
            StandaloneDatabaseProvider(this)
        )
        cache = playbackCache
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(playbackCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        val player = NotificationAwarePlayer(exoPlayer) { playbackController() }

        val sessionActivity = PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader(this)))
            .setCallback(sessionCallback)
            .setMediaButtonPreferences(listOf(previousButton, nextButton))
            .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        cache?.release()
        cache = null
        super.onDestroy()
    }

    private fun playbackController(): PlaybackController = getKoin().get()

    private val nextCommand = SessionCommand(COMMAND_NEXT, Bundle.EMPTY)
    private val previousCommand = SessionCommand(COMMAND_PREVIOUS, Bundle.EMPTY)
    private val stopCommand = SessionCommand(COMMAND_STOP, Bundle.EMPTY)

    private val nextButton by lazy {
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setDisplayName(getString(R.string.next_song))
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()
    }
    private val previousButton by lazy {
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .setDisplayName(getString(R.string.previous_song))
            .setSlots(CommandButton.SLOT_BACK)
            .build()
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(previousCommand)
                    .add(nextCommand)
                    .add(stopCommand)
                    .build()
            val playerCommands =
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setMediaButtonPreferences(listOf(previousButton, nextButton))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_PREVIOUS -> playbackController().previous()
                COMMAND_NEXT -> playbackController().next()
                COMMAND_STOP -> {
                    session.player.stop()
                    session.player.clearMediaItems()
                    PlaybackCommandBus.requestStop()
                }
                else -> return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "media"
        const val SESSION_ACTIVITY_REQUEST_CODE = 100
        const val COMMAND_NEXT = "com.example.android.playback.NEXT"
        const val COMMAND_PREVIOUS = "com.example.android.playback.PREVIOUS"
        const val COMMAND_STOP = "com.example.android.playback.STOP"
        const val PLAYBACK_NOTIFICATION_ID = 2001
        const val PLAYBACK_CHANNEL_ID = "media_playback"
    }
}

/**
 * Exposes Next/Previous as standard player commands so System UI, lock screen,
 * and headset media keys all route through [PlaybackController].
 */
@OptIn(UnstableApi::class)
private class NotificationAwarePlayer(
    private val player: ExoPlayer,
    private val playbackController: () -> PlaybackController
) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_PREVIOUS)
            .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: @Player.Command Int): Boolean =
        when (command) {
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }

    override fun seekToNext() {
        playbackController().next()
    }

    override fun seekToNextMediaItem() {
        seekToNext()
    }

    override fun seekToPrevious() {
        playbackController().previous()
    }

    override fun seekToPreviousMediaItem() {
        seekToPrevious()
    }

    override fun hasNextMediaItem(): Boolean = playbackController().hasNext()

    override fun hasPreviousMediaItem(): Boolean = playbackController().hasPrevious()

    override fun release() {
        player.release()
    }
}
