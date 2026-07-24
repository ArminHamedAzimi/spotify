package com.example.android.domain.home

interface HomeRepository {
    suspend fun getRecentSongs(): List<Song>
    suspend fun getPopularSongs(): List<PopularSong>
    suspend fun getTopArtists(): List<TopArtist>
}

data class PopularSong(val song: Song, val likeCount: Int)

data class TopArtist(
    val profile: com.example.android.data.remote.PublicProfileDto,
    val followerCount: Int,
    val sampleSong: Song
)
