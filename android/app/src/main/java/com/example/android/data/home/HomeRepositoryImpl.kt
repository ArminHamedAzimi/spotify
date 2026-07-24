package com.example.android.data.home

import com.example.android.data.remote.RefreshRequest
import com.example.android.data.remote.SongDto
import com.example.android.data.remote.SpotifyApi
import com.example.android.data.session.TokenStore
import com.example.android.domain.home.HomeRepository
import com.example.android.domain.home.Song
import com.example.android.domain.home.PopularSong
import com.example.android.domain.home.TopArtist
import retrofit2.HttpException

class HomeRepositoryImpl(
    private val api: SpotifyApi,
    private val tokenStore: TokenStore
) : HomeRepository {
    override suspend fun getRecentSongs(): List<Song> {
        val songs = authenticated(api::recentSongs)
        return songs.map { it.toDomain() }
    }

    override suspend fun getPopularSongs(): List<PopularSong> =
        authenticated(api::popularSongs).map {
            PopularSong(
                Song(
                    it.id, it.title, it.artist.name, it.coverImageUrl,
                    it.audioUrl, it.duration
                ),
                it.likeCount
            )
        }

    override suspend fun getTopArtists(): List<TopArtist> =
        authenticated(api::topArtists).map {
            TopArtist(
                profile = it.artist,
                followerCount = it.followerCount,
                sampleSong = Song(
                    it.song.id, it.song.title, it.song.artist.name,
                    it.song.coverImageUrl, it.song.audioUrl, it.song.duration
                )
            )
        }

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val accessToken = tokenStore.accessToken ?: throw AuthenticationRequiredException()
        return try {
            block(accessToken.asBearer())
        } catch (error: HttpException) {
            if (error.code() != UNAUTHORIZED) throw error
            val refreshToken = tokenStore.refreshToken ?: throw AuthenticationRequiredException()
            val refreshed = api.refresh(RefreshRequest(refreshToken))
            tokenStore.save(refreshed.access)
            block(refreshed.access.asBearer())
        }
    }

    private fun SongDto.toDomain() = Song(
        id = id,
        title = title,
        artistName = artist.name,
        coverImageUrl = coverImageUrl,
        audioUrl = audioUrl,
        duration = duration
    )

    private fun String.asBearer() = "Bearer $this"

    private companion object {
        const val UNAUTHORIZED = 401
    }
}

class AuthenticationRequiredException : Exception()
