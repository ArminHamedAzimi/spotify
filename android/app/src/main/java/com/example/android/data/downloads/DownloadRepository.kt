package com.example.android.data.downloads

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.android.domain.downloads.DownloadedSong
import com.example.android.domain.home.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Offline downloads store used by playback resolve and the Downloads tab. */
class DownloadRepository(
    @Suppress("UNUSED_PARAMETER") context: Context
) {
    private val _songs = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val songs: StateFlow<List<DownloadedSong>> = _songs.asStateFlow()

    fun resolve(song: Song, @Suppress("UNUSED_PARAMETER") userId: String?): Song = song

    fun isDownloaded(userId: String?, songId: String?): Boolean =
        !userId.isNullOrBlank() &&
            !songId.isNullOrBlank() &&
            _songs.value.any { it.id == songId }

    fun pagedSongs(@Suppress("UNUSED_PARAMETER") userId: String?): Flow<PagingData<DownloadedSong>> =
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                object : PagingSource<Int, DownloadedSong>() {
                    override fun getRefreshKey(state: PagingState<Int, DownloadedSong>): Int? = null

                    override suspend fun load(
                        params: LoadParams<Int>
                    ): LoadResult<Int, DownloadedSong> =
                        LoadResult.Page(_songs.value, prevKey = null, nextKey = null)
                }
            }
        ).flow

    fun remove(@Suppress("UNUSED_PARAMETER") userId: String?, songId: String) {
        _songs.value = _songs.value.filterNot { it.id == songId }
    }

    fun refresh(@Suppress("UNUSED_PARAMETER") userId: String? = null) = Unit
}
