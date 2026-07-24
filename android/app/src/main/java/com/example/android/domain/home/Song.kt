package com.example.android.domain.home

data class Song(
    val id: String,
    val title: String,
    val artistName: String,
    val coverImageUrl: String,
    val audioUrl: String,
    val duration: String?
)
