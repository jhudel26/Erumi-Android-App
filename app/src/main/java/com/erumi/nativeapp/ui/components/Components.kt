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
fun SectionHeader(
    title: String,
    onSeeAllClick: (() -> Unit)? = null
) {
    com.erumi.nativeapp.shared.ui.components.SectionHeader(
        title = title,
        onSeeAllClick = onSeeAllClick
    )
}

@Composable
fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.erumi.nativeapp.shared.ui.components.MediaPosterCard(
        anime = anime,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun ContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.erumi.nativeapp.shared.ui.components.ContinueMediaCard(
        progress = progress,
        onClick = onClick,
        modifier = modifier
    )
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
    val imageResolver = remember { ImageResolver.getInstance(context) }
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
                                    ErumiBackground.copy(alpha = 0.5f),
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
                                0.35f to ErumiBackground.copy(alpha = 0.5f),
                                0.8f to ErumiBackground.copy(alpha = 0.95f),
                                1f to ErumiBackground
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
                                .background(ErumiPrimary)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SPOTLIGHT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = ErumiBackground
                            )
                        }
                        if (anime.releaseYear != null) {
                            Text(
                                text = "${anime.releaseYear}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ErumiSecondaryText
                            )
                        }
                        if (anime.score != null) {
                            Text(
                                text = "\u2605 ${String.format(Locale.US, "%.1f", anime.score)}",
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
                            colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Watch Now",
                                tint = ErumiBackground,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Watch Now",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = ErumiBackground
                            )
                        }

                        OutlinedButton(
                            onClick = { (onDetailsClick ?: onAnimeClick).invoke(anime) },
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
                                if (isSelected) ErumiPrimary else ErumiSecondaryText.copy(alpha = 0.35f)
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
fun SpotlightHeroBanner(
    anime: Anime,
    onWatchClick: () -> Unit,
    onDetailsClick: () -> Unit = onWatchClick,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageResolver = remember { ImageResolver.getInstance(context) }
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
                            ErumiBackground.copy(alpha = 0.6f),
                            ErumiBackground.copy(alpha = 0.95f)
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
                        .background(ErumiPrimary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "FEATURED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = ErumiBackground
                    )
                }
                if (anime.releaseYear != null) {
                    Text(
                        text = "${anime.releaseYear}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErumiSecondaryText
                    )
                }
                if (anime.score != null) {
                    Text(
                        text = "\u2605 ${String.format("%.1f", anime.score)}",
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
                    colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Watch Now",
                        tint = ErumiBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Watch Now",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = ErumiBackground
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
    com.erumi.nativeapp.shared.ui.components.MediaErrorState(
        message = message,
        onRetry = onRetry,
        modifier = modifier
    )
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

    BoxWithConstraints(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TealDark.copy(alpha = 0.8f),
                        ErumiPrimary.copy(alpha = 0.6f)
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

        val isCompactWidth = maxWidth < 180.dp
        val isCompactHeight = maxHeight < 140.dp

        if (isCompactWidth) {
            // Vertical compact layout for narrow cards (e.g. poster cards 120-135dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AsyncImage(
                    model = R.drawable.app_icon,
                    contentDescription = "Erumi",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    alpha = 0.85f
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ERUMI",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = ErumiBackground.copy(alpha = 0.9f)
                )
            }
        } else if (isCompactHeight) {
            // Horizontal compact layout for wide but short cards (e.g. continue watching 115dp)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = R.drawable.app_icon,
                    contentDescription = "Erumi",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    alpha = 0.85f
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "ERUMI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = ErumiBackground.copy(alpha = 0.9f)
                    )
                }
            }
        } else {
            // Full spacious branding layout for banners and large dialogs
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = R.drawable.fullbodybranding,
                    contentDescription = "Erumi",
                    modifier = Modifier.size(120.dp),
                    alpha = 0.8f
                )

                Spacer(modifier = Modifier.width(24.dp))

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
                        color = ErumiBackground.copy(alpha = 0.9f)
                    )
                    if (title.isNotBlank() && title != "Erumi") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = ErumiBackground.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
