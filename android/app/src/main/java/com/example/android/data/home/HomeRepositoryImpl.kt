package com.example.android.data.home

import com.example.android.data.remote.RefreshRequest
import com.example.android.data.remote.SongDto
import com.example.android.data.remote.SpotifyApi
import com.example.android.data.session.TokenStore
import com.example.android.domain.home.HomeRepository
import com.example.android.domain.home.Song
import retrofit2.HttpException

class HomeRepositoryImpl(
    private val api: SpotifyApi,
    private val tokenStore: TokenStore
) : HomeRepository {
    override suspend fun getRecentSongs(): List<Song> {
        val accessToken = tokenStore.accessToken ?: throw AuthenticationRequiredException()
        val songs = try {
            api.recentSongs(accessToken.asBearer())
        } catch (error: HttpException) {
            if (error.code() != UNAUTHORIZED) throw error
            val refreshToken = tokenStore.refreshToken ?: throw AuthenticationRequiredException()
            val refreshed = api.refresh(RefreshRequest(refreshToken))
            tokenStore.save(refreshed.access)
            api.recentSongs(refreshed.access.asBearer())
        }
        return songs.map { it.toDomain() }
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
