package com.erumi.nativeapp.manga.presentation.discover

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.manga.data.repository.MangaRepository
import com.erumi.nativeapp.manga.domain.models.Manga
import com.erumi.nativeapp.manga.domain.models.MangaResult
import com.erumi.nativeapp.manga.domain.models.MangaSortOption
import com.erumi.nativeapp.shared.ui.components.FilterChipRow
import com.erumi.nativeapp.shared.ui.components.MediaErrorState
import com.erumi.nativeapp.shared.ui.components.MediaPosterCard
import com.erumi.nativeapp.shared.ui.components.ShimmerLoader
import com.erumi.nativeapp.shared.ui.components.ShimmerType
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.TextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val contentType: String = "All",
    val genre: String = "All",
    val sort: MangaSortOption = MangaSortOption.POPULARITY,
    val genres: List<String> = emptyList(),
    val items: List<Manga> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

class MangaDiscoverViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = MangaRepository(application)
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeActiveSourceId().collect {
                _uiState.update { it.copy(genres = listOf("All") + repo.getGenres(), page = 1) }
                loadPage(1, reset = true)
            }
        }
    }

    fun setContentType(value: String) {
        _uiState.update { it.copy(contentType = value) }
        refresh()
    }

    fun setGenre(value: String) {
        _uiState.update { it.copy(genre = value) }
        refresh()
    }

    fun setSort(sort: MangaSortOption) {
        _uiState.update { it.copy(sort = sort) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, page = 1) }
            loadPage(1, reset = true)
        }
    }

    fun loadNext() {
        val state = _uiState.value
        if (!state.hasNext || state.isLoadingMore || state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            loadPage(state.page + 1, reset = false)
        }
    }

    private suspend fun loadPage(page: Int, reset: Boolean) {
        val state = _uiState.value
        val genre = state.genre.takeUnless { it == "All" }
        val result = repo.browse(page, state.sort, genre)
        when (result) {
            is MangaResult.Success -> {
                val filtered = result.data.items.filter { manga ->
                    when (state.contentType) {
                        "Manga" -> manga.contentType.name.equals("MANGA", true) || manga.contentType.name == "UNKNOWN"
                        "Manhwa" -> manga.contentType.name.equals("MANHWA", true) || manga.contentType.name == "UNKNOWN"
                        "Manhua" -> manga.contentType.name.equals("MANHUA", true) || manga.contentType.name == "UNKNOWN"
                        else -> true
                    }
                }
                _uiState.update {
                    it.copy(
                        items = if (reset) filtered else it.items + filtered,
                        page = page,
                        hasNext = result.data.hasNextPage,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null
                    )
                }
            }
            is MangaResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = if (it.items.isEmpty()) result.message else null
                    )
                }
            }
        }
    }
}

@Composable
fun MangaDiscoverScreen(
    onMangaClick: (Manga) -> Unit,
    viewModel: MangaDiscoverViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { it >= state.items.lastIndex - 4 }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadNext() }
    }

    Column(modifier = Modifier.fillMaxSize().background(ErumiBackground).statusBarsPadding()) {
        Text(
            text = "Discover",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
        Text(
            text = "Find your next story across genres and categories",
            style = MaterialTheme.typography.bodyMedium,
            color = ErumiSecondaryText,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
        FilterChipRow(
            options = listOf("All", "Manhua", "Manhwa", "Manga"),
            selected = state.contentType,
            onSelected = viewModel::setContentType
        )
        FilterChipRow(
            options = listOf(
                "Popularity" to MangaSortOption.POPULARITY,
                "Latest" to MangaSortOption.LATEST_UPDATES,
                "New" to MangaSortOption.RECENTLY_ADDED,
                "A–Z" to MangaSortOption.ALPHABETICAL,
                "Rating" to MangaSortOption.RATING
            ).map { it.first },
            selected = when (state.sort) {
                MangaSortOption.POPULARITY -> "Popularity"
                MangaSortOption.LATEST_UPDATES -> "Latest"
                MangaSortOption.RECENTLY_ADDED -> "New"
                MangaSortOption.ALPHABETICAL -> "A–Z"
                MangaSortOption.RATING -> "Rating"
            },
            onSelected = { selectedStr ->
                val sort = when (selectedStr) {
                    "Latest" -> MangaSortOption.LATEST_UPDATES
                    "New" -> MangaSortOption.RECENTLY_ADDED
                    "A–Z" -> MangaSortOption.ALPHABETICAL
                    "Rating" -> MangaSortOption.RATING
                    else -> MangaSortOption.POPULARITY
                }
                viewModel.setSort(sort)
            }
        )
        if (state.genres.isNotEmpty()) {
            FilterChipRow(
                options = state.genres.take(20),
                selected = state.genre,
                onSelected = viewModel::setGenre
            )
        }

        when {
            state.isLoading && state.items.isEmpty() -> ShimmerLoader(
                type = ShimmerType.GRID,
                columns = GridCells.Adaptive(120.dp),
                itemCount = 8,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            state.error != null && state.items.isEmpty() -> MediaErrorState(state.error!!, onRetry = viewModel::refresh)
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(state.items, key = { it.id }) { manga ->
                        MediaPosterCard(manga = manga, onClick = { onMangaClick(manga) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
