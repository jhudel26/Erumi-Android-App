package com.erumi.nativeapp.features.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erumi.nativeapp.data.repository.UserLibraryRepository
import com.erumi.nativeapp.domain.model.Anime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val watchlist: List<Anime> = emptyList(),
    val isLoading: Boolean = false
)

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepo = UserLibraryRepository(application)
    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepo.watchlistFlow.collect { items ->
                _uiState.update { it.copy(watchlist = items) }
            }
        }
    }

    fun removeFromWatchlist(anime: Anime) {
        viewModelScope.launch {
            libraryRepo.toggleWatchlist(anime)
        }
    }
}
