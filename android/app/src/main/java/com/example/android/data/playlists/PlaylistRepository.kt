package com.example.android.data.playlists

import com.example.android.data.remote.*
import com.example.android.data.session.TokenStore
import com.example.android.domain.home.Song
import retrofit2.HttpException

class PlaylistRepository(
    private val tokenStore: TokenStore,
    private val api: SpotifyApi
) {
    suspend fun mine() = authenticated(api::myPlaylists)
    suspend fun create(title: String) = authenticated {
        api.createPlaylist(it, CreatePlaylistRequest(title.trim()))
    }
    suspend fun addSong(playlistId: String, songId: String) = authenticated {
        api.addSongToPlaylist(it, playlistId, PlaylistSongRequest(songId))
    }
    suspend fun removeSong(playlistId: String, songId: String) = authenticated {
        api.removeSongFromPlaylist(it, playlistId, songId)
    }
    suspend fun details(playlistId: String): Pair<PlaylistDto, List<Song>> = authenticated { auth ->
        val playlist = api.playlist(auth, playlistId)
        playlist to playlist.songs.map { api.song(auth, it).toDomain() }
    }
    suspend fun playlistNext(playlistId: String, songId: String?, shuffle: Boolean) =
        authenticated { api.playlistNextSong(it, playlistId, NextSongRequest(songId, shuffle)).toDomain() }
    suspend fun randomNext(songId: String?) =
        authenticated { api.randomNextSong(it, RandomNextRequest(songId)).toDomain() }

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
}

fun SongDto.toDomain() = Song(id, title, artist.name, coverImageUrl, audioUrl, duration)
