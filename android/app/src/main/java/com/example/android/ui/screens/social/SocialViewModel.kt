package com.example.android.ui.screens.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.android.data.remote.PublicProfileDto
import com.example.android.data.social.ConnectionType
import com.example.android.data.social.SocialRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SocialUiState(
    val selectedProfile: PublicProfileDto? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val followedUserIds: Set<String> = emptySet(),
    val currentUserId: String? = null,
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class SocialViewModel(private val repository: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(SocialUiState())
    val state = _state.asStateFlow()
    private val selectedId = MutableStateFlow<String?>(null)
    private val connectionRequest = MutableStateFlow<Pair<String, ConnectionType>?>(null)

    val publicPlaylists = selectedId.filterNotNull()
        .flatMapLatest(repository::publicPlaylists).cachedIn(viewModelScope)
    val connections = connectionRequest.filterNotNull()
        .flatMapLatest { repository.connections(it.first, it.second) }.cachedIn(viewModelScope)

    fun setCurrentUser(userId: String?) {
        if (_state.value.currentUserId == userId) return
        _state.update { SocialUiState(currentUserId = userId) }
        if (userId != null) viewModelScope.launch {
            runCatching { repository.allFollowing(userId) }.onSuccess { ids ->
                _state.update { it.copy(followedUserIds = ids) }
            }
        }
    }

    fun openProfile(profile: PublicProfileDto) {
        selectedId.value = profile.id
        _state.update { it.copy(selectedProfile = profile, isLoading = true) }
        viewModelScope.launch {
            runCatching { repository.counts(profile.id) }.onSuccess { counts ->
                _state.update {
                    it.copy(
                        followerCount = counts.first,
                        followingCount = counts.second,
                        isLoading = false
                    )
                }
            }.onFailure { _state.update { it.copy(isLoading = false) } }
        }
    }

    fun showConnections(userId: String, type: ConnectionType) {
        connectionRequest.value = userId to type
    }

    fun toggleFollow(userId: String) = viewModelScope.launch {
        val followed = userId in _state.value.followedUserIds
        runCatching {
            if (followed) repository.unfollow(userId) else repository.follow(userId)
        }.onSuccess {
            _state.update {
                it.copy(
                    followedUserIds = if (followed) {
                        it.followedUserIds - userId
                    } else {
                        it.followedUserIds + userId
                    },
                    followerCount = if (it.selectedProfile?.id == userId) {
                        (it.followerCount + if (followed) -1 else 1).coerceAtLeast(0)
                    } else {
                        it.followerCount
                    }
                )
            }
        }
    }
}
