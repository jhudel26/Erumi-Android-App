package com.erumi.nativeapp.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.erumi.nativeapp.R
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.WatchProgress
import com.erumi.nativeapp.shared.ui.components.OfflineHomeView
import com.erumi.nativeapp.shared.ui.components.ContinueMediaCard
import com.erumi.nativeapp.shared.ui.components.FilterChipRow
import com.erumi.nativeapp.shared.ui.components.MediaPosterCard
import com.erumi.nativeapp.shared.ui.components.SectionHeader
import com.erumi.nativeapp.shared.ui.components.ShimmerLoader
import com.erumi.nativeapp.shared.ui.components.ShimmerPosterCard
import com.erumi.nativeapp.shared.ui.components.ShimmerType
import com.erumi.nativeapp.shared.ui.components.rememberShimmerBrush
import com.erumi.nativeapp.shared.ui.tour.TourTargetKey
import com.erumi.nativeapp.shared.ui.tour.tourHighlight
import com.erumi.nativeapp.ui.components.ErrorView
import com.erumi.nativeapp.ui.components.HeroCarousel
import com.erumi.nativeapp.ui.theme.*

private val GENRE_FILTER_OPTIONS = listOf("All", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Mystery", "Supernatural")

@Composable
fun HomeScreen(
    onAnimeClick: (Anime) -> Unit,
    onResumeWatch: (WatchProgress) -> Unit,
    onHistoryClick: () -> Unit = {},
    onGoToDownloads: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSwitchExperience: (() -> Unit)? = null,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = ErumiBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HomeTopBar(
                onSearchClick = onSearchClick,
                onSwitchExperience = onSwitchExperience
            )
        }
    ) { padding ->
        if (state.isLoading) {
            ShimmerLoader(type = ShimmerType.HOME, modifier = Modifier.padding(padding))
        } else if (state.isOffline) {
            OfflineHomeView(
                onGoToDownloads = onGoToDownloads,
                onRetry = { viewModel.loadData() },
                modifier = Modifier.padding(padding),
                description = "No internet connection detected.\nYou can still watch your downloaded episodes offline."
            )
        } else {
            val errorMessage = state.error
            if (errorMessage != null) {
                ErrorView(
                    message = errorMessage,
                    onRetry = { viewModel.loadData() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 80.dp)
                ) {
            // 1. Featured Spotlight Hero Carousel
            val spotlightList = state.spotlightAnime.ifEmpty {
                listOfNotNull(state.featuredAnime)
            }
            if (spotlightList.isNotEmpty()) {
                item {
                    HeroCarousel(
                        spotlightAnime = spotlightList,
                        onAnimeClick = onAnimeClick
                    )
                }
            }

            // 2. Continue Watching Shelf
            if (state.continueWatching.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader(
                            title = "Continue Watching",
                            onSeeAllClick = onHistoryClick
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.continueWatching) { item ->
                                ContinueMediaCard(
                                    progress = item,
                                    onClick = { onResumeWatch(item) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Trending & Popular Row
            if (state.popularAnime.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader(title = "Trending Spotlight")
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.popularAnime) { anime ->
                                MediaPosterCard(anime = anime, onClick = { onAnimeClick(anime) })
                            }
                        }
                    }
                }
            }

            // 4. Genre Filter Chips
            item {
                Column {
                    SectionHeader(title = "Explore Categories")
                    Spacer(modifier = Modifier.height(8.dp))
                    FilterChipRow(
                        options = GENRE_FILTER_OPTIONS,
                        selected = state.selectedGenre,
                        onSelected = { viewModel.selectGenre(it) }
                    )
                }
            }

            // 5. Latest Releases Grid Header
            item {
                SectionHeader(
                    title = if (state.selectedGenre == "All") "Latest Releases" else "${state.selectedGenre} Anime"
                )
            }

            // Content or Loading/Error State
            if (state.isLoading && state.latestReleases.isEmpty()) {
                item {
                    val brush = rememberShimmerBrush()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(3) {
                            ShimmerPosterCard(modifier = Modifier.weight(1f), brush = brush)
                        }
                    }
                }
            } else if (state.error != null && state.latestReleases.isEmpty()) {
                item {
                    ErrorView(
                        message = state.error ?: "Failed to load catalog",
                        onRetry = { viewModel.loadData() }
                    )
                }
            } else {
                val chunked = state.latestReleases.chunked(3)
                items(chunked) { rowAnimes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (anime in rowAnimes) {
                            Box(modifier = Modifier.weight(1f)) {
                                MediaPosterCard(
                                    anime = anime,
                                    onClick = { onAnimeClick(anime) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        val remaining = 3 - rowAnimes.size
                        if (remaining > 0) {
                            Spacer(modifier = Modifier.weight(remaining.toFloat()))
                        }
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun HomeTopBar(
    onSearchClick: () -> Unit = {},
    onSwitchExperience: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = R.drawable.app_icon,
                contentDescription = "Erumi Logo",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column {
                Text(
                    text = "ERUMI",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = ErumiPrimary
                )
                Text(
                    text = "Cinema Anime Streaming",
                    style = MaterialTheme.typography.labelSmall,
                    color = ErumiMuted
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.tourHighlight(TourTargetKey.HOME_SEARCH)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = ErumiSecondaryText)
            }
            if (onSwitchExperience != null) {
                IconButton(
                    onClick = onSwitchExperience,
                    modifier = Modifier.tourHighlight(TourTargetKey.HOME_SWITCH)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Switch to Manhua",
                        tint = ErumiSecondaryText
                    )
                }
            }
        }
    }
}
