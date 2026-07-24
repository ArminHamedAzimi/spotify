package com.example.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.home.AuthenticationRequiredException
import com.example.android.domain.home.GetRecentSongsUseCase
import com.example.android.domain.home.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentSongs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val requiresAuthentication: Boolean = false,
    val hasError: Boolean = false
)

sealed interface HomeEvent {
    data object Refresh : HomeEvent
}

class HomeViewModel(
    private val getRecentSongs: GetRecentSongsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRecentSongs()
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.Refresh -> loadRecentSongs()
        }
    }

    private fun loadRecentSongs() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, requiresAuthentication = false, hasError = false)
            }
            _uiState.value = try {
                HomeUiState(recentSongs = getRecentSongs(), isLoading = false)
            } catch (_: AuthenticationRequiredException) {
                HomeUiState(isLoading = false, requiresAuthentication = true)
            } catch (_: Exception) {
                HomeUiState(isLoading = false, hasError = true)
            }
        }
    }
}
