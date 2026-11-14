package com.erumi.nativeapp.manga.presentation.downloads

import android.app.Application
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
import androidx.compose.material.icons.rounded.PauseCircle
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.manga.data.download.MangaDownloadManager
import com.erumi.nativeapp.manga.domain.models.MangaDownloadItem
import com.erumi.nativeapp.manga.domain.models.MangaDownloadStatus
import com.erumi.nativeapp.manga.presentation.components.MangaCoverImage
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MangaDownloadGroup(
    val mangaId: String,
    val mangaTitle: String,
    val coverUrl: String?,
    val chapters: List<MangaDownloadItem>,
    val completedCount: Int,
    val downloadingCount: Int,
    val failedCount: Int,
    val isAnyDownloading: Boolean,
    val overallProgress: Int
)

class MangaDownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = MangaDownloadManager(application)

    val groups = manager.observeDownloads().map { list ->
        list.groupBy { it.mangaTitle.trim() }
            .map { (title, chList) ->
                val sorted = chList.sortedByDescending { it.createdAt }
                val mangaId = sorted.first().mangaId
                val cover = sorted.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
                val completed = sorted.count { it.status == MangaDownloadStatus.COMPLETED }
                val downloading = sorted.count {
                    it.status == MangaDownloadStatus.DOWNLOADING || it.status == MangaDownloadStatus.QUEUED
                }
                val failed = sorted.count { it.status == MangaDownloadStatus.FAILED }
                val active = sorted.filter {
                    it.status == MangaDownloadStatus.DOWNLOADING || it.status == MangaDownloadStatus.QUEUED
                }
                val avgProg = if (active.isNotEmpty()) {
                    active.sumOf { it.progress } / active.size
                } else {
                    100
                }

                MangaDownloadGroup(
                    mangaId = mangaId,
                    mangaTitle = title,
                    coverUrl = cover,
                    chapters = sorted,
                    completedCount = completed,
                    downloadingCount = downloading,
                    failedCount = failed,
                    isAnyDownloading = downloading > 0,
                    overallProgress = avgProg
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun storageBytes(): Long = manager.storageUsageBytes()

    fun deleteGroup(group: MangaDownloadGroup) = viewModelScope.launch {
        group.chapters.forEach { item ->
            manager.delete(item.id)
        }
    }

    fun pauseAll(groups: List<MangaDownloadGroup>) = viewModelScope.launch {
        groups.flatMap { it.chapters }
            .filter { it.status == MangaDownloadStatus.DOWNLOADING || it.status == MangaDownloadStatus.QUEUED }
            .forEach { manager.cancel(it.id) }
    }
}

@Composable
fun MangaDownloadsScreen(
    onMangaClick: (mangaId: String) -> Unit,
    viewModel: MangaDownloadsViewModel = viewModel()
) {
    val groups by viewModel.groups.collectAsState()
    var storage by remember { mutableLongStateOf(0L) }
    var groupToDelete by remember { mutableStateOf<MangaDownloadGroup?>(null) }

    LaunchedEffect(groups) {
        storage = viewModel.storageBytes()
    }

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
            color = TextPrimary,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Tap any downloaded manhua to view and read its chapters",
            style = MaterialTheme.typography.bodyMedium,
            color = ErumiSecondaryText
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Queue management buttons
        if (groups.any { it.isAnyDownloading }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.pauseAll(groups) },
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
        }

        if (groups.isEmpty()) {
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
                        text = "Download chapters from the details page to read anytime",
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
                    items = groups,
                    key = { it.mangaId }
                ) { group ->
                    MangaDownloadGroupCard(
                        group = group,
                        // Tapping the card navigates to the details page, just like Anime
                        onClick = { onMangaClick(group.mangaId) },
                        onDeleteClick = { groupToDelete = group }
                    )
                }
            }
        }
    }

    // Confirmation dialog before deleting grouped chapters
    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = {
                Text(
                    text = "Delete Downloads?",
                    color = ErumiPrimaryText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove all ${group.chapters.size} downloaded chapter(s) of \"${group.mangaTitle}\" from your device?",
                    color = ErumiSecondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(group)
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
            }
        )
    }
}

@Composable
fun MangaDownloadGroupCard(
    group: MangaDownloadGroup,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val completedChapters = group.chapters.filter { it.status == MangaDownloadStatus.COMPLETED }
    val chapterSummary = when {
        completedChapters.isEmpty() -> "${group.chapters.size} chapter(s) \u2022 In progress"
        completedChapters.size == 1 -> "${completedChapters.first().chapterName} downloaded"
        else -> "${completedChapters.size} chapters downloaded"
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
                MangaCoverImage(
                    url = group.coverUrl,
                    contentDescription = group.mangaTitle,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.mangaTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = chapterSummary,
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
                        text = "${group.downloadingCount} downloading \u2022 In progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim
                    )
                } else if (group.failedCount > 0 && group.completedCount == 0) {
                    Text(
                        text = "\u26a0️ Download failed \u2022 Tap to open",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = ErumiError
                    )
                } else {
                    Text(
                        text = "\u2713 ${group.completedCount} Ready Offline",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = SuccessGreen
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete All Chapters",
                    tint = TextDim
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = "Open Details",
                tint = TextDim,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 4.dp)
            )
        }
    }
}
