package com.erumi.nativeapp.manga.presentation.search

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.manga.data.repository.MangaRepository
import com.erumi.nativeapp.manga.domain.models.Manga
import com.erumi.nativeapp.manga.domain.models.MangaResult
import com.erumi.nativeapp.shared.ui.components.MediaEmptyState
import com.erumi.nativeapp.shared.ui.components.MediaErrorState
import com.erumi.nativeapp.shared.ui.components.MediaPosterCard
import com.erumi.nativeapp.shared.ui.components.MediaSearchBar
import com.erumi.nativeapp.shared.ui.components.ShimmerLoader
import com.erumi.nativeapp.shared.ui.components.ShimmerType
import com.erumi.nativeapp.ui.theme.BgCard
import com.erumi.nativeapp.ui.theme.BgSurface
import com.erumi.nativeapp.ui.theme.BgSurfaceElevated
import com.erumi.nativeapp.ui.theme.BorderSubtle
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.TextDim
import com.erumi.nativeapp.ui.theme.TextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Manga> = emptyList(),
    val history: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false
)

class MangaSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = MangaRepository(application)
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repo.observeSearchHistory().collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), searched = false, error = null, isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            performSearch(query)
        }
    }

    fun searchNow(query: String = _uiState.value.query) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clearSearchHistory() }
    }

    fun deleteSearchHistory(query: String) {
        viewModelScope.launch { repo.deleteSearchHistory(query) }
    }

    private suspend fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _uiState.update { it.copy(isLoading = true, error = null, searched = true, query = trimmed) }
        when (val result = repo.search(trimmed, 1)) {
            is MangaResult.Success -> _uiState.update {
                it.copy(results = result.data.items, isLoading = false, error = null)
            }
            is MangaResult.Error -> _uiState.update {
                it.copy(isLoading = false, error = result.message, results = emptyList())
            }
        }
    }
}

@Composable
fun MangaSearchScreen(
    onMangaClick: (Manga) -> Unit,
    viewModel: MangaSearchViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ErumiBackground)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        MediaSearchBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            placeholder = "Search manhua, manhwa, manga by title...",
            onSearch = { viewModel.searchNow() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading -> ShimmerLoader(
                type = ShimmerType.SEARCH,
                columns = GridCells.Adaptive(120.dp),
                itemCount = 9
            )
            state.error != null -> MediaErrorState(state.error!!, onRetry = { viewModel.searchNow() })
            state.searched && state.results.isEmpty() -> MediaEmptyState(
                message = "No results found for \"${state.query}\"",
                subtitle = "Try searching with different keywords"
            )
            state.results.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "${state.results.size} results found",
                            style = MaterialTheme.typography.labelMedium.copy(color = ErumiSecondaryText),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(state.results, key = { it.id }) { manga ->
                        MediaPosterCard(manga = manga, onClick = { onMangaClick(manga) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            else -> {
                if (state.history.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        TextButton(onClick = viewModel::clearHistory) {
                            Text("Clear All", color = ErumiSecondaryText, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.history.forEach { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgSurface)
                                    .clickable { viewModel.searchNow(query) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = query,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                                )
                                IconButton(
                                    onClick = { viewModel.deleteSearchHistory(query) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = TextDim,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
