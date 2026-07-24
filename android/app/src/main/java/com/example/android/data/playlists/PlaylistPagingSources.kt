package com.example.android.data.playlists

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.android.data.remote.PlaylistDto
import com.example.android.domain.home.Song

internal class PlaylistsPagingSource(
    private val loadPage: suspend (Int, Int) -> PlaylistPage<PlaylistDto>
) : PagingSource<Int, PlaylistDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PlaylistDto> =
        runCatching {
            val page = params.key ?: FIRST_PAGE
            val response = loadPage(page, params.loadSize)
            LoadResult.Page(
                data = response.results,
                prevKey = if (page == FIRST_PAGE) null else page - 1,
                nextKey = if (response.hasNext) page + 1 else null
            )
        }.getOrElse { error -> LoadResult.Error(error) }

    override fun getRefreshKey(state: PagingState<Int, PlaylistDto>): Int? =
        state.anchorPosition?.let(state::closestPageToPosition)?.let { page ->
            page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
        }
}

internal class PlaylistSongsPagingSource(
    private val loadPage: suspend (Int, Int) -> PlaylistPage<Song>
) : PagingSource<Int, Song>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Song> =
        runCatching {
            val page = params.key ?: FIRST_PAGE
            val response = loadPage(page, params.loadSize)
            LoadResult.Page(
                data = response.results,
                prevKey = if (page == FIRST_PAGE) null else page - 1,
                nextKey = if (response.hasNext) page + 1 else null
            )
        }.getOrElse { error -> LoadResult.Error(error) }

    override fun getRefreshKey(state: PagingState<Int, Song>): Int? =
        state.anchorPosition?.let(state::closestPageToPosition)?.let { page ->
            page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
        }
}

internal data class PlaylistPage<T>(val results: List<T>, val hasNext: Boolean)

private const val FIRST_PAGE = 1
