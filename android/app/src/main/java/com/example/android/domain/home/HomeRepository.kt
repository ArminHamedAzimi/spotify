package com.example.android.domain.home

interface HomeRepository {
    suspend fun getRecentSongs(): List<Song>
}
