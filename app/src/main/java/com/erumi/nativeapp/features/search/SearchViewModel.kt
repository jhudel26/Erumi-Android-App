package com.erumi.nativeapp.features.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erumi.nativeapp.core.common.Resource
import com.erumi.nativeapp.data.repository.AnimeRepository
import com.erumi.nativeapp.data.repository.UserLibraryRepository
import com.erumi.nativeapp.domain.model.Anime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val searchResults: List<Anime> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val animeRepo = AnimeRepository(application)
    private val libraryRepo = UserLibraryRepository(application)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        observeRecentSearches()
    }

    private fun observeRecentSearches() {
        viewModelScope.launch {
            libraryRepo.recentSearchesFlow.collect { searches ->
                _uiState.update { it.copy(recentSearches = searches) }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false, hasSearched = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // Debounce
            performSearch(newQuery)
        }
    }

    fun performSearch(queryToSearch: String) {
        val q = queryToSearch.trim()
        if (q.isBlank()) return

        _uiState.update { it.copy(query = q, isSearching = true, hasSearched = true, error = null) }

        viewModelScope.launch {
            animeRepo.searchFlow(q).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val results = resource.data.orEmpty()
                        // E7: only persist query when results are non-empty
                        if (results.isNotEmpty()) libraryRepo.addSearchQuery(q)
                        _uiState.update {
                            it.copy(
                                searchResults = results,
                                isSearching = false
                            )
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = resource.message
                            )
                        }
                    }
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isSearching = true) }
                    }
                }
            }
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            libraryRepo.removeSearchQuery(query)
        }
    }

    fun clearAllRecentSearches() {
        viewModelScope.launch {
            libraryRepo.clearAllSearches()
        }
    }
}
