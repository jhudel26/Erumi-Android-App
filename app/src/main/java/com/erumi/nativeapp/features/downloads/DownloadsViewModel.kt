package com.erumi.nativeapp.features.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erumi.nativeapp.data.repository.UserLibraryRepository
import com.erumi.nativeapp.domain.model.DownloadItem
import com.erumi.nativeapp.domain.model.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnimeDownloadGroup(
    val animeId: String,
    val animeTitle: String,
    val poster: String?,
    val episodes: List<DownloadItem>,
    val completedCount: Int,
    val downloadingCount: Int,
    val failedCount: Int,
    val totalDownloadedBytes: Long,
    val isAnyDownloading: Boolean,
    val overallProgress: Int
)

data class DownloadsUiState(
    val animeGroups: List<AnimeDownloadGroup> = emptyList(),
    val rawDownloads: List<DownloadItem> = emptyList()
)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepo = UserLibraryRepository(application)
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepo.downloadsFlow.collect { items ->
                val grouped = items
                    .groupBy { it.animeTitle.trim() }
                    .map { (title, epList) ->
                        val sortedEps = epList.sortedBy { it.episodeNumber }
                        val poster = sortedEps.firstOrNull { !it.poster.isNullOrBlank() }?.poster
                        val animeId = sortedEps.first().animeId
                        val completed = sortedEps.count { it.status == DownloadStatus.COMPLETED }
                        val downloading = sortedEps.count {
                            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                        }
                        val failed = sortedEps.count { it.status == DownloadStatus.FAILED }
                        val totalBytes = sortedEps.sumOf { it.downloadedBytes }
                        val isAnyDownloading = downloading > 0
                        val avgProgress = if (downloading > 0) {
                            val activeEps = sortedEps.filter {
                                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                            }
                            activeEps.sumOf { it.progress } / activeEps.size.coerceAtLeast(1)
                        } else {
                            100
                        }

                        AnimeDownloadGroup(
                            animeId = animeId,
                            animeTitle = title,
                            poster = poster,
                            episodes = sortedEps,
                            completedCount = completed,
                            downloadingCount = downloading,
                            failedCount = failed,
                            totalDownloadedBytes = totalBytes,
                            isAnyDownloading = isAnyDownloading,
                            overallProgress = avgProgress
                        )
                    }

                _uiState.update {
                    it.copy(
                        animeGroups = grouped,
                        rawDownloads = items
                    )
                }
            }
        }
    }

    fun deleteAnimeGroup(group: AnimeDownloadGroup) {
        viewModelScope.launch {
            group.episodes.forEach { ep ->
                libraryRepo.deleteDownload(ep.id)
            }
        }
    }

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            libraryRepo.deleteDownload(id)
        }
    }
}
