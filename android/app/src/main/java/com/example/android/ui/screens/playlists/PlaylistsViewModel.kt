package com.example.android.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.android.data.playlists.PlaylistRepository
import com.example.android.data.remote.PlaylistDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val selected: PlaylistDto? = null,
    val isLoading: Boolean = false,
    val error: Boolean = false,
    val addedMemberships: Set<Pair<String, String>> = emptySet(),
    val loadedMemberships: Set<Pair<String, String>> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModel(private val repository: PlaylistRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistsUiState())
    val state = _state.asStateFlow()
    private val selectedPlaylistId = MutableStateFlow<String?>(null)
    private val loadingMemberships = mutableSetOf<Pair<String, String>>()

    val playlists = repository.pagedPlaylists().cachedIn(viewModelScope)
    val songs = selectedPlaylistId
        .filterNotNull()
        .flatMapLatest(repository::pagedPlaylistSongs)
        .cachedIn(viewModelScope)

    fun refresh() = repository.invalidatePlaylists()

    fun load(id: String) {
        selectedPlaylistId.value = id
        _state.update { it.copy(isLoading = true, error = false) }
        viewModelScope.launch {
            runCatching { repository.playlist(id) }.fold(
                { playlist ->
                    _state.update { it.copy(selected = playlist, isLoading = false) }
                },
                { _state.update { it.copy(isLoading = false, error = true) } }
            )
        }
    }

    fun create(title: String, onCreated: () -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.create(title) }.onSuccess {
            repository.invalidatePlaylists()
            onCreated()
        }
    }

    fun loadMemberships(songId: String, playlistIds: Collection<String>) {
        playlistIds.forEach { playlistId ->
            val membership = playlistId to songId
            if (membership in _state.value.loadedMemberships ||
                !loadingMemberships.add(membership)
            ) {
                return@forEach
            }
            viewModelScope.launch {
                runCatching {
                    repository.playlistContainsSong(playlistId, songId)
                }.onSuccess { containsSong ->
                    _state.update { current ->
                        current.copy(
                            addedMemberships = if (containsSong) {
                                current.addedMemberships + membership
                            } else {
                                current.addedMemberships - membership
                            },
                            loadedMemberships = current.loadedMemberships + membership
                        )
                    }
                }
                loadingMemberships.remove(membership)
            }
        }
    }

    fun addSong(playlistId: String, songId: String) = viewModelScope.launch {
        runCatching { repository.addSong(playlistId, songId) }.onSuccess {
            _state.update {
                val membership = playlistId to songId
                it.copy(
                    addedMemberships = it.addedMemberships + membership,
                    loadedMemberships = it.loadedMemberships + membership
                )
            }
            repository.invalidatePlaylists()
            repository.invalidateSongs()
        }
    }

    fun removeSong(playlistId: String, songId: String) = viewModelScope.launch {
        runCatching { repository.removeSong(playlistId, songId) }.fold(
            {
                _state.update {
                    val membership = playlistId to songId
                    it.copy(
                        addedMemberships = it.addedMemberships - membership,
                        loadedMemberships = it.loadedMemberships + membership
                    )
                }
                repository.invalidatePlaylists()
                repository.invalidateSongs()
            },
            { repository.invalidateSongs() }
        )
    }

    fun updateVisibility(playlistId: String, isPublic: Boolean) = viewModelScope.launch {
        runCatching { repository.updateVisibility(playlistId, isPublic) }.onSuccess { playlist ->
            _state.update { it.copy(selected = playlist) }
            repository.invalidatePlaylists()
        }
    }

    fun openLikedPlaylist(onFound: (String) -> Unit) = viewModelScope.launch {
        repository.likedPlaylistId()?.let(onFound)
    }
}
