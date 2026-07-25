package com.example.android.domain.downloads

import com.example.android.domain.home.Song

data class DownloadedSong(
    val id: String,
    val title: String,
    val artistName: String,
    val coverImageUrl: String,
    val localAudioPath: String,
    val duration: String?
) {
    fun toPlayableSong(): Song = Song(
        id = id,
        title = title,
        artistName = artistName,
        coverImageUrl = coverImageUrl,
        audioUrl = localAudioPath,
        duration = duration
    )
}
