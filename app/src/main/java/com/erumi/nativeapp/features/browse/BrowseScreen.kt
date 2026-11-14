package com.erumi.nativeapp.features.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.shared.ui.components.FilterChipRow
import com.erumi.nativeapp.shared.ui.components.MediaPosterCard
import com.erumi.nativeapp.shared.ui.components.ShimmerLoader
import com.erumi.nativeapp.shared.ui.components.ShimmerType
import com.erumi.nativeapp.ui.components.ErrorView
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText

private val GENRE_FILTERS = listOf(
    "All", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Mystery", "Supernatural", "Horror", "Sports", "Music"
)

@Composable
fun BrowseScreen(
    onAnimeClick: (Anime) -> Unit,
    viewModel: BrowseViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedGenre by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ErumiBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Browse",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = ErumiPrimaryText,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Explore anime by genre, season, and popularity",
            style = MaterialTheme.typography.bodyMedium,
            color = ErumiSecondaryText
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Genre Filter
        FilterChipRow(
            options = GENRE_FILTERS,
            selected = selectedGenre,
            onSelected = { genre ->
                selectedGenre = genre
                viewModel.filterByGenre(genre)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Content
        when {
            state.isLoading && state.animeList.isEmpty() -> {
                ShimmerLoader(
                    type = ShimmerType.GRID,
                    columns = GridCells.Fixed(2),
                    itemCount = 6
                )
            }
            state.error != null && state.animeList.isEmpty() -> {
                ErrorView(
                    message = state.error!!,
                    onRetry = { viewModel.loadAnime() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                if (state.animeList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No anime found for '$selectedGenre'",
                            color = ErumiSecondaryText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.animeList, key = { it.title }) { anime ->
                            MediaPosterCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) }
                            )
                        }
                    }
                }
            }
        }
    }
}
