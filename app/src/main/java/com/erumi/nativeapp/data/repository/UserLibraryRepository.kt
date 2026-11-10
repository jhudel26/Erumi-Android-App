package com.erumi.nativeapp.data.repository

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.erumi.nativeapp.core.database.AppDatabase
import com.erumi.nativeapp.core.database.entity.*
import com.erumi.nativeapp.core.download.ErumiDownloadService
import com.erumi.nativeapp.core.download.ErumiDownloadUtil
import com.erumi.nativeapp.core.network.StreamRefererRegistry
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.DownloadItem
import com.erumi.nativeapp.domain.model.DownloadStatus
import com.erumi.nativeapp.domain.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.Request
import com.erumi.nativeapp.core.network.ApiClient
import com.erumi.nativeapp.domain.model.SubtitleTrack

class UserLibraryRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context)
) {

    // ── Watch History ──
    val watchHistoryFlow: Flow<List<WatchProgress>> = db.watchHistoryDao().getAllHistoryFlow().map { list ->
        list.map {
            WatchProgress(
                animeId = it.animeId,
                animeTitle = it.animeTitle,
                poster = it.poster,
                episodeNumber = it.episodeNumber,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                updatedAt = it.watchedAt
            )
        }
    }

    suspend fun saveWatchProgress(
        animeId: String,
        animeTitle: String,
        poster: String?,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val historyId = "${animeId}_$episodeNumber"

        // Don't overwrite an existing valid poster with null or blank
        val resolvedPoster = if (!poster.isNullOrBlank()) {
            poster
        } else {
            getPosterForAnime(animeId, animeTitle)
        }

        db.watchHistoryDao().upsertHistory(
            WatchHistoryEntity(
                id = historyId,
                animeId = animeId,
                animeTitle = animeTitle,
                poster = resolvedPoster,
                episodeNumber = episodeNumber,
                positionMs = positionMs,
                durationMs = durationMs,
                watchedAt = now
            )
        )

        // Only put in Continue Watching if not completed (e.g. less than 95% watched)
        val isFinished = durationMs > 0 && (positionMs.toFloat() / durationMs.toFloat()) >= 0.95f
        if (!isFinished && positionMs > 5_000) {
            db.continueWatchingDao().upsertContinueWatching(
                ContinueWatchingEntity(
                    animeId = animeId,
                    animeTitle = animeTitle,
                    poster = resolvedPoster,
                    episodeNumber = episodeNumber,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = now
                )
            )
        } else if (isFinished) {
            db.continueWatchingDao().removeByAnimeId(animeId)
        }
    }

    suspend fun getPosterForAnime(animeId: String, animeTitle: String): String? = withContext(Dispatchers.IO) {
        val cwPoster = db.continueWatchingDao().getByAnimeId(animeId)?.poster
        if (!cwPoster.isNullOrBlank()) return@withContext cwPoster

        val histPoster = db.watchHistoryDao().getHistoryForAnime(animeId).firstOrNull { !it.poster.isNullOrBlank() }?.poster
        if (!histPoster.isNullOrBlank()) return@withContext histPoster

        val dlPoster = db.downloadDao().getDownloadByAnimeId(animeId)
            ?.takeIf { !it.poster.isNullOrBlank() }?.poster
        if (!dlPoster.isNullOrBlank()) return@withContext dlPoster

        val animePoster = db.animeDao().getAnimeById(animeId)?.poster
        if (!animePoster.isNullOrBlank()) return@withContext animePoster

        null
    }

    suspend fun updateContinueWatchingPoster(animeId: String, poster: String) = withContext(Dispatchers.IO) {
        if (poster.isBlank()) return@withContext
        db.continueWatchingDao().updatePosterIfMissing(animeId, poster)
        db.watchHistoryDao().updatePosterIfMissing(animeId, poster)
    }

    suspend fun removeContinueWatching(animeId: String) = withContext(Dispatchers.IO) {
        db.continueWatchingDao().removeByAnimeId(animeId)
    }

    /**
     * Removes one watch-history row and, when Continue Watching points at the same
     * anime + episode, clears that shelf entry so Home stays in sync.
     */
    suspend fun deleteHistoryEntry(animeId: String, episodeNumber: Int) = withContext(Dispatchers.IO) {
        val historyId = "${animeId}_$episodeNumber"
        db.watchHistoryDao().deleteById(historyId)
        val continueItem = db.continueWatchingDao().getByAnimeId(animeId)
        if (continueItem != null && continueItem.episodeNumber == episodeNumber) {
            db.continueWatchingDao().removeByAnimeId(animeId)
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        db.watchHistoryDao().clearAllHistory()
        // Home "Continue Watching" is fed by a separate table; clear it with history.
        db.continueWatchingDao().clearAll()
    }

    // ── Continue Watching Shelf ──
    val continueWatchingFlow: Flow<List<WatchProgress>> = db.continueWatchingDao().getContinueWatchingFlow().map { list ->
        list.map {
            WatchProgress(
                animeId = it.animeId,
                animeTitle = it.animeTitle,
                poster = it.poster,
                episodeNumber = it.episodeNumber,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
                updatedAt = it.updatedAt
            )
        }
    }

    // ── Watchlist ──
    val watchlistFlow: Flow<List<Anime>> = db.watchlistDao().getWatchlistFlow().map { list ->
        list.map {
            Anime(
                id = it.animeId,
                title = it.title,
                poster = it.poster,
                banner = it.banner,
                releaseYear = it.releaseYear,
                score = it.score,
                status = it.status,
                format = it.format
            )
        }
    }

    fun isInWatchlistFlow(animeId: String): Flow<Boolean> = db.watchlistDao().isInWatchlistFlow(animeId)

    suspend fun toggleWatchlist(anime: Anime) = withContext(Dispatchers.IO) {
        val exists = db.watchlistDao().isInWatchlist(anime.id)
        if (exists) {
            db.watchlistDao().removeFromWatchlist(anime.id)
        } else {
            db.watchlistDao().insertWatchlist(
                WatchlistEntity(
                    animeId = anime.id,
                    title = anime.title,
                    poster = anime.poster,
                    banner = anime.banner,
                    releaseYear = anime.releaseYear,
                    score = anime.score,
                    status = anime.status,
                    format = anime.format
                )
            )
        }
    }

    // ── Search History ──
    val recentSearchesFlow: Flow<List<String>> = db.searchHistoryDao().getRecentSearchesFlow().map { list ->
        list.map { it.query }
    }

    suspend fun addSearchQuery(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            db.searchHistoryDao().insertSearch(SearchHistoryEntity(query.trim()))
        }
    }

    suspend fun removeSearchQuery(query: String) = withContext(Dispatchers.IO) {
        db.searchHistoryDao().deleteSearch(query)
    }

    suspend fun clearAllSearches() = withContext(Dispatchers.IO) {
        db.searchHistoryDao().clearAllSearches()
    }

    // ── Downloads ──
    val downloadsFlow: Flow<List<DownloadItem>> = db.downloadDao().getAllDownloadsFlow().map { list ->
        list.map {
            DownloadItem(
                id = it.id,
                animeId = it.animeId,
                animeTitle = it.animeTitle,
                episodeNumber = it.episodeNumber,
                poster = it.poster,
                streamUrl = it.streamUrl,
                localFilePath = it.localFilePath,
                status = it.status,
                progress = it.progress,
                downloadedBytes = it.downloadedBytes,
                totalBytes = it.totalBytes,
                error = it.error
            )
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun startDownload(
        animeId: String,
        animeTitle: String,
        poster: String?,
        episodeNumber: Int,
        streamUrl: String,
        referer: String? = null,
        subtitles: List<SubtitleTrack> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val downloadId = "${animeId}_$episodeNumber"
        android.util.Log.d("ErumiDownload", "startDownload called for $downloadId, url=$streamUrl, referer=$referer")

        // 1. Register the referer in the global registry BEFORE queueing the download,
        //    so the OkHttp interceptor can inject the correct Referer/Origin for every request.
        if (!referer.isNullOrBlank()) {
            StreamRefererRegistry.register(streamUrl, referer)
            StreamRefererRegistry.register(downloadId, referer)
        }

        // 2. Clear any stale Media3 download entry first (avoids "Failed to remove nonexistent"
        //    or stuck DOWNLOADING states from previous failed attempts).
        try {
            DownloadService.sendRemoveDownload(
                context,
                ErumiDownloadService::class.java,
                downloadId,
                false
            )
            android.util.Log.d("ErumiDownload", "Sent remove for $downloadId (stale cleanup)")
        } catch (e: Exception) {
            android.util.Log.d("ErumiDownload", "Remove stale (expected if none): ${e.message}")
        }

        // 3. Wipe stale Room record so the UI starts fresh from PENDING.
        db.downloadDao().deleteDownload(downloadId)

        // 4. Insert fresh PENDING record so the UI shows a spinner immediately.
        val entity = DownloadEntity(
            id = downloadId,
            animeId = animeId,
            animeTitle = animeTitle,
            episodeNumber = episodeNumber,
            poster = poster,
            streamUrl = streamUrl,
            localFilePath = null,
            status = DownloadStatus.PENDING,
            progress = 0
        )
        db.downloadDao().insertOrUpdateDownload(entity)

        // 4b. Download external subtitles locally for 100% offline viewing
        if (subtitles.isNotEmpty()) {
            val subsDir = File(context.applicationContext.getExternalFilesDir(null), "erumi_subtitles")
            if (!subsDir.exists()) subsDir.mkdirs()

            val localSubs = coroutineScope {
                subtitles.mapIndexed { idx, sub ->
                    async {
                        try {
                            val req = Request.Builder()
                                .url(sub.url)
                                .header("User-Agent", com.erumi.nativeapp.core.common.Constants.USER_AGENT)
                                .apply {
                                    if (!referer.isNullOrBlank()) header("Referer", referer)
                                }
                                .build()
                            val body = ApiClient.okHttpClient.newCall(req).execute().use { resp ->
                                resp.body?.string()
                            }
                            if (!body.isNullOrBlank()) {
                                val safeSubName = "${downloadId}_${sub.language.ifBlank { "und" }}_${idx}.vtt"
                                val subFile = File(subsDir, safeSubName)
                                subFile.writeText(body, Charsets.UTF_8)
                                SubtitleTrack(
                                    label = sub.label,
                                    url = Uri.fromFile(subFile).toString(),
                                    language = sub.language
                                )
                            } else null
                        } catch (e: Exception) {
                            android.util.Log.e("ErumiDownload", "Failed to download offline subtitle: ${sub.url}", e)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (localSubs.isNotEmpty()) {
                val jsonFile = File(subsDir, "${downloadId}_subs.json")
                jsonFile.writeText(com.google.gson.Gson().toJson(localSubs), Charsets.UTF_8)
                android.util.Log.d("ErumiDownload", "Saved ${localSubs.size} offline subtitles for $downloadId")
            }
        }

        // 5. Queue the download with Media3.
        try {
            val mimeType = if (streamUrl.contains("m3u8", ignoreCase = true)) {
                MimeTypes.APPLICATION_M3U8
            } else {
                MimeTypes.VIDEO_MP4
            }
            val downloadRequest = DownloadRequest.Builder(downloadId, Uri.parse(streamUrl))
                .setMimeType(mimeType)
                .build()
            DownloadService.sendAddDownload(
                context,
                ErumiDownloadService::class.java,
                downloadRequest,
                true
            )
            android.util.Log.d("ErumiDownload", "sendAddDownload OK for $downloadId")
        } catch (e: Exception) {
            android.util.Log.e("ErumiDownload", "sendAddDownload FAILED for $downloadId", e)
            db.downloadDao().insertOrUpdateDownload(
                entity.copy(status = DownloadStatus.FAILED, error = e.message)
            )
        }
    }

    /** Delete only the Room record (Media3 state untouched). */
    suspend fun deleteFromDbOnly(id: String) = withContext(Dispatchers.IO) {
        db.downloadDao().deleteDownload(id)
    }

    suspend fun getDownloadByEpisode(animeTitle: String, episodeNumber: Int): DownloadItem? = withContext(Dispatchers.IO) {
        val entity = db.downloadDao().getDownloadByEpisode(animeTitle, episodeNumber) ?: return@withContext null
        val downloadId = entity.id
        val subsDir = File(context.applicationContext.getExternalFilesDir(null), "erumi_subtitles")
        val jsonFile = File(subsDir, "${downloadId}_subs.json")
        val offlineSubs: List<SubtitleTrack> = if (jsonFile.exists()) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<SubtitleTrack>>() {}.type
                com.google.gson.Gson().fromJson(jsonFile.readText(Charsets.UTF_8), type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        DownloadItem(
            id = entity.id,
            animeId = entity.animeId,
            animeTitle = entity.animeTitle,
            episodeNumber = entity.episodeNumber,
            poster = entity.poster,
            streamUrl = entity.streamUrl,
            localFilePath = entity.localFilePath,
            status = entity.status,
            progress = entity.progress,
            downloadedBytes = entity.downloadedBytes,
            totalBytes = entity.totalBytes,
            error = entity.error,
            subtitles = offlineSubs
        )
    }

    suspend fun getDownloadById(id: String): DownloadItem? = withContext(Dispatchers.IO) {
        val entity = db.downloadDao().getDownloadById(id) ?: return@withContext null
        val downloadId = entity.id
        val subsDir = File(context.applicationContext.getExternalFilesDir(null), "erumi_subtitles")
        val jsonFile = File(subsDir, "${downloadId}_subs.json")
        val offlineSubs: List<SubtitleTrack> = if (jsonFile.exists()) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<SubtitleTrack>>() {}.type
                com.google.gson.Gson().fromJson(jsonFile.readText(Charsets.UTF_8), type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        DownloadItem(
            id = entity.id,
            animeId = entity.animeId,
            animeTitle = entity.animeTitle,
            episodeNumber = entity.episodeNumber,
            poster = entity.poster,
            streamUrl = entity.streamUrl,
            localFilePath = entity.localFilePath,
            status = entity.status,
            progress = entity.progress,
            downloadedBytes = entity.downloadedBytes,
            totalBytes = entity.totalBytes,
            error = entity.error,
            subtitles = offlineSubs
        )
    }

    @OptIn(UnstableApi::class)
    suspend fun deleteDownload(id: String) = withContext(Dispatchers.IO) {
        db.downloadDao().deleteDownload(id)
        try {
            DownloadService.sendRemoveDownload(
                context,
                ErumiDownloadService::class.java,
                id,
                true
            )
        } catch (e: Exception) {
            // Ignored
        }

        // Clean up offline subtitle files from storage
        try {
            val subsDir = File(context.applicationContext.getExternalFilesDir(null), "erumi_subtitles")
            if (subsDir.exists()) {
                subsDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith(id)) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun pauseDownload(id: String) = withContext(Dispatchers.IO) {
        try {
            ErumiDownloadUtil.pauseDownload(context, id)
            db.downloadDao().insertOrUpdateDownload(
                db.downloadDao().getDownloadById(id)?.copy(
                    status = DownloadStatus.PAUSED
                ) ?: return@withContext
            )
        } catch (e: Exception) {
            android.util.Log.e("UserLibraryRepo", "Failed to pause download: $id", e)
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun resumeDownload(id: String) = withContext(Dispatchers.IO) {
        try {
            ErumiDownloadUtil.resumeDownload(context, id)
            db.downloadDao().insertOrUpdateDownload(
                db.downloadDao().getDownloadById(id)?.copy(
                    status = DownloadStatus.PENDING
                ) ?: return@withContext
            )
        } catch (e: Exception) {
            android.util.Log.e("UserLibraryRepo", "Failed to resume download: $id", e)
        }
    }

    suspend fun pauseAllDownloads() = withContext(Dispatchers.IO) {
        try {
            val activeDownloads = db.downloadDao().getAllDownloadsFlow().firstOrNull()?.filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
            } ?: emptyList()
            activeDownloads.forEach { download ->
                pauseDownload(download.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserLibraryRepo", "Failed to pause all downloads", e)
        }
    }

    suspend fun resumeAllDownloads() = withContext(Dispatchers.IO) {
        try {
            val pausedDownloads = db.downloadDao().getAllDownloadsFlow().firstOrNull()?.filter {
                it.status == DownloadStatus.PAUSED
            } ?: emptyList()
            pausedDownloads.forEach { download ->
                resumeDownload(download.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserLibraryRepo", "Failed to resume all downloads", e)
        }
    }

    suspend fun getActiveDownloadCount(): Int = withContext(Dispatchers.IO) {
        try {
            db.downloadDao().getAllDownloadsFlow().firstOrNull()?.count {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
