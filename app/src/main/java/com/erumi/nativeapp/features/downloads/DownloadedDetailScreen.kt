package com.erumi.nativeapp.features.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownloadOff
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.erumi.nativeapp.core.image.ImageResolver
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.DownloadItem
import com.erumi.nativeapp.domain.model.DownloadStatus
import com.erumi.nativeapp.ui.components.ErumiImagePlaceholder
import com.erumi.nativeapp.ui.components.ShimmerLoadingPlaceholder
import com.erumi.nativeapp.ui.theme.*

@Composable
fun DownloadedDetailScreen(
    animeTitle: String,
    onBackClick: () -> Unit,
    onPlayEpisode: (String, Int, Long, String?, String?) -> Unit,
    onViewOnlineDetails: (String) -> Unit = {},
    viewModel: DownloadsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val group = remember(state.animeGroups, animeTitle) {
        state.animeGroups.firstOrNull {
            it.animeTitle.trim().equals(animeTitle.trim(), ignoreCase = true)
        } ?: state.animeGroups.firstOrNull {
            it.animeId.equals(animeTitle.trim(), ignoreCase = true)
        }
    }

    var episodeToDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgMain,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Offline Downloads",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                if (group != null && group.episodes.isNotEmpty()) {
                    IconButton(onClick = { showDeleteAllDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Delete All",
                            tint = TextDim
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (group == null || group.episodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownloadOff,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No downloaded episodes found",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("Back to Downloads", color = BgMain, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val context = LocalContext.current
            val imageResolver = remember { ImageResolver(context) }
            var resolvedPoster by remember { mutableStateOf(group.poster) }

            LaunchedEffect(group.animeTitle, group.poster) {
                if (group.poster.isNullOrBlank()) {
                    val anime = Anime(
                        id = group.animeId ?: group.animeTitle,
                        title = group.animeTitle,
                        poster = null
                    )
                    resolvedPoster = imageResolver.resolvePosterUrl(anime)
                } else {
                    resolvedPoster = group.poster
                }
            }

            val completedEps = group.episodes.filter { it.status == DownloadStatus.COMPLETED }.sortedBy { it.episodeNumber }
            val firstPlayableEp = completedEps.firstOrNull() ?: group.episodes.firstOrNull()
            val totalMb = group.totalDownloadedBytes / (1024 * 1024)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
            ) {
                // 1. Anime Info Card Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 82.dp, height = 114.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgSurfaceElevated)
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(resolvedPoster)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = group.animeTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    ShimmerLoadingPlaceholder(modifier = Modifier.fillMaxSize())
                                },
                                error = {
                                    ErumiImagePlaceholder(
                                        modifier = Modifier.fillMaxSize(),
                                        title = group.animeTitle
                                    )
                                }
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.animeTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${group.completedCount} Episode(s) Ready Offline",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = SuccessGreen
                                )
                            }

                            if (totalMb > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Total storage: $totalMb MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (firstPlayableEp != null) {
                                Button(
                                    onClick = {
                                        onPlayEpisode(
                                            group.animeTitle,
                                            firstPlayableEp.episodeNumber,
                                            0L,
                                            group.animeId,
                                            group.poster
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = BgMain,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Play Episode ${firstPlayableEp.episodeNumber}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = BgMain
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Section Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Downloaded Episodes (${group.episodes.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Ready to watch offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDim
                        )
                    }
                }

                // 3. Downloaded Episodes List (ONLY downloaded/in-progress episodes)
                items(
                    items = group.episodes.sortedBy { it.episodeNumber },
                    key = { it.id }
                ) { ep ->
                    val epSizeMb = ep.downloadedBytes / (1024 * 1024)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .clickable {
                                if (ep.status == DownloadStatus.COMPLETED) {
                                    onPlayEpisode(
                                        group.animeTitle,
                                        ep.episodeNumber,
                                        0L,
                                        group.animeId,
                                        group.poster
                                    )
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Episode Badge
                            Box(
                                modifier = Modifier
                                    .size(width = 46.dp, height = 46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "EP\n${ep.episodeNumber}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        lineHeight = 13.sp
                                    ),
                                    color = TealPrimary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Episode ${ep.episodeNumber}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                when (ep.status) {
                                    DownloadStatus.COMPLETED -> {
                                        Text(
                                            text = if (epSizeMb > 0) "✓ Ready Offline • $epSizeMb MB" else "✓ Ready Offline",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = SuccessGreen
                                        )
                                    }
                                    DownloadStatus.DOWNLOADING -> {
                                        Column {
                                            LinearProgressIndicator(
                                                progress = { (ep.progress / 100f).coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth(0.8f)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = TealPrimary,
                                                trackColor = BgSurfaceElevated
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Downloading ${ep.progress}% • ${if (epSizeMb > 0) "$epSizeMb MB" else "..."}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TealLight
                                            )
                                        }
                                    }
                                    DownloadStatus.FAILED -> {
                                        Text(
                                            text = "⚠️ Download failed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ErumiError
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "Queued",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextDim
                                        )
                                    }
                                }
                            }
                        }

                        // Right Action Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (ep.status == DownloadStatus.COMPLETED) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(TealPrimary)
                                        .clickable {
                                            onPlayEpisode(
                                                group.animeTitle,
                                                ep.episodeNumber,
                                                0L,
                                                group.animeId,
                                                group.poster
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = BgMain,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { episodeToDelete = ep },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete",
                                    tint = TextDim,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 4. Link to Online Details Page (at the bottom)
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onViewOnlineDetails(group.animeTitle) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(BorderSubtle)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Full Anime Details (Online)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog before deleting single episode
    episodeToDelete?.let { ep ->
        AlertDialog(
            onDismissRequest = { episodeToDelete = null },
            title = {
                Text(
                    text = "Delete Episode ${ep.episodeNumber}?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove this downloaded episode from your device storage?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val remainingCount = (group?.episodes?.size ?: 0) - 1
                        viewModel.deleteDownload(ep.id)
                        episodeToDelete = null
                        if (remainingCount <= 0) {
                            onBackClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErumiError)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { episodeToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }

    // Confirmation dialog before deleting all downloaded episodes for this anime
    if (showDeleteAllDialog && group != null) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text(
                    text = "Delete All Downloads?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all ${group.episodes.size} downloaded episode(s) of \"${group.animeTitle}\"?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAnimeGroup(group)
                        showDeleteAllDialog = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErumiError)
                ) {
                    Text("Delete All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }
}
