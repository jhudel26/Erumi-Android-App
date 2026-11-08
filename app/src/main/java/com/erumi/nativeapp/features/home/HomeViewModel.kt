package com.erumi.nativeapp.features.home

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erumi.nativeapp.core.common.Resource
import com.erumi.nativeapp.data.repository.AnimeRepository
import com.erumi.nativeapp.data.repository.UserLibraryRepository
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val featuredAnime: Anime? = null,
    val spotlightAnime: List<Anime> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val latestReleases: List<Anime> = emptyList(),
    val popularAnime: List<Anime> = emptyList(),
    val selectedGenre: String = "All",
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val error: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val animeRepo = AnimeRepository(application)
    private val libraryRepo = UserLibraryRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        observeContinueWatching()
    }

    fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun loadData() {
        if (!isOnline() && _uiState.value.latestReleases.isEmpty()) {
            _uiState.update { it.copy(isOffline = true, isLoading = false, error = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isOffline = false) }

            // Latest releases
            animeRepo.getLatestFlow(18).collect { res ->
                when (res) {
                    is Resource.Success -> {
                        val items = res.data.orEmpty()
                        _uiState.update { current ->
                            val currentSpotlight = current.spotlightAnime
                            val spotlight = if (currentSpotlight.isEmpty()) {
                                items.take(6)
                            } else currentSpotlight
                            current.copy(
                                latestReleases = items,
                                spotlightAnime = spotlight,
                                featuredAnime = current.featuredAnime ?: items.firstOrNull(),
                                isLoading = false,
                                isOffline = false
                            )
                        }
                    }
                    is Resource.Error -> {
                        val offline = !isOnline() && _uiState.value.latestReleases.isEmpty()
                        _uiState.update {
                            it.copy(
                                error = if (offline) null else res.message,
                                isLoading = false,
                                isOffline = offline
                            )
                        }
                    }
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }

        viewModelScope.launch {
            if (!isOnline()) return@launch
            animeRepo.getPopularFlow(20).collect { res ->
                if (res is Resource.Success) {
                    val items = res.data.orEmpty()
                    _uiState.update { current ->
                        val combined = (items.take(6) + current.latestReleases.take(3))
                            .distinctBy { it.title }
                            .take(6)
                        current.copy(
                            popularAnime = items,
                            spotlightAnime = if (combined.isNotEmpty()) combined else current.spotlightAnime,
                            featuredAnime = current.featuredAnime ?: items.firstOrNull()
                        )
                    }
                }
            }
        }
    }

    fun selectGenre(genre: String) {
        _uiState.update { it.copy(selectedGenre = genre, isLoading = true) }
        viewModelScope.launch {
            animeRepo.browseFlow(if (genre == "All") null else genre, page = 1, limit = 24).collect { res ->
                if (res is Resource.Success) {
                    _uiState.update { it.copy(latestReleases = res.data.orEmpty(), isLoading = false) }
                } else if (res is Resource.Error) {
                    _uiState.update { it.copy(isLoading = false, error = res.message) }
                }
            }
        }
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            libraryRepo.continueWatchingFlow.collect { list ->
                _uiState.update { it.copy(continueWatching = list) }
                // Backfill any existing continue watching items that have null/blank posters
                list.filter { it.poster.isNullOrBlank() }.forEach { missing ->
                    launch(Dispatchers.IO) {
                        try {
                            val details = animeRepo.getAnimeDetails(missing.animeTitle)
                            val poster = details?.poster
                            if (!poster.isNullOrBlank()) {
                                libraryRepo.updateContinueWatchingPoster(missing.animeId, poster)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}
