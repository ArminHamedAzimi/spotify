package com.example.android.data.search

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.android.data.playlists.toDomain
import com.example.android.data.remote.PublicProfileDto
import com.example.android.data.remote.RefreshRequest
import com.example.android.data.remote.SpotifyApi
import com.example.android.data.session.TokenStore
import com.example.android.domain.home.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class SearchRepository(
    private val tokenStore: TokenStore,
    private val api: SpotifyApi,
    private val historyDao: SearchHistoryDao
) {
    val history = historyDao.observeRecent(HISTORY_LIMIT)

    fun profiles(query: String): Flow<PagingData<PublicProfileDto>> =
        pager { page, size ->
            authenticated { api.searchUsers(it, query, page, size) }
                .let { SearchPage(it.results, it.next != null) }
        }

    fun songs(query: String): Flow<PagingData<Song>> =
        pager { page, size ->
            authenticated { api.searchSongs(it, query, page, size) }
                .let { SearchPage(it.results.map { song -> song.toDomain() }, it.next != null) }
        }

    suspend fun saveHistory(query: String, type: String) {
        withContext(Dispatchers.IO) {
            historyDao.save(
                SearchHistoryEntity(
                    query.trim(),
                    type,
                    System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeHistory(item: SearchHistoryEntity) = withContext(Dispatchers.IO) {
        historyDao.remove(item)
    }

    private fun <T : Any> pager(
        loadPage: suspend (Int, Int) -> SearchPage<T>
    ): Flow<PagingData<T>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { SearchPagingSource(loadPage) }
    ).flow

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val access = requireNotNull(tokenStore.accessToken)
        return try {
            block("Bearer $access")
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            val refresh = requireNotNull(tokenStore.refreshToken)
            val token = api.refresh(RefreshRequest(refresh))
            tokenStore.save(token.access)
            block("Bearer ${token.access}")
        }
    }

    private companion object {
        const val PAGE_SIZE = 10
        const val PREFETCH_DISTANCE = 3
        const val HISTORY_LIMIT = 20
    }
}

private class SearchPagingSource<T : Any>(
    private val loadPage: suspend (Int, Int) -> SearchPage<T>
) : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: FIRST_PAGE
        return runCatching {
            val response = loadPage(page, params.loadSize)
            LoadResult.Page(
                data = response.results,
                prevKey = if (page == FIRST_PAGE) null else page - 1,
                nextKey = if (response.hasNext) page + 1 else null
            )
        }.getOrElse { LoadResult.Error(it) }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let(state::closestPageToPosition)?.let {
            it.prevKey?.plus(1) ?: it.nextKey?.minus(1)
        }
}

private data class SearchPage<T>(val results: List<T>, val hasNext: Boolean)
private const val FIRST_PAGE = 1
