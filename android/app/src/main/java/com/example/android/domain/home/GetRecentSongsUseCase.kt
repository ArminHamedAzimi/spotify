package com.example.android.domain.home

class GetRecentSongsUseCase(private val repository: HomeRepository) {
    suspend operator fun invoke(): List<Song> = repository.getRecentSongs()
}
