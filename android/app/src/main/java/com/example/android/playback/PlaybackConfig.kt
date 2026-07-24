package com.example.android.playback

object PlaybackConfig {
    const val cacheSizeBytes = 256L * 1024L * 1024L
    const val fadeDurationMillis = 350L
    const val progressUpdateMillis = 500L
    const val fullVolume = 1f
    const val silentVolume = 0f
    const val millisPerMinute = 60_000L
    val playbackSpeeds = listOf(1f, 1.5f, 2f)
    val sleepTimerMinutes = listOf(15, 30, 45, 60)
}
