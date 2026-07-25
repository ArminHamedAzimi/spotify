package com.example.android.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.android.R
import com.example.android.data.downloads.DownloadRepository
import com.example.android.domain.downloads.DownloadedSong
import com.example.android.playback.PlaybackUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

data class DownloadsUiState(
    val songs: List<DownloadedSong> = emptyList(),
    val activeDownloadIds: Set<String> = emptySet(),
    val messageRes: Int? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val downloads: DownloadRepository
) : ViewModel() {
    private val activeUserId = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val pagedSongs: Flow<PagingData<DownloadedSong>> = activeUserId
        .flatMapLatest(downloads::pagedSongs)
        .cachedIn(viewModelScope)

    fun setActiveUser(userId: String?) {
        activeUserId.value = userId
        downloads.refresh(userId)
        _uiState.update { it.copy(songs = downloads.songs.value) }
    }

    fun removeDownload(songId: String) {
        downloads.remove(activeUserId.value, songId)
        _uiState.update {
            it.copy(
                songs = downloads.songs.value,
                messageRes = R.string.download_removed
            )
        }
    }

    fun download(
        @Suppress("UNUSED_PARAMETER") playback: PlaybackUiState,
        @Suppress("UNUSED_PARAMETER") userId: String?,
        isPremium: Boolean
    ) {
        _uiState.update {
            it.copy(
                messageRes = if (isPremium) {
                    R.string.download_failed
                } else {
                    R.string.download_premium_required
                }
            )
        }
    }
}
