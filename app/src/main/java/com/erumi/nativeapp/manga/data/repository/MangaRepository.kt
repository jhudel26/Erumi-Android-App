package com.erumi.nativeapp.manga.data.repository

import android.content.Context
import com.erumi.nativeapp.manga.data.local.MangaChapterEntity
import com.erumi.nativeapp.manga.data.local.MangaDatabase
import com.erumi.nativeapp.manga.data.local.MangaEntity
import com.erumi.nativeapp.manga.data.local.MangaHistoryEntity
import com.erumi.nativeapp.manga.data.local.MangaLibraryEntity
import com.erumi.nativeapp.manga.data.local.MangaPreferencesDataStore
import com.erumi.nativeapp.manga.data.local.MangaReadingProgressEntity
import com.erumi.nativeapp.manga.data.local.MangaSearchHistoryEntity
import com.erumi.nativeapp.manga.data.sources.MangaSourceManager
import com.erumi.nativeapp.manga.domain.models.Manga
import com.erumi.nativeapp.manga.domain.models.MangaChapter
import com.erumi.nativeapp.manga.domain.models.MangaContentType
import com.erumi.nativeapp.manga.domain.models.MangaDetails
import com.erumi.nativeapp.manga.domain.models.MangaHistoryEntry
import com.erumi.nativeapp.manga.domain.models.MangaLibraryItem
import com.erumi.nativeapp.manga.domain.models.MangaLibraryStatus
import com.erumi.nativeapp.manga.domain.models.MangaPage
import com.erumi.nativeapp.manga.domain.models.MangaPagedResult
import com.erumi.nativeapp.manga.domain.models.MangaPublicationStatus
import com.erumi.nativeapp.manga.domain.models.MangaReadingProgress
import com.erumi.nativeapp.manga.domain.models.MangaResult
import com.erumi.nativeapp.manga.domain.models.MangaSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class MangaRepository(
    context: Context,
    private val sourceManager: MangaSourceManager = MangaSourceManager(),
    private val preferences: MangaPreferencesDataStore = MangaPreferencesDataStore(context)
) {
    private val db = MangaDatabase.getInstance(context)

    suspend fun activeSourceId(): String = preferences.activeSourceId.first()

    fun observeActiveSourceId(): Flow<String> = preferences.activeSourceId

    suspend fun setActiveSource(sourceId: String) {
        sourceManager.setActive(sourceId)
        preferences.setActiveSource(sourceId)
    }

    fun listSources() = sourceManager.getAll()

    private suspend fun source() = sourceManager.require(activeSourceId()).also {
        sourceManager.setActive(it.info.id)
    }

    private suspend fun sourceForUrl(url: String) =
        sourceManager.findSourceForUrl(url) ?: source()

    private suspend fun sourceForManga(mangaId: String, mangaUrl: String) =
        sourceManager.findSourceForMangaId(mangaId) ?: sourceManager.findSourceForUrl(mangaUrl) ?: source()

    suspend fun getPopular(page: Int) = source().getPopular(page).alsoCacheList()
    suspend fun getLatest(page: Int) = source().getLatest(page).alsoCacheList()
    suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult> {
        if (query.isNotBlank()) {
            db.searchHistoryDao().upsert(
                MangaSearchHistoryEntity(query.trim(), System.currentTimeMillis())
            )
        }
        return source().search(query, page).alsoCacheList()
    }

    suspend fun browse(
        page: Int,
        sort: MangaSortOption,
        genre: String? = null,
        status: String? = null
    ) = source().browse(page, sort, genre, status).alsoCacheList()

    suspend fun getGenres(): List<String> = source().supportedGenres()

    suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails> {
        val targetSource = sourceForUrl(mangaUrl)
        return when (val result = targetSource.getDetails(mangaUrl)) {
            is MangaResult.Success -> {
                cacheDetails(result.data)
                result
            }
            is MangaResult.Error -> {
                val local = db.mangaDao().getById(mangaUrl)
                    ?: db.mangaDao().getByUrl(targetSource.info.id, mangaUrl)
                if (local != null) {
                    MangaResult.Success(local.toDomainDetails())
                } else {
                    result
                }
            }
        }
    }

    suspend fun getChapters(mangaUrl: String, mangaId: String): MangaResult<List<MangaChapter>> {
        val targetSource = sourceForManga(mangaId, mangaUrl)
        val downloaded = db.downloadDao().observeAll().map { list ->
            list.filter { it.status == "COMPLETED" }.map { it.chapterId }.toSet()
        }.first()

        return when (val result = targetSource.getChapters(mangaUrl)) {
            is MangaResult.Success -> {
                val chapters = result.data.map { chapter ->
                    val local = db.chapterDao().getById(chapter.id)
                    chapter.copy(
                        isRead = local?.isRead == true,
                        isDownloaded = chapter.id in downloaded
                    )
                }
                db.chapterDao().upsertAll(chapters.map {
                    MangaChapterEntity(
                        id = it.id,
                        mangaId = mangaId,
                        sourceId = it.sourceId,
                        url = it.url,
                        name = it.name,
                        number = it.number,
                        uploadedAt = it.uploadedAt,
                        isRead = it.isRead
                    )
                })
                MangaResult.Success(chapters)
            }
            is MangaResult.Error -> {
                val localChapters = db.chapterDao().getForManga(mangaId)
                if (localChapters.isNotEmpty()) {
                    MangaResult.Success(
                        localChapters.map { entity ->
                            MangaChapter(
                                id = entity.id,
                                mangaId = entity.mangaId,
                                sourceId = entity.sourceId,
                                url = entity.url,
                                name = entity.name,
                                number = entity.number,
                                uploadedAt = entity.uploadedAt,
                                isRead = entity.isRead,
                                isDownloaded = entity.id in downloaded
                            )
                        }
                    )
                } else {
                    result
                }
            }
        }
    }

    suspend fun getPages(chapterUrl: String): MangaResult<List<MangaPage>> {
        // Check if chapter is downloaded
        val download = db.downloadDao().getByChapterUrl(chapterUrl)
        if (download != null && download.status == "COMPLETED" && download.localDir != null) {
            // Load from local files
            val dir = java.io.File(download.localDir)
            if (dir.exists()) {
                val localFiles = dir.listFiles()
                if (!localFiles.isNullOrEmpty()) {
                    return MangaResult.Success(
                        localFiles.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }
                            .mapIndexed { index, file ->
                                MangaPage(index, "file://${file.absolutePath}")
                            }
                    )
                }
            }
        }
        // Fallback to network
        return sourceForUrl(chapterUrl).getChapterPages(chapterUrl)
    }

    fun observeContinueReading(): Flow<List<MangaReadingProgress>> =
        db.progressDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeLibrary(): Flow<List<MangaLibraryItem>> =
        db.libraryDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeHistory(): Flow<List<MangaHistoryEntry>> =
        db.historyDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeSearchHistory(): Flow<List<String>> =
        db.searchHistoryDao().observeRecent().map { list -> list.map { it.query } }

    suspend fun clearSearchHistory() = db.searchHistoryDao().clear()

    suspend fun deleteSearchHistory(query: String) = db.searchHistoryDao().delete(query)

    suspend fun getLibraryItem(mangaId: String): MangaLibraryItem? =
        db.libraryDao().get(mangaId)?.toDomain()

    suspend fun getProgress(mangaId: String): MangaReadingProgress? =
        db.progressDao().get(mangaId)?.toDomain()

    suspend fun addToLibrary(
        manga: Manga,
        status: MangaLibraryStatus = MangaLibraryStatus.READING
    ) {
        val now = System.currentTimeMillis()
        db.libraryDao().upsert(
            MangaLibraryEntity(
                mangaId = manga.id,
                sourceId = manga.sourceId,
                title = manga.title,
                coverUrl = manga.coverUrl,
                contentType = manga.contentType.name,
                status = status.name,
                latestChapter = manga.latestChapter,
                addedAt = db.libraryDao().get(manga.id)?.addedAt ?: now,
                updatedAt = now
            )
        )
    }

    suspend fun updateLibraryStatus(mangaId: String, status: MangaLibraryStatus) {
        val existing = db.libraryDao().get(mangaId) ?: return
        db.libraryDao().upsert(
            existing.copy(status = status.name, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun removeFromLibrary(mangaId: String) = db.libraryDao().remove(mangaId)

    suspend fun saveProgress(progress: MangaReadingProgress) {
        db.progressDao().upsert(
            MangaReadingProgressEntity(
                mangaId = progress.mangaId,
                sourceId = progress.sourceId,
                mangaTitle = progress.mangaTitle,
                coverUrl = progress.coverUrl,
                chapterId = progress.chapterId,
                chapterUrl = progress.chapterUrl,
                chapterName = progress.chapterName,
                chapterNumber = progress.chapterNumber,
                pageIndex = progress.pageIndex,
                pageCount = progress.pageCount,
                percentage = progress.percentage,
                updatedAt = progress.updatedAt
            )
        )
        db.chapterDao().markRead(progress.chapterId)
        db.historyDao().upsert(
            MangaHistoryEntity(
                mangaId = progress.mangaId,
                sourceId = progress.sourceId,
                title = progress.mangaTitle,
                coverUrl = progress.coverUrl,
                chapterId = progress.chapterId,
                chapterName = progress.chapterName,
                pageIndex = progress.pageIndex,
                openedAt = progress.updatedAt
            )
        )
    }

    suspend fun recordHistoryOpen(
        mangaId: String,
        sourceId: String,
        title: String,
        coverUrl: String?,
        chapterId: String?,
        chapterName: String?,
        pageIndex: Int
    ) {
        db.historyDao().upsert(
            MangaHistoryEntity(
                mangaId = mangaId,
                sourceId = sourceId,
                title = title,
                coverUrl = coverUrl,
                chapterId = chapterId,
                chapterName = chapterName,
                pageIndex = pageIndex,
                openedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHistory(mangaId: String) {
        db.historyDao().delete(mangaId)
        db.progressDao().delete(mangaId)
    }

    suspend fun clearHistory() {
        db.historyDao().clear()
        db.progressDao().clear()
    }

    private suspend fun MangaResult<MangaPagedResult>.alsoCacheList(): MangaResult<MangaPagedResult> {
        if (this is MangaResult.Success) {
            db.mangaDao().upsertAll(data.items.map { it.toEntity() })
        }
        return this
    }

    private suspend fun cacheDetails(details: MangaDetails) {
        db.mangaDao().upsert(details.manga.toEntity(details))
    }

    private fun Manga.toEntity(details: MangaDetails? = null) = MangaEntity(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        coverUrl = coverUrl,
        description = details?.description,
        author = details?.author,
        artist = details?.artist,
        genres = genres.joinToString(","),
        status = status.name,
        contentType = contentType.name,
        rating = rating,
        alternativeTitles = details?.alternativeTitles?.joinToString("|").orEmpty(),
        latestChapter = latestChapter,
        chapterCount = details?.chapterCount ?: 0,
        updatedAt = System.currentTimeMillis()
    )

    private fun MangaEntity.toDomainDetails(): MangaDetails {
        val manga = Manga(
            id = id,
            sourceId = sourceId,
            url = url,
            title = title,
            coverUrl = coverUrl,
            latestChapter = latestChapter,
            status = runCatching { MangaPublicationStatus.valueOf(status) }.getOrDefault(MangaPublicationStatus.UNKNOWN),
            contentType = runCatching { MangaContentType.valueOf(contentType) }.getOrDefault(MangaContentType.UNKNOWN),
            rating = rating,
            genres = genres.split(",").filter { it.isNotBlank() }
        )
        return MangaDetails(
            manga = manga,
            alternativeTitles = alternativeTitles.split("|").filter { it.isNotBlank() },
            description = description,
            author = author,
            artist = artist,
            chapterCount = chapterCount,
            updatedAt = updatedAt
        )
    }

    private fun MangaLibraryEntity.toDomain() = MangaLibraryItem(
        mangaId = mangaId,
        sourceId = sourceId,
        title = title,
        coverUrl = coverUrl,
        contentType = runCatching { MangaContentType.valueOf(contentType) }.getOrDefault(MangaContentType.UNKNOWN),
        status = runCatching { MangaLibraryStatus.valueOf(status) }.getOrDefault(MangaLibraryStatus.READING),
        latestChapter = latestChapter,
        addedAt = addedAt,
        updatedAt = updatedAt
    )

    private fun MangaReadingProgressEntity.toDomain() = MangaReadingProgress(
        mangaId = mangaId,
        sourceId = sourceId,
        mangaTitle = mangaTitle,
        coverUrl = coverUrl,
        chapterId = chapterId,
        chapterUrl = chapterUrl,
        chapterName = chapterName,
        chapterNumber = chapterNumber,
        pageIndex = pageIndex,
        pageCount = pageCount,
        percentage = percentage,
        updatedAt = updatedAt
    )

    private fun MangaHistoryEntity.toDomain() = MangaHistoryEntry(
        mangaId = mangaId,
        sourceId = sourceId,
        title = title,
        coverUrl = coverUrl,
        chapterId = chapterId,
        chapterName = chapterName,
        pageIndex = pageIndex,
        openedAt = openedAt
    )
}
