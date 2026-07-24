package com.example.android.data.playlists

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.android.data.remote.*
import com.example.android.data.session.TokenStore
import com.example.android.domain.home.Song
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.util.concurrent.CopyOnWriteArraySet

class PlaylistRepository(
    private val tokenStore: TokenStore,
    private val api: SpotifyApi
) {
    private val playlistSources = CopyOnWriteArraySet<PlaylistsPagingSource>()
    private val songSources = CopyOnWriteArraySet<PlaylistSongsPagingSource>()

    fun pagedPlaylists(): Flow<PagingData<PlaylistDto>> = Pager(
        config = pagingConfig(),
        pagingSourceFactory = {
            PlaylistsPagingSource { page, size ->
                authenticated { auth ->
                    api.myPlaylists(auth, page, size).let {
                        PlaylistPage(it.results, it.next != null)
                    }
                }
            }.trackIn(playlistSources)
        }
    ).flow

    fun pagedPlaylistSongs(playlistId: String): Flow<PagingData<Song>> = Pager(
        config = pagingConfig(),
        pagingSourceFactory = {
            PlaylistSongsPagingSource { page, size ->
                authenticated { auth ->
                    api.playlistSongs(auth, playlistId, page, size).let {
                        PlaylistPage(it.results.map(SongDto::toDomain), it.next != null)
                    }
                }
            }.trackIn(songSources)
        }
    ).flow

    suspend fun playlist(playlistId: String) = authenticated {
        api.playlist(it, playlistId)
    }

    suspend fun likedPlaylistId(): String? = authenticated { auth ->
        var page = 1
        do {
            val response = api.myPlaylists(auth, page, MAX_PAGE_SIZE)
            response.results.firstOrNull(PlaylistDto::isLiked)?.let { return@authenticated it.id }
            page++
        } while (response.next != null)
        null
    }
    suspend fun create(title: String) = authenticated {
        api.createPlaylist(it, CreatePlaylistRequest(title.trim()))
    }
    suspend fun addSong(playlistId: String, songId: String) = authenticated {
        api.addSongToPlaylist(it, playlistId, PlaylistSongRequest(songId))
    }

    suspend fun playlistContainsSong(playlistId: String, songId: String): Boolean =
        authenticated { auth ->
            var page = 1
            do {
                val response = api.playlistSongs(auth, playlistId, page, MAX_PAGE_SIZE)
                if (response.results.any { it.id == songId }) return@authenticated true
                page++
            } while (response.next != null)
            false
        }

    suspend fun removeSong(playlistId: String, songId: String) = authenticated {
        api.removeSongFromPlaylist(it, playlistId, songId)
    }
    suspend fun updateVisibility(playlistId: String, isPublic: Boolean) = authenticated {
        api.updatePlaylistVisibility(it, playlistId, PlaylistVisibilityRequest(isPublic))
    }
    suspend fun playlistNext(playlistId: String, songId: String?, shuffle: Boolean) =
        authenticated { api.playlistNextSong(it, playlistId, NextSongRequest(songId, shuffle)).toDomain() }
    suspend fun randomNext(songId: String?) =
        authenticated { api.randomNextSong(it, RandomNextRequest(songId)).toDomain() }

    fun invalidatePlaylists() = playlistSources.toList().forEach(PlaylistsPagingSource::invalidate)
    fun invalidateSongs() = songSources.toList().forEach(PlaylistSongsPagingSource::invalidate)

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val access = requireNotNull(tokenStore.accessToken)
        return try { block("Bearer $access") } catch (error: HttpException) {
            if (error.code() != 401) throw error
            val refresh = requireNotNull(tokenStore.refreshToken)
            val token = api.refresh(RefreshRequest(refresh))
            tokenStore.save(token.access)
            block("Bearer ${token.access}")
        }
    }

    private fun pagingConfig() = PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE,
        prefetchDistance = PREFETCH_DISTANCE,
        enablePlaceholders = false
    )

    private fun <T : androidx.paging.PagingSource<*, *>> T.trackIn(
        sources: MutableSet<T>
    ): T = also { source ->
        sources += source
        registerInvalidatedCallback { sources -= source }
    }

    private companion object {
        const val PAGE_SIZE = 10
        const val PREFETCH_DISTANCE = 3
        const val MAX_PAGE_SIZE = 100
    }
}

fun SongDto.toDomain() = Song(id, title, artist.name, coverImageUrl, audioUrl, duration)
