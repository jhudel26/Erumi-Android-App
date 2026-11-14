package com.erumi.nativeapp.manga.presentation.details

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.manga.data.download.MangaDownloadManager
import com.erumi.nativeapp.manga.data.repository.MangaRepository
import com.erumi.nativeapp.manga.domain.models.MangaChapter
import com.erumi.nativeapp.manga.domain.models.MangaDetails
import com.erumi.nativeapp.manga.domain.models.MangaLibraryStatus
import com.erumi.nativeapp.manga.domain.models.MangaReadingProgress
import com.erumi.nativeapp.manga.domain.models.MangaResult
import com.erumi.nativeapp.manga.presentation.components.MangaCoverImage
import com.erumi.nativeapp.shared.ui.components.DownloadStatusBadge
import com.erumi.nativeapp.shared.ui.components.MediaErrorState
import com.erumi.nativeapp.shared.ui.components.ShimmerLoader
import com.erumi.nativeapp.shared.ui.components.ShimmerType
import com.erumi.nativeapp.ui.theme.BgCard
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.SuccessGreen
import com.erumi.nativeapp.ui.theme.TextDim
import com.erumi.nativeapp.ui.theme.TextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder

data class DetailsUiState(
    val details: MangaDetails? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val progress: MangaReadingProgress? = null,
    val inLibrary: Boolean = false,
    val newestFirst: Boolean = true,
    val chapterQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val downloadStatuses: Map<String, String> = emptyMap() // chapterId -> status
)

class MangaDetailsViewModel(
    application: Application,
    private val mangaUrl: String
) : AndroidViewModel(application) {
    private val repo = MangaRepository(application)
    private val downloads = MangaDownloadManager(application)
    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloads.observeDownloads().collect { downloadList ->
                val statusMap = downloadList.associate { it.chapterId to it.status.name }
                _uiState.update { it.copy(downloadStatuses = statusMap) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val detailsResult = repo.getDetails(mangaUrl)) {
                is MangaResult.Success -> {
                    val details = detailsResult.data
                    val chaptersResult = repo.getChapters(mangaUrl, details.manga.id)
                    val chapters = (chaptersResult as? MangaResult.Success)?.data.orEmpty()
                    val progress = repo.getProgress(details.manga.id)
                    val library = repo.getLibraryItem(details.manga.id)
                    repo.recordHistoryOpen(
                        mangaId = details.manga.id,
                        sourceId = details.manga.sourceId,
                        title = details.manga.title,
                        coverUrl = details.manga.coverUrl,
                        chapterId = progress?.chapterId,
                        chapterName = progress?.chapterName,
                        pageIndex = progress?.pageIndex ?: 0
                    )
                    _uiState.update {
                        it.copy(
                            details = details,
                            chapters = chapters,
                            progress = progress,
                            inLibrary = library != null,
                            isLoading = false,
                            error = (chaptersResult as? MangaResult.Error)?.message
                        )
                    }
                }
                is MangaResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = detailsResult.message) }
                }
            }
        }
    }

    fun toggleLibrary() {
        val details = _uiState.value.details ?: return
        viewModelScope.launch {
            if (_uiState.value.inLibrary) {
                repo.removeFromLibrary(details.manga.id)
                _uiState.update { it.copy(inLibrary = false) }
            } else {
                repo.addToLibrary(details.manga, MangaLibraryStatus.READING)
                _uiState.update { it.copy(inLibrary = true) }
            }
        }
    }

    fun toggleSort() {
        _uiState.update { it.copy(newestFirst = !it.newestFirst) }
    }

    fun setChapterQuery(query: String) {
        _uiState.update { it.copy(chapterQuery = query) }
    }

    fun downloadChapter(chapter: MangaChapter) {
        val details = _uiState.value.details ?: return
        viewModelScope.launch {
            downloads.enqueue(
                mangaId = details.manga.id,
                sourceId = details.manga.sourceId,
                mangaTitle = details.manga.title,
                chapterId = chapter.id,
                chapterUrl = chapter.url,
                chapterName = chapter.name,
                coverUrl = details.manga.coverUrl
            )
        }
    }

    fun downloadAllChapters() {
        val details = _uiState.value.details ?: return
        val chapters = _uiState.value.chapters
        val statuses = _uiState.value.downloadStatuses
        viewModelScope.launch {
            chapters.forEach { chapter ->
                val status = statuses[chapter.id]
                if (status != "COMPLETED" && status != "DOWNLOADING" && status != "QUEUED" && !chapter.isDownloaded) {
                    downloads.enqueue(
                        mangaId = details.manga.id,
                        sourceId = details.manga.sourceId,
                        mangaTitle = details.manga.title,
                        chapterId = chapter.id,
                        chapterUrl = chapter.url,
                        chapterName = chapter.name,
                        coverUrl = details.manga.coverUrl
                    )
                }
            }
        }
    }

    class Factory(
        private val application: Application,
        private val mangaUrl: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MangaDetailsViewModel(application, mangaUrl) as T
        }
    }
}

@Composable
fun MangaDetailsScreen(
    mangaUrlEncoded: String,
    onBack: () -> Unit,
    onReadChapter: (mangaUrl: String, chapterUrl: String, mangaId: String, title: String, cover: String?, chapterId: String, chapterName: String, chapterNumber: Float?) -> Unit,
    viewModel: MangaDetailsViewModel = viewModel(
        factory = MangaDetailsViewModel.Factory(
            application = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application,
            mangaUrl = URLDecoder.decode(mangaUrlEncoded, "UTF-8")
        )
    )
) {
    val state by viewModel.uiState.collectAsState()

    when {
        state.isLoading && state.details == null -> ShimmerLoader(
            type = ShimmerType.DETAILS,
            modifier = Modifier.background(ErumiBackground).fillMaxSize()
        )
        state.error != null && state.details == null -> MediaErrorState(state.error!!, onRetry = viewModel::refresh, modifier = Modifier.background(ErumiBackground))
        else -> {
            val details = state.details ?: return
            val filtered = state.chapters
                .filter { state.chapterQuery.isBlank() || it.name.contains(state.chapterQuery, true) }
                .let { if (state.newestFirst) it else it.asReversed() }

            LazyColumn(
                modifier = Modifier.fillMaxSize().background(ErumiBackground),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = viewModel::toggleLibrary) {
                            Icon(
                                if (state.inLibrary) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Library",
                                tint = ErumiPrimary
                            )
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MangaCoverImage(
                            url = details.manga.coverUrl,
                            contentDescription = details.manga.title,
                            modifier = Modifier.width(130.dp).aspectRatio(0.7f)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(details.manga.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            details.author?.let { Text("Author: $it", color = ErumiSecondaryText, fontSize = 13.sp) }
                            Text("Status: ${details.manga.status.name.lowercase().replaceFirstChar { it.titlecase() }}", color = ErumiSecondaryText, fontSize = 13.sp)
                            Text("Type: ${details.manga.contentType.name.lowercase().replaceFirstChar { it.titlecase() }}", color = ErumiSecondaryText, fontSize = 13.sp)
                            val sourceDisplay = when (details.manga.sourceId.lowercase()) {
                                "manhuaplus" -> "ManhuaPlus"
                                "mangadex" -> "MangaDex"
                                "manganato" -> "Manganato"
                                "asurascans" -> "Asura Scans"
                                "flamecomics" -> "Flame Comics"
                                "manhuascan" -> "ManhuaScan"
                                else -> details.manga.sourceId.replaceFirstChar { it.titlecase() }
                            }
                            Text("Source: $sourceDisplay", color = ErumiSecondaryText, fontSize = 13.sp)
                            Text("${details.chapterCount} chapters", color = TextDim, fontSize = 12.sp)
                            if (details.manga.genres.isNotEmpty()) {
                                Text(details.manga.genres.joinToString(" \u2022 "), color = ErumiPrimary, fontSize = 12.sp, maxLines = 2)
                            }
                        }
                    }
                }
                details.description?.takeIf { it.isNotBlank() }?.let { description ->
                    item {
                        var isExpanded by remember { mutableStateOf(false) }
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = description,
                                color = ErumiSecondaryText,
                                fontSize = 14.sp,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (description.length > 120) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isExpanded) "Show Less" else "Read More",
                                    color = ErumiPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { isExpanded = !isExpanded }
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val progress = state.progress
                        val startChapter = progress?.let { p ->
                            state.chapters.firstOrNull { it.id == p.chapterId }
                        } ?: state.chapters.lastOrNull()
                        Button(
                            onClick = {
                                val chapter = startChapter ?: return@Button
                                onReadChapter(
                                    details.manga.url,
                                    chapter.url,
                                    details.manga.id,
                                    details.manga.title,
                                    details.manga.coverUrl,
                                    chapter.id,
                                    chapter.name,
                                    chapter.number
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary),
                            enabled = startChapter != null
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (progress != null) "Continue Reading" else "Start Reading")
                        }
                        OutlinedButton(onClick = viewModel::toggleLibrary) {
                            Text(if (state.inLibrary) "In Library" else "Add to Library")
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chapters", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::downloadAllChapters) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = ErumiPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Download All", color = ErumiPrimary, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = viewModel::toggleSort) {
                            Text(if (state.newestFirst) "Newest" else "Oldest", color = ErumiSecondaryText, fontSize = 13.sp)
                        }
                    }
                }
                items(filtered, key = { it.id }) { chapter ->
                    val downloadStatus = state.downloadStatuses[chapter.id]
                    val isDownloading = downloadStatus == "DOWNLOADING" || downloadStatus == "QUEUED"
                    val isCompleted = downloadStatus == "COMPLETED"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onReadChapter(
                                    details.manga.url,
                                    chapter.url,
                                    details.manga.id,
                                    details.manga.title,
                                    details.manga.coverUrl,
                                    chapter.id,
                                    chapter.name,
                                    chapter.number
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chapter.name,
                                color = if (chapter.isRead) TextDim else TextPrimary,
                                fontWeight = if (chapter.isRead) FontWeight.Normal else FontWeight.Medium
                            )
                            chapter.uploadedAt?.let {
                                Text(
                                    text = java.text.DateFormat.getDateInstance().format(java.util.Date(it)),
                                    color = TextDim,
                                    fontSize = 11.sp
                                )
                            }
                            if (isDownloading) {
                                DownloadStatusBadge(status = com.erumi.nativeapp.domain.model.DownloadStatus.DOWNLOADING)
                            } else if (isCompleted || chapter.isDownloaded) {
                                DownloadStatusBadge(status = com.erumi.nativeapp.domain.model.DownloadStatus.COMPLETED)
                            }
                        }
                        if (!isCompleted && !chapter.isDownloaded) {
                            IconButton(onClick = { viewModel.downloadChapter(chapter) }) {
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = if (isDownloading) ErumiPrimary else ErumiSecondaryText)
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BgCard))
                }
            }
        }
    }
}
