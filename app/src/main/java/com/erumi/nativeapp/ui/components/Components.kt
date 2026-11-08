package com.erumi.nativeapp.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.erumi.nativeapp.R
import com.erumi.nativeapp.core.image.ImageResolver
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.WatchProgress
import com.erumi.nativeapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ErumiTopBar() {
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
                    color = TealPrimary
                )
                Text(
                    text = "Cinema Anime Streaming",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onSeeAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TealPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }
        if (onSeeAllClick != null) {
            Text(
                text = "See All",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TealLight,
                modifier = Modifier.clickable { onSeeAllClick() }
            )
        }
    }
}

@Composable
fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageResolver = remember { ImageResolver(context) }
    var resolvedPoster by remember { mutableStateOf(anime.poster) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Only resolve if poster is null/blank
    LaunchedEffect(anime.id, anime.poster) {
        if (anime.poster.isNullOrBlank() && !isLoading) {
            isLoading = true
            coroutineScope.launch {
                val resolved = imageResolver.resolvePosterUrl(anime)
                resolvedPoster = resolved
                isLoading = false
            }
        } else {
            resolvedPoster = anime.poster
        }
    }

    PressScale(onClick = onClick, modifier = modifier.width(135.dp)) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            ) {
                // Show branded placeholder while resolving, otherwise show actual image
                if (isLoading) {
                    ErumiImagePlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        title = anime.title
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(resolvedPoster)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            ErumiImagePlaceholder(
                                modifier = Modifier.fillMaxSize(),
                                title = anime.title
                            )
                        },
                        error = {
                            ErumiImagePlaceholder(
                                modifier = Modifier.fillMaxSize(),
                                title = anime.title
                            )
                        }
                    )
                }

                // Dark vignette gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 100f
                            )
                        )
                )

                // Rating Star Tag (Top-End)
                anime.score?.let { score ->
                    if (score > 0) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(BgMain.copy(alpha = 0.85f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Rating",
                                tint = WarningAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format("%.1f", score),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Episode Tag (Bottom-Start)
                if (anime.episodes > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(TealDark.copy(alpha = 0.9f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "EP ${anime.episodes}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TealLight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = anime.englishName ?: anime.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val yearOrFormat = listOfNotNull(anime.releaseYear?.toString(), anime.format ?: "TV").joinToString(" • ")
            if (yearOrFormat.isNotBlank()) {
                Text(
                    text = yearOrFormat,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageResolver = remember { ImageResolver(context) }
    var resolvedPoster by remember { mutableStateOf(progress.poster) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(progress.animeId, progress.poster) {
        if (progress.poster.isNullOrBlank() && !isLoading) {
            isLoading = true
            coroutineScope.launch {
                val anime = com.erumi.nativeapp.domain.model.Anime(
                    id = progress.animeId,
                    title = progress.animeTitle,
                    poster = null
                )
                val resolved = imageResolver.resolvePosterUrl(anime)
                resolvedPoster = resolved
                isLoading = false
            }
        } else {
            resolvedPoster = progress.poster
        }
    }

    PressScale(onClick = onClick, modifier = modifier.width(220.dp).height(115.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(BgSurfaceElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
        ) {
            if (isLoading) {
                ErumiImagePlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    title = progress.animeTitle
                )
            } else {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(resolvedPoster)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = progress.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        ErumiImagePlaceholder(
                            modifier = Modifier.fillMaxSize(),
                            title = progress.animeTitle
                        )
                    },
                    error = {
                        ErumiImagePlaceholder(
                            modifier = Modifier.fillMaxSize(),
                            title = progress.animeTitle
                        )
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            // Center Play Circle
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(TealPrimary.copy(alpha = 0.9f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Resume",
                    tint = BgMain,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Bottom Info & Progress Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = progress.animeTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Episode ${progress.episodeNumber}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TealLight,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = TealPrimary,
                    trackColor = BorderSubtle
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    spotlightAnime: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    onWatchClick: ((Anime) -> Unit)? = null,
    onDetailsClick: ((Anime) -> Unit)? = null
) {
    if (spotlightAnime.isEmpty()) return

    val context = LocalContext.current
    val imageResolver = remember { ImageResolver(context) }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { spotlightAnime.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // Resolve images for spotlight anime
    val resolvedImages = remember { mutableMapOf<String, String?>() }
    val loadingStates = remember { mutableMapOf<String, Boolean>() }
    LaunchedEffect(spotlightAnime) {
        spotlightAnime.forEach { anime ->
            loadingStates[anime.id] = true
            coroutineScope.launch {
                val resolved = imageResolver.resolveBannerUrl(anime) ?: imageResolver.resolvePosterUrl(anime)
                resolvedImages[anime.id] = resolved
                loadingStates[anime.id] = false
            }
        }
    }

    // Auto-advance pager every 5 seconds when idle
    LaunchedEffect(spotlightAnime.size) {
        if (spotlightAnime.size > 1) {
            snapshotFlow { pagerState.settledPage }.collectLatest { page ->
                delay(5000L)
                if (!pagerState.isScrollInProgress) {
                    val next = (page + 1) % spotlightAnime.size
                    try {
                        pagerState.animateScrollToPage(
                            page = next,
                            animationSpec = tween(
                                durationMillis = 650,
                                easing = FastOutSlowInEasing
                            )
                        )
                    } catch (_: Exception) {
                        // Ignore if interrupted by user gesture
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val anime = spotlightAnime.getOrNull(page) ?: return@HorizontalPager
            val resolvedImage = resolvedImages[anime.id] ?: (anime.banner ?: anime.poster)
            val isLoading = loadingStates[anime.id] ?: false

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { (onDetailsClick ?: onAnimeClick).invoke(anime) }
            ) {
                if (isLoading) {
                    ErumiImagePlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        title = anime.title
                    )
                } else {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(resolvedImage)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            ErumiImagePlaceholder(
                                modifier = Modifier.fillMaxSize(),
                                title = anime.title
                            )
                        },
                        error = {
                            ErumiImagePlaceholder(
                                modifier = Modifier.fillMaxSize(),
                                title = anime.title
                            )
                        }
                    )
                }

                // Top subtle gradient for depth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    BgMain.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Bottom rich gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to BgMain.copy(alpha = 0.5f),
                                0.8f to BgMain.copy(alpha = 0.95f),
                                1f to BgMain
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TealPrimary)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SPOTLIGHT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = BgMain
                            )
                        }
                        if (anime.releaseYear != null) {
                            Text(
                                text = "${anime.releaseYear}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        if (anime.score != null) {
                            Text(
                                text = "★ ${String.format(Locale.US, "%.1f", anime.score)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = WarningAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = anime.englishName ?: anime.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { (onWatchClick ?: onAnimeClick).invoke(anime) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Watch Now",
                                tint = BgMain,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Watch Now",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = BgMain
                            )
                        }

                        OutlinedButton(
                            onClick = { (onDetailsClick ?: onAnimeClick).invoke(anime) },
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(listOf(BorderSubtle, BorderSubtle))
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }

        // Animated page indicator pills at bottom-end
        if (spotlightAnime.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                spotlightAnime.indices.forEach { index ->
                    val isSelected = index == pagerState.currentPage
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 22.dp else 6.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "indicatorWidth"
                    )
                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isSelected) TealPrimary else TextSecondary.copy(alpha = 0.35f)
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun HeroBanner(
    anime: Anime,
    onWatchClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SpotlightHeroBanner(
        anime = anime,
        onWatchClick = onWatchClick,
        onDetailsClick = onDetailsClick,
        modifier = modifier
    )
}

@Composable
fun SpotlightHeroBanner(
    anime: Anime,
    onWatchClick: () -> Unit,
    onDetailsClick: () -> Unit = onWatchClick,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageResolver = remember { ImageResolver(context) }
    var resolvedImage by remember { mutableStateOf(anime.banner ?: anime.poster) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(anime.id, anime.banner, anime.poster) {
        if ((anime.banner.isNullOrBlank() && anime.poster.isNullOrBlank()) && !isLoading) {
            isLoading = true
            coroutineScope.launch {
                resolvedImage = imageResolver.resolveBannerUrl(anime) ?: imageResolver.resolvePosterUrl(anime)
                isLoading = false
            }
        } else {
            resolvedImage = anime.banner ?: anime.poster
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .clickable { onDetailsClick() }
    ) {
        if (isLoading) {
            ShimmerLoadingPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resolvedImage)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ShimmerLoadingPlaceholder(modifier = Modifier.fillMaxSize())
                },
                error = {
                    ErumiImagePlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        title = anime.title
                    )
                }
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BgMain.copy(alpha = 0.6f),
                            BgMain.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TealPrimary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "FEATURED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = BgMain
                    )
                }
                if (anime.releaseYear != null) {
                    Text(
                        text = "${anime.releaseYear}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                if (anime.score != null) {
                    Text(
                        text = "★ ${String.format("%.1f", anime.score)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = WarningAmber, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = anime.englishName ?: anime.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onWatchClick,
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Watch Now",
                        tint = BgMain,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Watch Now",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = BgMain
                    )
                }

                OutlinedButton(
                    onClick = onDetailsClick,
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(BorderSubtle, BorderSubtle))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurfaceElevated)
    )
}

// Shimmer loading animation component
@Composable
fun ShimmerLoadingPlaceholder(
    modifier: Modifier = Modifier
) {
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(shimmerBrush)
    )
}

// Press scale animation wrapper
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PressScale(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "pressScale"
    )
    Box(
        modifier = modifier
            .scale(scale)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        content()
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            tint = TextDim,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = BgMain)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Retry", color = BgMain, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Erumi-branded placeholder for missing anime images.
 * Uses the full body branding image with title on the right and animated shimmer effect.
 */
@Composable
fun ErumiImagePlaceholder(
    modifier: Modifier = Modifier,
    title: String = "Erumi"
) {
    val shimmerColors = listOf(
        Color.Transparent,
        Color.White.copy(alpha = 0.15f),
        Color.Transparent
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

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TealDark.copy(alpha = 0.8f),
                        TealPrimary.copy(alpha = 0.6f)
                    )
                )
            )
    ) {
        // Animated shimmer effect overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmerBrush)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branding image on the left
            AsyncImage(
                model = R.drawable.fullbodybranding,
                contentDescription = "Erumi",
                modifier = Modifier.size(120.dp),
                alpha = 0.8f
            )

            Spacer(modifier = Modifier.width(32.dp))

            // Title on the right
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "ERUMI",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp
                    ),
                    color = BgMain.copy(alpha = 0.9f)
                )
                if (title.isNotBlank() && title != "Erumi") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = BgMain.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
