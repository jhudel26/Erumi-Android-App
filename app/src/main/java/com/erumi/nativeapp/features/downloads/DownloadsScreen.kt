package com.erumi.nativeapp.features.downloads

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownloadOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.erumi.nativeapp.core.image.ImageResolver
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.DownloadStatus
import com.erumi.nativeapp.ui.components.ErumiImagePlaceholder
import com.erumi.nativeapp.ui.components.ShimmerLoadingPlaceholder
import com.erumi.nativeapp.ui.theme.BgCard
import com.erumi.nativeapp.ui.theme.BgSurfaceElevated
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiError
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.SuccessGreen
import com.erumi.nativeapp.ui.theme.TealLight
import com.erumi.nativeapp.ui.theme.TextDim
import com.erumi.nativeapp.ui.theme.TextPrimary

@Composable
fun DownloadsScreen(
    onAnimeClick: (String) -> Unit,
    viewModel: DownloadsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var groupToDelete by remember { mutableStateOf<AnimeDownloadGroup?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ErumiBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = ErumiPrimaryText,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Tap any downloaded anime to view and play its episodes",
            style = MaterialTheme.typography.bodyMedium,
            color = ErumiSecondaryText
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Queue management buttons
        if (state.animeGroups.any { it.isAnyDownloading }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.pauseAllDownloads() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BgCard)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PauseCircle,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pause All", color = TextPrimary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else if (state.animeGroups.any { 
            it.episodes.any { it.status == DownloadStatus.PAUSED } 
        }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.resumeAllDownloads() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = ErumiBackground,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resume All", color = ErumiBackground)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.animeGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownloadOff,
                        contentDescription = "No Downloads",
                        tint = TextDim,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No offline downloads yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Download episodes from the anime details page to watch anytime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDim
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = state.animeGroups,
                    key = { it.animeId ?: it.animeTitle }
                ) { group ->
                    AnimeDownloadGroupCard(
                        group = group,
                        // Tapping the card navigates to the Detail page which shows
                        // downloaded episodes inline with play/status indicators
                        onClick = { onAnimeClick(group.animeTitle) },
                        onDeleteClick = { groupToDelete = group },
                        onPauseClick = { downloadId -> viewModel.pauseDownload(downloadId) },
                        onResumeClick = { downloadId -> viewModel.resumeDownload(downloadId) }
                    )
                }
            }
        }
    }

    // Confirmation dialog before deleting grouped episodes
    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = {
                Text(
                    text = "Delete Downloads?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove all ${group.episodes.size} downloaded episode(s) of \"${group.animeTitle}\" from your device?",
                    color = ErumiSecondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAnimeGroup(group)
                        groupToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErumiError)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("Cancel", color = ErumiSecondaryText)
                }
            },
            containerColor = BgCard
        )
    }
}

@Composable
fun AnimeDownloadGroupCard(
    group: AnimeDownloadGroup,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPauseClick: (String) -> Unit = {},
    onResumeClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val imageResolver = remember { ImageResolver.getInstance(context) }
    var resolvedPoster by remember { mutableStateOf(group.poster) }

    LaunchedEffect(group.animeTitle, group.poster) {
        if (group.poster.isNullOrBlank()) {
            val anime = Anime(
                id = group.animeTitle, // Use title as ID since we don't have a real ID
                title = group.animeTitle,
                poster = null
            )
            resolvedPoster = imageResolver.resolvePosterUrl(anime)
        } else {
            resolvedPoster = group.poster
        }
    }

    val sizeMb = group.totalDownloadedBytes / (1024 * 1024)

    // Summarize which episodes are downloaded, e.g. "Ep 1, 2, 3" or "Ep 1-12"
    val epNumbers = group.episodes
        .filter { it.status == DownloadStatus.COMPLETED }
        .map { it.episodeNumber }
        .sorted()

    val episodeSummary = when {
        epNumbers.isEmpty() -> "${group.episodes.size} episode(s) \u2022 In progress"
        epNumbers.size == 1 -> "Episode ${epNumbers.first()} downloaded"
        epNumbers.size <= 4 -> "Ep " + epNumbers.joinToString(", ") + " downloaded"
        else -> "Ep ${epNumbers.first()}-${epNumbers.last()} (${epNumbers.size} episodes)"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 86.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = episodeSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TealLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (group.isAnyDownloading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { (group.overallProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = ErumiPrimary,
                            trackColor = BgSurfaceElevated
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${group.overallProgress}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TealLight
                        )
                    }
                    Text(
                        text = "${group.downloadingCount} downloading \u2022 ${if (sizeMb > 0) "$sizeMb MB" else "In progress"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim
                    )
                } else if (group.failedCount > 0 && group.completedCount == 0) {
                    Text(
                        text = "\u26a0️ Download failed \u2022 Tap to retry",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = ErumiError
                    )
                } else {
                    val statusText = if (sizeMb > 0) {
                        "\u2713 ${group.completedCount} Ready Offline \u2022 $sizeMb MB"
                    } else {
                        "\u2713 ${group.completedCount} Ready Offline"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = SuccessGreen
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Show pause/resume button for individual episodes
            if (group.episodes.size == 1) {
                val download = group.episodes.first()
                if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PENDING) {
                    IconButton(onClick = { onPauseClick(download.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = "Pause Download",
                            tint = ErumiPrimary
                        )
                    }
                } else if (download.status == DownloadStatus.PAUSED) {
                    IconButton(onClick = { onResumeClick(download.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Resume Download",
                            tint = ErumiPrimary
                        )
                    }
                }
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete All Episodes",
                    tint = TextDim
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = "Open Details",
                tint = TextDim,
                modifier = Modifier
                    .size(14.dp)
                    .padding(end = 4.dp)
            )
        }
    }
}
