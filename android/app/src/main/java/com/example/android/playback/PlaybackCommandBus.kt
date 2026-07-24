package com.example.android.playback

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PlaybackCommandBus {
    private val _commands = MutableSharedFlow<PlaybackCommand>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val commands = _commands.asSharedFlow()

    fun requestNext() {
        _commands.tryEmit(PlaybackCommand.Next)
    }
}

enum class PlaybackCommand { Next }
