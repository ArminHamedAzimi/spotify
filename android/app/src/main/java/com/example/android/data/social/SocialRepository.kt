package com.example.android.data.social

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.android.data.remote.*
import com.example.android.data.session.TokenStore
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException

enum class ConnectionType { Followers, Following }

class SocialRepository(
    private val tokenStore: TokenStore,
    private val api: SpotifyApi
) {
    suspend fun follow(userId: String) = authenticated { api.followUser(it, userId) }
    suspend fun unfollow(userId: String) = authenticated { api.unfollowUser(it, userId) }

    suspend fun counts(userId: String): Pair<Int, Int> = authenticated { auth ->
        api.userFollowers(auth, userId, FIRST_PAGE, COUNT_PAGE_SIZE).count to
            api.userFollowing(auth, userId, FIRST_PAGE, COUNT_PAGE_SIZE).count
    }

    suspend fun allFollowing(userId: String): Set<String> = authenticated { auth ->
        val result = mutableSetOf<String>()
        var page = FIRST_PAGE
        do {
            val response = api.userFollowing(auth, userId, page, MAX_PAGE_SIZE)
            response.results.mapTo(result, PublicProfileDto::id)
            page++
        } while (response.next != null)
        result
    }

    fun connections(userId: String, type: ConnectionType): Flow<PagingData<PublicProfileDto>> =
        pager { page, size ->
            authenticated { auth ->
                when (type) {
                    ConnectionType.Followers -> api.userFollowers(auth, userId, page, size)
                    ConnectionType.Following -> api.userFollowing(auth, userId, page, size)
                }
            }.let { SocialPage(it.results, it.next != null) }
        }

    fun publicPlaylists(userId: String): Flow<PagingData<PlaylistDto>> =
        pager { page, size ->
            authenticated { api.userPublicPlaylists(it, userId, page, size) }
                .let { SocialPage(it.results, it.next != null) }
        }

    private fun <T : Any> pager(
        load: suspend (Int, Int) -> SocialPage<T>
    ) = Pager(
        PagingConfig(PAGE_SIZE, prefetchDistance = PREFETCH_DISTANCE, enablePlaceholders = false)
    ) { SocialPagingSource(load) }.flow

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val access = requireNotNull(tokenStore.accessToken)
        return try {
            block("Bearer $access")
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            val token = api.refresh(RefreshRequest(requireNotNull(tokenStore.refreshToken)))
            tokenStore.save(token.access)
            block("Bearer ${token.access}")
        }
    }

    private companion object {
        const val PAGE_SIZE = 10
        const val PREFETCH_DISTANCE = 3
        const val MAX_PAGE_SIZE = 100
        const val COUNT_PAGE_SIZE = 1
    }
}

private class SocialPagingSource<T : Any>(
    private val loadPage: suspend (Int, Int) -> SocialPage<T>
) : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: FIRST_PAGE
        return runCatching {
            val response = loadPage(page, params.loadSize)
            LoadResult.Page(
                response.results,
                if (page == FIRST_PAGE) null else page - 1,
                if (response.hasNext) page + 1 else null
            )
        }.getOrElse { LoadResult.Error(it) }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let(state::closestPageToPosition)?.let {
            it.prevKey?.plus(1) ?: it.nextKey?.minus(1)
        }
}

private data class SocialPage<T>(val results: List<T>, val hasNext: Boolean)
private const val FIRST_PAGE = 1
