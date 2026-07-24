package com.example.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.android.data.search.SearchRepository
import com.example.android.data.search.SearchHistoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchType { Profiles, Songs }

data class SearchUiState(
    val query: String = "",
    val type: SearchType = SearchType.Profiles
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val repository: SearchRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state = _state
    val history = repository.history
    private var lastSavedSearch: Pair<String, SearchType>? = null

    private val searchInput = combine(
        _state.map { it.query.trim() }.debounce(SEARCH_DEBOUNCE_MILLIS),
        _state.map { it.type }.distinctUntilChanged()
    ) { query, type ->
        query to type
    }.distinctUntilChanged()

    val profiles = searchInput.flatMapLatest { (query, type) ->
        if (query.isBlank() || type != SearchType.Profiles) {
            flowOf(PagingData.empty())
        } else {
            repository.profiles(query)
        }
    }.cachedIn(viewModelScope)

    val songs = searchInput.flatMapLatest { (query, type) ->
        if (query.isBlank() || type != SearchType.Songs) {
            flowOf(PagingData.empty())
        } else {
            repository.songs(query)
        }
    }.cachedIn(viewModelScope)

    fun setQuery(query: String) = _state.update {
        it.copy(query = query.take(MAX_QUERY_LENGTH))
    }

    fun setType(type: SearchType) = _state.update { it.copy(type = type) }

    fun saveCurrentQuery() {
        val query = _state.value.query.trim()
        val type = _state.value.type
        val search = query to type
        if (query.isBlank() || search == lastSavedSearch) return
        lastSavedSearch = search
        viewModelScope.launch {
            runCatching { repository.saveHistory(query, type.name) }
                .onFailure { lastSavedSearch = null }
        }
    }

    fun useHistory(item: SearchHistoryEntity) {
        val type = runCatching { SearchType.valueOf(item.searchType) }
            .getOrDefault(SearchType.Profiles)
        _state.value = SearchUiState(query = item.query, type = type)
    }

    fun removeHistory(item: SearchHistoryEntity) = viewModelScope.launch {
        repository.removeHistory(item)
        if (lastSavedSearch == (item.query to runCatching {
                SearchType.valueOf(item.searchType)
            }.getOrDefault(SearchType.Profiles))
        ) {
            lastSavedSearch = null
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L
        const val MAX_QUERY_LENGTH = 150
    }
}
