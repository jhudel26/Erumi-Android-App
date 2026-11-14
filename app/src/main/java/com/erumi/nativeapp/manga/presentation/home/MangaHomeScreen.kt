package com.erumi.nativeapp.manga.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.erumi.nativeapp.R
import com.erumi.nativeapp.manga.domain.models.Manga
import com.erumi.nativeapp.manga.domain.models.MangaLibraryItem
import com.erumi.nativeapp.manga.domain.models.MangaReadingProgress
import com.erumi.nativeapp.manga.presentation.components.MangaCoverImage
import com.erumi.nativeapp.manga.presentation.components.MangaHeroCarousel
import com.erumi.nativeapp.manga.presentation.components.MangaHorizontalRow
import com.erumi.nativeapp.shared.ui.components.ContinueMediaCard
import com.erumi.nativeapp.shared.ui.components.MediaErrorState
import com.erumi.nativeapp.shared.ui.components.OfflineHomeView
import com.erumi.nativeapp.shared.ui.components.SectionHeader
import com.erumi.nativeapp.shared.ui.components.ShimmerLoader
import com.erumi.nativeapp.shared.ui.components.ShimmerType
import com.erumi.nativeapp.shared.ui.tour.TourTargetKey
import com.erumi.nativeapp.shared.ui.tour.tourHighlight
import com.erumi.nativeapp.ui.components.PressScale
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.TextDim
import com.erumi.nativeapp.ui.theme.TextPrimary

@Composable
fun MangaHomeScreen(
    onMangaClick: (Manga) -> Unit,
    onContinueReading: (MangaReadingProgress) -> Unit,
    onLibraryClick: (MangaLibraryItem) -> Unit,
    onSwitchExperience: () -> Unit,
    onSearchClick: () -> Unit = {},
    onSeeAllContinueReading: () -> Unit = {},
    onGoToDownloads: () -> Unit = {},
    viewModel: MangaHomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(ErumiBackground)) {
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
                        text = "Manhua · Manhwa · Manga",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.tourHighlight(TourTargetKey.HOME_SEARCH)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = ErumiSecondaryText)
                }
                IconButton(
                    onClick = onSwitchExperience,
                    modifier = Modifier.tourHighlight(TourTargetKey.HOME_SWITCH)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Switch experience", tint = ErumiSecondaryText)
                }
            }
        }

        when {
            state.isLoading && state.popular.isEmpty() && !state.isOffline -> ShimmerLoader(type = ShimmerType.HOME, modifier = Modifier.fillMaxSize())
            state.isOffline -> OfflineHomeView(
                onGoToDownloads = onGoToDownloads,
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
                description = "No internet connection detected.\nYou can still read your downloaded chapters offline."
            )
            state.error != null && state.popular.isEmpty() -> MediaErrorState(state.error!!, onRetry = viewModel::refresh)
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.spotlightManga.isNotEmpty()) {
                        item {
                            MangaHeroCarousel(
                                spotlightManga = state.spotlightManga,
                                onMangaClick = onMangaClick,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                    if (state.continueReading.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Continue Reading",
                                onSeeAllClick = onSeeAllContinueReading
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.continueReading, key = { it.mangaId }) { progress ->
                                    ContinueMediaCard(progress, onClick = { onContinueReading(progress) })
                                }
                            }
                        }
                    }
                    item { SectionHeader("Trending") }
                    item { MangaHorizontalRow(state.trending, onMangaClick) }
                    item { SectionHeader("Popular") }
                    item { MangaHorizontalRow(state.popular, onMangaClick) }
                    item { SectionHeader("Recently Updated") }
                    item { MangaHorizontalRow(state.recentlyUpdated, onMangaClick) }
                    item { SectionHeader("Latest Chapters") }
                    item { MangaHorizontalRow(state.latestChapters, onMangaClick) }
                    item { SectionHeader("Recommended") }
                    item { MangaHorizontalRow(state.recommended, onMangaClick) }
                    item { SectionHeader("New Releases") }
                    item { MangaHorizontalRow(state.newReleases, onMangaClick) }
                    if (state.library.isNotEmpty()) {
                        item { SectionHeader("Your Library") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.library, key = { it.mangaId }) { item ->
                                    PressScale(
                                        onClick = { onLibraryClick(item) },
                                        modifier = Modifier.width(130.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                        ) {
                                            MangaCoverImage(
                                                url = item.coverUrl,
                                                contentDescription = item.title,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
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
    }
}
