package com.erumi.nativeapp.manga.presentation.home

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erumi.nativeapp.manga.data.repository.MangaRepository
import com.erumi.nativeapp.manga.domain.models.Manga
import com.erumi.nativeapp.manga.domain.models.MangaLibraryItem
import com.erumi.nativeapp.manga.domain.models.MangaReadingProgress
import com.erumi.nativeapp.manga.domain.models.MangaResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MangaHomeUiState(
    val spotlightManga: List<Manga> = emptyList(),
    val continueReading: List<MangaReadingProgress> = emptyList(),
    val trending: List<Manga> = emptyList(),
    val popular: List<Manga> = emptyList(),
    val recentlyUpdated: List<Manga> = emptyList(),
    val latestChapters: List<Manga> = emptyList(),
    val recommended: List<Manga> = emptyList(),
    val newReleases: List<Manga> = emptyList(),
    val library: List<MangaLibraryItem> = emptyList(),
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val error: String? = null,
    val isFirstLoad: Boolean = true
)

class MangaHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = MangaRepository(application)
    private val _uiState = MutableStateFlow(MangaHomeUiState())
    val uiState: StateFlow<MangaHomeUiState> = _uiState.asStateFlow()

    init {
        observeLocal()
        observeActiveSource()
    }

    fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun observeActiveSource() {
        viewModelScope.launch {
            repo.observeActiveSourceId().collect {
                refresh()
            }
        }
    }

    private fun observeLocal() {
        viewModelScope.launch {
            repo.observeContinueReading().collect { list ->
                _uiState.update { it.copy(continueReading = list) }
            }
        }
        viewModelScope.launch {
            repo.observeLibrary().collect { list ->
                _uiState.update { it.copy(library = list) }
            }
        }
    }

    fun refresh() {
        if (!isOnline() && _uiState.value.popular.isEmpty()) {
            _uiState.update { it.copy(isOffline = true, isLoading = false, error = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isOffline = false) }
            val popular = repo.getPopular(1)
            val latest = repo.getLatest(1)
            val newest = repo.browse(1, com.erumi.nativeapp.manga.domain.models.MangaSortOption.RECENTLY_ADDED)

            val popularItems = (popular as? MangaResult.Success)?.data?.items.orEmpty()
            val latestItems = (latest as? MangaResult.Success)?.data?.items.orEmpty()
            val newItems = (newest as? MangaResult.Success)?.data?.items.orEmpty()

            val error = listOf(popular, latest, newest)
                .filterIsInstance<MangaResult.Error>()
                .firstOrNull()?.message

            val offline = !isOnline() && popularItems.isEmpty() && latestItems.isEmpty() && newItems.isEmpty()
            if (offline) {
                _uiState.update { it.copy(isOffline = true, isLoading = false, error = null) }
                return@launch
            }

            if (popularItems.isEmpty() && latestItems.isEmpty() && newItems.isEmpty() && error != null) {
                _uiState.update { it.copy(isLoading = false, error = error, isOffline = false) }
                return@launch
            }

            var spotlight = (popularItems + latestItems).distinctBy { it.title }.take(6)
            if (_uiState.value.isFirstLoad && spotlight.size > 1) {
                spotlight = spotlight.shuffled()
            }

            _uiState.update {
                it.copy(
                    spotlightManga = spotlight,
                    trending = popularItems.take(18),
                    popular = popularItems,
                    recentlyUpdated = latestItems,
                    latestChapters = latestItems.filter { m -> !m.latestChapter.isNullOrBlank() },
                    recommended = popularItems.drop(6).take(12),
                    newReleases = newItems,
                    isLoading = false,
                    isOffline = false,
                    error = null,
                    isFirstLoad = false
                )
            }
        }
    }
}
