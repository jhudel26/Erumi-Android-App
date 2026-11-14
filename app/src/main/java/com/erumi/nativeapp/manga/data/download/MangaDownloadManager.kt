package com.erumi.nativeapp.manga.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.erumi.nativeapp.manga.data.local.MangaDatabase
import com.erumi.nativeapp.manga.data.local.MangaDownloadEntity
import com.erumi.nativeapp.manga.data.local.MangaPreferencesDataStore
import com.erumi.nativeapp.manga.domain.models.MangaDownloadItem
import com.erumi.nativeapp.manga.domain.models.MangaDownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class MangaDownloadManager(
    private val context: Context,
    private val preferences: MangaPreferencesDataStore = MangaPreferencesDataStore(context)
) {
    private val db = MangaDatabase.getInstance(context)

    fun observeDownloads(): Flow<List<MangaDownloadItem>> =
        db.downloadDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun enqueue(
        mangaId: String,
        sourceId: String,
        mangaTitle: String,
        chapterId: String,
        chapterUrl: String,
        chapterName: String,
        coverUrl: String?
    ): String {
        val existing = db.downloadDao().getByChapter(chapterId)
        if (existing != null && existing.status in setOf("QUEUED", "DOWNLOADING", "COMPLETED")) {
            return existing.id
        }

        val id = existing?.id ?: UUID.nameUUIDFromBytes(chapterId.toByteArray()).toString()
        val entity = MangaDownloadEntity(
            id = id,
            mangaId = mangaId,
            sourceId = sourceId,
            mangaTitle = mangaTitle,
            chapterId = chapterId,
            chapterUrl = chapterUrl,
            chapterName = chapterName,
            coverUrl = coverUrl,
            status = MangaDownloadStatus.QUEUED.name,
            progress = 0,
            localDir = null,
            error = null,
            createdAt = System.currentTimeMillis()
        )
        db.downloadDao().upsert(entity)

        val wifiOnly = preferences.downloadSettings.first().wifiOnly
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<MangaDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(MangaDownloadWorker.KEY_DOWNLOAD_ID to id))
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(id),
            ExistingWorkPolicy.KEEP,
            request
        )
        return id
    }

    suspend fun cancel(downloadId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(downloadId))
        db.downloadDao().updateStatus(
            id = downloadId,
            status = MangaDownloadStatus.CANCELLED.name,
            progress = 0,
            error = "Cancelled",
            localDir = null
        )
    }

    suspend fun delete(downloadId: String) {
        val item = db.downloadDao().get(downloadId)
        item?.localDir?.let { dir ->
            runCatching { File(dir).deleteRecursively() }
        }
        WorkManager.getInstance(context).cancelUniqueWork(workName(downloadId))
        db.downloadDao().delete(downloadId)
    }

    suspend fun storageUsageBytes(): Long {
        val root = File(context.filesDir, "manga_downloads")
        if (!root.exists()) return 0L
        return root.walkBottomUp().filter { it.isFile }.map { it.length() }.sum()
    }

    private fun workName(id: String) = "manga_download_$id"

    private fun MangaDownloadEntity.toDomain() = MangaDownloadItem(
        id = id,
        mangaId = mangaId,
        sourceId = sourceId,
        mangaTitle = mangaTitle,
        chapterId = chapterId,
        chapterUrl = chapterUrl,
        chapterName = chapterName,
        coverUrl = coverUrl,
        status = runCatching { MangaDownloadStatus.valueOf(status) }.getOrDefault(MangaDownloadStatus.QUEUED),
        progress = progress,
        localDir = localDir,
        error = error,
        createdAt = createdAt
    )

    companion object {
        const val WORK_TAG = "erumi_manga_downloads"
    }
}
