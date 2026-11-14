package com.erumi.nativeapp.manga.presentation.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.erumi.nativeapp.core.image.ImageResolver
import com.erumi.nativeapp.manga.domain.models.Manga
import com.erumi.nativeapp.manga.domain.models.MangaContentType
import com.erumi.nativeapp.manga.domain.models.MangaReadingProgress
import com.erumi.nativeapp.ui.theme.BgCard
import com.erumi.nativeapp.ui.theme.BorderSubtle
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.TealDark
import com.erumi.nativeapp.ui.theme.TextDim
import com.erumi.nativeapp.ui.theme.TextPrimary
import com.erumi.nativeapp.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MangaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null
) {
    com.erumi.nativeapp.shared.ui.components.SectionHeader(
        title = title,
        modifier = modifier,
        onSeeAllClick = onSeeAllClick
    )
}

@Composable
fun MangaCoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(url) {
        if (url.isNullOrBlank()) null else {
            val referer = when {
                url.contains("2xstorage") || url.contains("manganato") || url.contains("manganelo") ||
                url.contains("readmanganato") || url.contains("natomanga") -> "https://www.manganato.gg/"
                url.contains("manhuaplus") -> "https://manhuaplus.org/"
                url.contains("asura") || url.contains("asuracomic") -> "https://asuracomic.net/"
                url.contains("flamecomics") -> "https://flamecomics.xyz/"
                url.contains("manhuascan") || url.contains("manhuaus") -> "https://manhuascan.io/"
                else -> null
            }
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .setHeader("User-Agent", com.erumi.nativeapp.manga.data.sources.MangaHttpClient.USER_AGENT)
                .apply {
                    if (referer != null) {
                        setHeader("Referer", referer)
                    }
                }
                .build()
        }
    }

    SubcomposeAsyncImage(
        model = imageRequest ?: url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
        loading = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(com.erumi.nativeapp.shared.ui.components.rememberShimmerBrush())
            )
        },
        error = {
            Box(Modifier.fillMaxSize().background(BgCard), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextDim)
            }
        }
    )
}

@Composable
fun MangaPosterCard(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.erumi.nativeapp.shared.ui.components.MediaPosterCard(
        manga = manga,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun MangaHorizontalRow(
    items: List<Manga>,
    onMangaClick: (Manga) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.id }) { manga ->
            MangaPosterCard(manga = manga, onClick = { onMangaClick(manga) })
        }
    }
}

@Composable
fun ContinueReadingCard(
    progress: MangaReadingProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.erumi.nativeapp.shared.ui.components.ContinueMediaCard(
        progress = progress,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun MangaErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    com.erumi.nativeapp.shared.ui.components.MediaErrorState(
        message = message,
        onRetry = onRetry,
        modifier = modifier
    )
}

@Composable
fun MangaFilterChipRow(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    com.erumi.nativeapp.shared.ui.components.FilterChipRow(
        options = options,
        selected = selected,
        onSelected = onSelected
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaHeroCarousel(
    spotlightManga: List<Manga>,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    onReadClick: ((Manga) -> Unit)? = null,
    onDetailsClick: ((Manga) -> Unit)? = null
) {
    if (spotlightManga.isEmpty()) return

    val context = LocalContext.current
    val imageResolver = remember { ImageResolver.getInstance(context) }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { spotlightManga.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // Resolve images for spotlight manga
    val resolvedImages = remember { mutableMapOf<String, String?>() }
    val loadingStates = remember { mutableMapOf<String, Boolean>() }
    LaunchedEffect(spotlightManga) {
        spotlightManga.forEach { manga ->
            loadingStates[manga.id] = true
            coroutineScope.launch {
                val resolved = imageResolver.resolveBannerUrl(manga) ?: imageResolver.resolvePosterUrl(manga)
                resolvedImages[manga.id] = resolved
                loadingStates[manga.id] = false
            }
        }
    }

    // Auto-advance pager every 5 seconds when idle
    LaunchedEffect(spotlightManga.size) {
        if (spotlightManga.size > 1) {
            snapshotFlow { pagerState.settledPage }.collectLatest { page ->
                delay(5000L)
                if (!pagerState.isScrollInProgress) {
                    val next = (page + 1) % spotlightManga.size
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
            val manga = spotlightManga.getOrNull(page) ?: return@HorizontalPager
            val resolvedImage = resolvedImages[manga.id] ?: manga.coverUrl
            val isLoading = loadingStates[manga.id] ?: false

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { (onDetailsClick ?: onMangaClick).invoke(manga) }
            ) {
                if (isLoading) {
                    MangaPlaceholder(modifier = Modifier.fillMaxSize(), title = manga.title)
                } else {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(resolvedImage)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            MangaPlaceholder(modifier = Modifier.fillMaxSize(), title = manga.title)
                        },
                        error = {
                            MangaPlaceholder(modifier = Modifier.fillMaxSize(), title = manga.title)
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
                                    com.erumi.nativeapp.ui.theme.ErumiBackground.copy(alpha = 0.5f),
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
                                0.35f to com.erumi.nativeapp.ui.theme.ErumiBackground.copy(alpha = 0.5f),
                                0.8f to com.erumi.nativeapp.ui.theme.ErumiBackground.copy(alpha = 0.95f),
                                1f to com.erumi.nativeapp.ui.theme.ErumiBackground
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
                                color = com.erumi.nativeapp.ui.theme.ErumiBackground
                            )
                        }
                        manga.contentType?.let { type ->
                            if (type != MangaContentType.UNKNOWN) {
                                Text(
                                    text = type.name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDim
                                )
                            }
                        }
                        if (manga.rating != null && manga.rating!! > 0) {
                            Text(
                                text = "★ ${String.format(Locale.US, "%.1f", manga.rating)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = WarningAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { (onReadClick ?: onMangaClick).invoke(manga) },
                            colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Read Now",
                                tint = com.erumi.nativeapp.ui.theme.ErumiBackground,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Read Now",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = com.erumi.nativeapp.ui.theme.ErumiBackground
                            )
                        }

                        OutlinedButton(
                            onClick = { (onDetailsClick ?: onMangaClick).invoke(manga) },
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
        if (spotlightManga.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                spotlightManga.indices.forEach { index ->
                    val isSelected = index == pagerState.currentPage
                    val width = animateDpAsState(
                        targetValue = if (isSelected) 22.dp else 6.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "indicatorWidth"
                    )
                    Box(
                        modifier = Modifier
                            .width(width.value)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isSelected) ErumiPrimary else TextDim.copy(alpha = 0.35f)
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

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MangaPlaceholder(
    modifier: Modifier = Modifier,
    title: String = "Erumi"
) {
    val shimmerColors = listOf(
        Color.Transparent,
        Color.White.copy(alpha = 0.15f),
        Color.Transparent
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200, 0f),
        end = Offset(translateAnim.value + 200, 0f)
    )

    @Suppress("UNUSED_PARAMETER", "BoxWithConstraintsScopeNotUsed")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                com.erumi.nativeapp.ui.components.ErumiImagePlaceholder(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                    title = title
                )
            }
        } else if (isCompactHeight) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.erumi.nativeapp.ui.components.ErumiImagePlaceholder(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    title = title
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.erumi.nativeapp.ui.components.ErumiImagePlaceholder(
                    modifier = Modifier.size(120.dp),
                    title = title
                )
            }
        }
    }
}
