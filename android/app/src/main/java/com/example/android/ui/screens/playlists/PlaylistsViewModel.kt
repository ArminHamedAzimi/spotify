package com.example.android.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.playlists.PlaylistRepository
import com.example.android.data.remote.PlaylistDto
import com.example.android.domain.home.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val playlists: List<PlaylistDto> = emptyList(),
    val selected: PlaylistDto? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: Boolean = false
)

class PlaylistsViewModel(private val repository: PlaylistRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistsUiState())
    val state = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = false) }
        runCatching { repository.mine() }.fold(
            { _state.update { state -> state.copy(playlists = it, isLoading = false) } },
            { _state.update { state -> state.copy(isLoading = false, error = true) } }
        )
    }
    fun load(id: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, songs = emptyList()) }
        runCatching { repository.details(id) }.fold(
            { (playlist, songs) -> _state.update { it.copy(selected = playlist, songs = songs, isLoading = false) } },
            { _state.update { it.copy(isLoading = false, error = true) } }
        )
    }
    fun create(title: String, onCreated: () -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.create(title) }.onSuccess { refresh(); onCreated() }
    }
    fun addSong(playlistId: String, songId: String) = viewModelScope.launch {
        runCatching { repository.addSong(playlistId, songId) }
            .onSuccess { refresh() }
    }

    fun removeSong(playlistId: String, songId: String) = viewModelScope.launch {
        _state.update { state ->
            state.copy(
                songs = state.songs.filterNot { it.id == songId },
                selected = state.selected?.copy(
                    songs = state.selected.songs.filterNot { it == songId }
                )
            )
        }
        runCatching { repository.removeSong(playlistId, songId) }
            .onSuccess { refresh() }
            .onFailure { load(playlistId) }
    }
}
