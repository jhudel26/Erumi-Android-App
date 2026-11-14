package com.erumi.nativeapp.features.anime_details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.erumi.nativeapp.domain.model.DownloadStatus
import com.erumi.nativeapp.shared.ui.components.DownloadStatusBadge
import com.erumi.nativeapp.shared.ui.components.ShimmerLoader
import com.erumi.nativeapp.shared.ui.components.ShimmerType
import com.erumi.nativeapp.ui.components.ErrorView
import com.erumi.nativeapp.ui.components.ErumiImagePlaceholder
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiBorder
import com.erumi.nativeapp.ui.theme.ErumiElevatedSurface
import com.erumi.nativeapp.ui.theme.ErumiError
import com.erumi.nativeapp.ui.theme.ErumiGold
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondary
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.ErumiSurface

private const val EPISODE_CHUNK_SIZE = 50

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    animeTitle: String,
    onBackClick: () -> Unit,
    onPlayEpisode: (String, Int, Long, String?, String?) -> Unit,
    onRelationClick: (String) -> Unit,
    viewModel: DetailViewModel
) {
    val context = LocalContext.current
    val imageResolver = remember { ImageResolver.getInstance(context) }

    val state by viewModel.uiState.collectAsState()
    var isExpandedSynopsis by remember { mutableStateOf(false) }
    var isGridMode by remember { mutableStateOf(false) }

    // Chunk episodes for sticky headers
    val episodeChunks = remember(state.episodes) {
        if (state.episodes.isEmpty()) emptyList()
        else state.episodes.chunked(EPISODE_CHUNK_SIZE)
    }
    val visibleEpisodes = episodeChunks.getOrNull(state.selectedChunk).orEmpty()

    // Resolve images for the anime
    var resolvedBanner by remember { mutableStateOf<String?>(null) }
    var resolvedPoster by remember { mutableStateOf<String?>(null) }
    var isLoadingImages by remember { mutableStateOf(false) }

    LaunchedEffect(state.anime) {
        state.anime?.let { anime ->
            isLoadingImages = true
            resolvedBanner = imageResolver.resolveBannerUrl(anime)
            resolvedPoster = imageResolver.resolvePosterUrl(anime)
            isLoadingImages = false
        }
    }

    Scaffold(
        containerColor = ErumiBackground,
        topBar = {
            TopAppBar(
                title = {
                    if (state.anime == null) {
                        Text(
                            text = animeTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ErumiPrimaryText)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleWatchlist() },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = if (state.isInWatchlist) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                            contentDescription = "Watchlist",
                            tint = if (state.isInWatchlist) ErumiPrimary else ErumiPrimaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { _ ->
        val anime = state.anime

        if (anime == null && state.isLoading) {
            ShimmerLoader(type = ShimmerType.DETAILS, modifier = Modifier.fillMaxSize())
        } else if (anime == null && state.error != null) {
            ErrorView(message = state.error ?: "Failed to load anime details", onRetry = { viewModel.loadDetails() })
        } else if (anime != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 1. Header Banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        if (isLoadingImages) {
                            ErumiImagePlaceholder(
                                modifier = Modifier.fillMaxSize(),
                                title = anime.title
                            )
                        } else {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(resolvedBanner ?: resolvedPoster)
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Transparent,
                                            ErumiBackground
                                        )
                                    )
                                )
                        )
                    }
                }

                // 2. Poster & Main Info
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.offset(y = (-40).dp)
                        ) {
                            // Poster
                            Box(
                                modifier = Modifier
                                    .width(115.dp)
                                    .height(165.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ErumiElevatedSurface)
                                    .border(1.5.dp, ErumiBorder, RoundedCornerShape(12.dp))
                            ) {
                                if (isLoadingImages) {
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
                            }

                            // Details Text
                            Column(modifier = Modifier.weight(1f)) {
                                Spacer(modifier = Modifier.height(44.dp))
                                Text(
                                    text = anime.englishName ?: anime.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp
                                    )
                                )
                                anime.romajiName?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall.copy(color = ErumiSecondaryText),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (anime.score != null) {
                                        Surface(color = ErumiGold.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = "\u2605 ${String.format(java.util.Locale.US, "%.1f", anime.score)}",
                                                style = MaterialTheme.typography.labelSmall.copy(color = ErumiGold, fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    anime.releaseYear?.let {
                                        Surface(color = ErumiElevatedSurface, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = it.toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(color = ErumiSecondaryText),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Surface(color = ErumiElevatedSurface, shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = anime.status ?: "RELEASING",
                                            style = MaterialTheme.typography.labelSmall.copy(color = ErumiSecondary),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Play Action Button
                        val targetEp = state.lastWatchedEpisode ?: 1
                        val resumeMs = state.lastWatchedPositionMs
                        val buttonText = if (state.lastWatchedEpisode != null) "Resume Ep $targetEp" else "Play Episode 1"

                        Button(
                            // Always play with the user-selected title (nav param), never a
                            // swapped AniList hit — mirrors Erumi streaming android.
                            onClick = { onPlayEpisode(animeTitle, targetEp, resumeMs, anime.id, resolvedPoster) },
                            colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = ErumiBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = buttonText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ErumiBackground
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Genres
                        if (anime.genres.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(anime.genres) { g ->
                                    Surface(
                                        color = ErumiElevatedSurface,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = g,
                                            style = MaterialTheme.typography.labelSmall.copy(color = ErumiSecondaryText),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Synopsis
                        anime.description?.let { desc ->
                            val cleanDesc = desc.replace(Regex("""<[^>]*>"""), "").trim()
                            Text(
                                text = "Synopsis",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = cleanDesc,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = ErumiSecondaryText,
                                    lineHeight = 20.sp
                                ),
                                maxLines = if (isExpandedSynopsis) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (cleanDesc.length > 150) {
                                Text(
                                    text = if (isExpandedSynopsis) "Show Less" else "Read More",
                                    color = ErumiPrimary,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier
                                        .clickable { isExpandedSynopsis = !isExpandedSynopsis }
                                        .padding(vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Franchise Relations (Sequels / Prequels)
                        if (anime.relations.isNotEmpty()) {
                            Text(
                                text = "Franchise & Seasons",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(anime.relations) { rel ->
                                    Surface(
                                        color = ErumiElevatedSurface,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.clickable { onRelationClick(rel.title) }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text(
                                                text = rel.relationType.replace("_", " "),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = ErumiSecondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Text(
                                                text = rel.title,
                                                style = MaterialTheme.typography.bodySmall.copy(color = ErumiPrimaryText),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Episode List Header with Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${state.episodes.size} Episodes",
                                    style = MaterialTheme.typography.labelMedium.copy(color = ErumiSecondaryText)
                                )
                                IconButton(onClick = { isGridMode = !isGridMode }) {
                                    Icon(
                                        imageVector = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                        contentDescription = if (isGridMode) "List View" else "Grid View",
                                        tint = ErumiPrimary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Sticky Episode Chunk Headers
                if (episodeChunks.size > 1) {
                    stickyHeader {
                        Surface(
                            color = ErumiBackground.copy(alpha = 0.96f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                episodeChunks.forEachIndexed { index, chunk ->
                                    val start = chunk.first().episodeNumber
                                    val end = chunk.last().episodeNumber
                                    FilterChip(
                                        selected = state.selectedChunk == index,
                                        onClick = { viewModel.selectChunk(index) },
                                        label = { Text("$start–$end") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ErumiPrimary,
                                            selectedLabelColor = ErumiBackground
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Episodes Grid / List
                if (state.episodes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ErumiPrimary)
                        }
                    }
                } else if (isGridMode) {
                    // Grid Mode: 5 episodes per row
                    val gridChunked = visibleEpisodes.chunked(5)
                    items(gridChunked.size) { rowIndex ->
                        val rowEps = gridChunked[rowIndex]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowEps.forEach { ep ->
                                val isCurrent = state.lastWatchedEpisode == ep.episodeNumber
                                val downloadItem = state.downloadsMap[ep.episodeNumber]
                                EpisodeGridItem(
                                    ep = ep,
                                    isCurrent = isCurrent,
                                    download = downloadItem,
                                    onClick = {
                                        val resume = if (isCurrent) state.lastWatchedPositionMs else 0L
                                        onPlayEpisode(animeTitle, ep.episodeNumber, resume, anime.id, resolvedPoster)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(5 - rowEps.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    // List Mode
                    items(visibleEpisodes) { ep ->
                        val isCurrent = state.lastWatchedEpisode == ep.episodeNumber
                        val downloadItem = state.downloadsMap[ep.episodeNumber]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) ErumiElevatedSurface else ErumiSurface)
                                .border(
                                    1.dp,
                                    if (isCurrent) ErumiPrimary else ErumiBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    val resume = if (isCurrent) state.lastWatchedPositionMs else 0L
                                    onPlayEpisode(animeTitle, ep.episodeNumber, resume, anime.id, resolvedPoster)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isCurrent) ErumiPrimary else ErumiElevatedSurface,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = ep.episodeNumber.toString(),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrent) ErumiBackground else ErumiPrimaryText
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ep.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) ErumiSecondary else ErumiPrimaryText
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    DownloadStatusBadge(
                                        status = downloadItem?.status,
                                        progress = downloadItem?.progress ?: 0,
                                        downloadedBytes = downloadItem?.downloadedBytes ?: 0L,
                                        fallbackText = "Sub / Dub Available"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Download Action Button
                                when (downloadItem?.status) {
                                    DownloadStatus.COMPLETED -> {
                                        IconButton(
                                            onClick = { viewModel.deleteDownload(ep.episodeNumber) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Downloaded (tap to remove)",
                                                tint = ErumiPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                                        Box(
                                            modifier = Modifier.size(36.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (downloadItem.progress > 0) {
                                                CircularProgressIndicator(
                                                    progress = { (downloadItem.progress.toFloat() / 100f).coerceIn(0f, 1f) },
                                                    color = ErumiPrimary,
                                                    strokeWidth = 2.5.dp,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else {
                                                CircularProgressIndicator(
                                                    color = ErumiPrimary,
                                                    strokeWidth = 2.5.dp,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    DownloadStatus.FAILED -> {
                                        IconButton(
                                            onClick = { viewModel.downloadEpisode(ep.episodeNumber) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Retry Download",
                                                tint = ErumiError,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        IconButton(
                                            onClick = { viewModel.downloadEpisode(ep.episodeNumber) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download Episode",
                                                tint = ErumiSecondaryText,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Play Button
                                IconButton(
                                    onClick = {
                                        val resume = if (isCurrent) state.lastWatchedPositionMs else 0L
                                        onPlayEpisode(animeTitle, ep.episodeNumber, resume, anime.id, resolvedPoster)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Play",
                                        tint = if (isCurrent) ErumiPrimary else ErumiSecondaryText,
                                        modifier = Modifier.size(26.dp)
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

// Grid episode item component
@Composable
private fun EpisodeGridItem(
    ep: com.erumi.nativeapp.domain.model.Episode,
    isCurrent: Boolean,
    download: com.erumi.nativeapp.domain.model.DownloadItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) ErumiElevatedSurface else ErumiSurface)
            .border(
                1.dp,
                if (isCurrent) ErumiPrimary else ErumiBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = if (isCurrent) ErumiPrimary else ErumiElevatedSurface,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = ep.episodeNumber.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) ErumiBackground else ErumiPrimaryText
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            
            // Download indicator
            when (download?.status) {
                com.erumi.nativeapp.domain.model.DownloadStatus.COMPLETED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ErumiPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                com.erumi.nativeapp.domain.model.DownloadStatus.DOWNLOADING,
                com.erumi.nativeapp.domain.model.DownloadStatus.PENDING -> {
                    CircularProgressIndicator(
                        progress = { (download.progress.toFloat() / 100f).coerceIn(0f, 1f) },
                        color = ErumiPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                }
                else -> {}
            }
        }
    }
}
