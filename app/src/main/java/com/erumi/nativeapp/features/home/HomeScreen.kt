package com.erumi.nativeapp.features.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.R
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.WatchProgress
import com.erumi.nativeapp.ui.components.AnimeCard
import com.erumi.nativeapp.ui.components.ContinueWatchingCard
import com.erumi.nativeapp.ui.components.ErrorView
import com.erumi.nativeapp.ui.components.ErumiTopBar
import com.erumi.nativeapp.ui.components.HeroCarousel
import com.erumi.nativeapp.ui.components.SectionHeader
import com.erumi.nativeapp.ui.theme.*

@Composable
fun HomeScreen(
    onAnimeClick: (Anime) -> Unit,
    onResumeWatch: (WatchProgress) -> Unit,
    onHistoryClick: () -> Unit = {},
    onGoToDownloads: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val genres = listOf("All", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Romance", "Sci-Fi", "Mystery", "Supernatural")

    Scaffold(
        containerColor = ErumiBackground,
        topBar = {
            ErumiTopBar()
        }
    ) { padding ->
        if (state.isLoading) {
            HomeShimmerLoader()
        } else if (state.isOffline) {
            OfflineHomeView(
                onGoToDownloads = onGoToDownloads,
                onRetry = { viewModel.loadData() },
                modifier = Modifier.padding(padding)
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
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
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
                                ContinueWatchingCard(
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
                                AnimeCard(anime = anime, onClick = { onAnimeClick(anime) })
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(genres) { genre ->
                            val isSelected = state.selectedGenre == genre
                            Surface(
                                color = if (isSelected) TealPrimary else BgCard,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.clickable { viewModel.selectGenre(genre) }
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isSelected) BgMain else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
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
                    HomeShimmerLoader()
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (anime in rowAnimes) {
                            Box(modifier = Modifier.weight(1f)) {
                                AnimeCard(
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

// Shimmer loading skeleton for home screen
@Composable
private fun HomeShimmerLoader() {
    val shimmerColors = listOf(
        BgSurfaceElevated,
        BgCard,
        BgSurfaceElevated
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200, 0f),
        end = Offset(translateAnim + 200, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero banner placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(shimmerBrush)
        )
        
        // Section header placeholder
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
        
        // Row of cards placeholder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(5) {
                Column {
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .height(190.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineHomeView(
    onGoToDownloads: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ErumiBackground)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Brand Image
            Image(
                painter = painterResource(id = R.drawable.ic_brand),
                contentDescription = "Erumi",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // "You are offline"
            Text(
                text = "You are offline",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = ErumiPrimaryText
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No internet connection detected.\nYou can still watch your downloaded episodes offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = ErumiSecondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // "Go to download page" Button
            Button(
                onClick = onGoToDownloads,
                colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = null,
                    tint = ErumiBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Go to download page",
                    color = ErumiBackground,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Try Again
            TextButton(onClick = onRetry) {
                Text(
                    text = "Try Again",
                    color = ErumiSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}
