package com.erumi.nativeapp.features.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erumi.nativeapp.core.common.Resource
import com.erumi.nativeapp.data.repository.AnimeRepository
import com.erumi.nativeapp.domain.model.Anime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseUiState(
    val isLoading: Boolean = false,
    val animeList: List<Anime> = emptyList(),
    val error: String? = null
)

class BrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AnimeRepository(application)

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        loadAnime()
    }

    fun loadAnime() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.browseFlow(genre = null, limit = 100).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            animeList = resource.data ?: emptyList()
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = resource.message,
                            animeList = resource.data ?: emptyList()
                        )
                    }
                }
            }
        }
    }

    fun filterByGenre(genre: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val genreParam = if (genre == "All") null else genre
            repository.browseFlow(genre = genreParam, limit = 100).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            animeList = resource.data ?: emptyList()
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = resource.message,
                            animeList = resource.data ?: emptyList()
                        )
                    }
                }
            }
        }
    }
}
