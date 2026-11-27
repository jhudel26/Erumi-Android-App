package com.erumi.nativeapp.manga.data.repository

import android.content.Context
import com.erumi.nativeapp.core.utils.FuzzyMatcher
import com.erumi.nativeapp.manga.data.local.MangaChapterEntity
import com.erumi.nativeapp.manga.data.local.MangaDatabase
import com.erumi.nativeapp.manga.data.local.MangaEntity
import com.erumi.nativeapp.manga.data.local.MangaHistoryEntity
import com.erumi.nativeapp.manga.data.local.MangaLibraryEntity
import com.erumi.nativeapp.manga.data.local.MangaPreferencesDataStore
import com.erumi.nativeapp.manga.data.local.MangaReadingProgressEntity
import com.erumi.nativeapp.manga.data.local.MangaSearchHistoryEntity
import com.erumi.nativeapp.manga.data.sources.MangaSource
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

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

    private suspend fun isAutoMode(): Boolean =
        activeSourceId() == MangaSourceManager.AUTO_SOURCE_ID

    private suspend fun source() = sourceManager.require(activeSourceId()).also {
        if (activeSourceId() != MangaSourceManager.AUTO_SOURCE_ID) {
            sourceManager.setActive(it.info.id)
        }
    }

    private suspend fun sourceForUrl(url: String) =
        sourceManager.findSourceForUrl(url) ?: source()

    private suspend fun sourceForManga(mangaId: String, mangaUrl: String) =
        sourceManager.findSourceForMangaId(mangaId) ?: sourceManager.findSourceForUrl(mangaUrl) ?: source()

    suspend fun getPopular(page: Int): MangaResult<MangaPagedResult> =
        if (isAutoMode()) {
            aggregateSources { it.getPopular(page) }.alsoCacheList()
        } else {
            source().getPopular(page).alsoCacheList()
        }

    suspend fun getLatest(page: Int): MangaResult<MangaPagedResult> =
        if (isAutoMode()) {
            aggregateSources { it.getLatest(page) }.alsoCacheList()
        } else {
            source().getLatest(page).alsoCacheList()
        }

    suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult> {
        if (query.isNotBlank()) {
            db.searchHistoryDao().upsert(
                MangaSearchHistoryEntity(query.trim(), System.currentTimeMillis())
            )
        }
        return if (isAutoMode()) {
            aggregateSources(searchQuery = query) { it.search(query, page) }.alsoCacheList()
        } else {
            source().search(query, page).alsoCacheList()
        }
    }

    suspend fun browse(
        page: Int,
        sort: MangaSortOption,
        genre: String? = null,
        status: String? = null
    ): MangaResult<MangaPagedResult> =
        if (isAutoMode()) {
            aggregateSources { it.browse(page, sort, genre, status) }.alsoCacheList()
        } else {
            source().browse(page, sort, genre, status).alsoCacheList()
        }

    suspend fun getGenres(): List<String> =
        if (isAutoMode()) {
            sourceManager.getAllSources()
                .flatMap { it.supportedGenres() }
                .distinct()
                .sorted()
        } else {
            source().supportedGenres()
        }

    suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails> {
        val cachedById = if (!mangaUrl.startsWith("http", ignoreCase = true)) {
            db.mangaDao().getById(mangaUrl)
        } else null
        val resolvedUrl = cachedById?.url ?: mangaUrl
        val targetSource = if (cachedById != null) {
            sourceForManga(cachedById.id, resolvedUrl)
        } else {
            sourceForUrl(resolvedUrl)
        }
        return when (val result = targetSource.getDetails(resolvedUrl)) {
            is MangaResult.Success -> {
                cacheDetails(result.data)
                result
            }
            is MangaResult.Error -> {
                val local = cachedById
                    ?: db.mangaDao().getById(mangaUrl)
                    ?: db.mangaDao().getByUrl(targetSource.info.id, resolvedUrl)
                if (local != null) {
                    MangaResult.Success(local.toDomainDetails())
                } else {
                    result
                }
            }
        }
    }

    suspend fun getChapters(mangaUrl: String, mangaId: String): MangaResult<List<MangaChapter>> {
        val resolvedUrl = if (!mangaUrl.startsWith("http", ignoreCase = true)) {
            db.mangaDao().getById(mangaId)?.url ?: mangaUrl
        } else {
            mangaUrl
        }
        val targetSource = sourceForManga(mangaId, resolvedUrl)
        val downloaded = db.downloadDao().observeAll().map { list ->
            list.filter { it.status == "COMPLETED" }.map { it.chapterId }.toSet()
        }.first()

        val primaryResult = targetSource.getChapters(resolvedUrl)
        val effectiveResult = if (
            (primaryResult is MangaResult.Error || (primaryResult is MangaResult.Success && primaryResult.data.isEmpty())) &&
            isAutoMode()
        ) {
            val mangaTitle = db.mangaDao().getById(mangaId)?.title
            if (!mangaTitle.isNullOrBlank()) {
                findFallbackChapters(mangaTitle, excludeSourceId = targetSource.info.id) ?: primaryResult
            } else {
                primaryResult
            }
        } else {
            primaryResult
        }

        return when (val result = effectiveResult) {
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
        val primarySource = sourceForUrl(chapterUrl)
        val primaryPages = primarySource.getChapterPages(chapterUrl)
        if (primaryPages is MangaResult.Success && primaryPages.data.isNotEmpty()) {
            return primaryPages
        }

        if (isAutoMode()) {
            val fallbackPages = findFallbackPages(chapterUrl, excludeSourceId = primarySource.info.id)
            if (fallbackPages != null) return fallbackPages
        }
        return primaryPages
    }

    private suspend fun aggregateSources(
        searchQuery: String? = null,
        fetch: suspend (MangaSource) -> MangaResult<MangaPagedResult>
    ): MangaResult<MangaPagedResult> = coroutineScope {
        val sources = sourceManager.getAllSources()
        val results = sources.map { src ->
            async {
                val res = withTimeoutOrNull(9_000L) { fetch(src) }
                src to res
            }
        }.awaitAll()

        val successes = results.mapNotNull { (src, res) ->
            (res as? MangaResult.Success)?.let { src to it.data }
        }
        if (successes.isEmpty()) {
            val firstError = results.firstNotNullOfOrNull { (_, res) -> (res as? MangaResult.Error)?.message }
            return@coroutineScope MangaResult.Error(firstError ?: "All manga sources failed to load")
        }

        val hasNextPage = successes.any { it.second.hasNextPage }
        val merged = mergeAndRankCatalog(successes.map { it.first to it.second.items }, searchQuery)
        MangaResult.Success(MangaPagedResult(items = merged, hasNextPage = hasNextPage))
    }

    private fun mergeAndRankCatalog(
        perSourceItems: List<Pair<MangaSource, List<Manga>>>,
        searchQuery: String? = null
    ): List<Manga> {
        data class Cluster(
            val normalizedKey: String,
            var best: Manga,
            var bestScore: Double,
            var bestRank: Int
        )

        val clusters = mutableListOf<Cluster>()
        val keyToIndex = HashMap<String, Int>()

        perSourceItems.forEach { (_, items) ->
            items.forEachIndexed { index, manga ->
                val norm = FuzzyMatcher.cleanForSearch(manga.title)
                if (norm.isBlank()) return@forEachIndexed

                val candidateScore = scoreMangaCandidate(manga, index, searchQuery)
                val existingIdx = keyToIndex[norm] ?: clusters.indexOfFirst { cluster ->
                    FuzzyMatcher.titleSimilarity(cluster.normalizedKey, norm) >= 0.88
                }.takeIf { it >= 0 }

                if (existingIdx == null) {
                    keyToIndex[norm] = clusters.size
                    clusters.add(Cluster(norm, manga, candidateScore, index))
                } else {
                    val cluster = clusters[existingIdx]
                    if (candidateScore > cluster.bestScore) {
                        cluster.best = manga
                        cluster.bestScore = candidateScore
                    }
                    if (index < cluster.bestRank) {
                        cluster.bestRank = index
                    }
                }
            }
        }

        return clusters
            .sortedWith(
                compareBy<Cluster> {
                    if (!searchQuery.isNullOrBlank()) -(FuzzyMatcher.titleSimilarity(searchQuery, it.best.title) * 100.0)
                    else it.bestRank.toDouble()
                }.thenByDescending { it.bestScore }
            )
            .map { it.best }
    }

    private fun scoreMangaCandidate(manga: Manga, rankIndex: Int, searchQuery: String?): Double {
        var score = sourceManager.sourcePriority(manga.sourceId) * 10.0
        if (!manga.coverUrl.isNullOrBlank()) score += 40.0
        val latestNum = manga.latestChapter?.let {
            Regex("""([0-9]+(?:\.[0-9]+)?)""").find(it)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        } ?: 0f
        score += (latestNum.coerceAtMost(500f) * 0.05)
        score -= (rankIndex * 0.25)
        if (!searchQuery.isNullOrBlank()) {
            score += FuzzyMatcher.titleSimilarity(searchQuery, manga.title) * 100.0
        }
        return score
    }

    private suspend fun findFallbackChapters(
        mangaTitle: String,
        excludeSourceId: String
    ): MangaResult<List<MangaChapter>>? = coroutineScope {
        val candidates = sourceManager.getAllSources()
            .filter { it.info.id != excludeSourceId }
            .sortedByDescending { sourceManager.sourcePriority(it.info.id) }

        val matches = candidates.map { src ->
            async {
                val res = withTimeoutOrNull(7_000L) { src.search(mangaTitle, 1) } as? MangaResult.Success
                val best = res?.data?.items
                    ?.filter { FuzzyMatcher.isValidTitleMatch(mangaTitle, it.title) }
                    ?.maxByOrNull { FuzzyMatcher.titleSimilarity(mangaTitle, it.title) }
                if (best != null && FuzzyMatcher.titleSimilarity(mangaTitle, best.title) >= 0.70) {
                    src to best
                } else null
            }
        }.awaitAll().filterNotNull()

        for ((src, match) in matches) {
            val chRes = withTimeoutOrNull(7_000L) { src.getChapters(match.url) }
            if (chRes is MangaResult.Success && chRes.data.isNotEmpty()) {
                return@coroutineScope chRes
            }
        }
        null
    }

    private suspend fun findFallbackPages(
        chapterUrl: String,
        excludeSourceId: String
    ): MangaResult<List<MangaPage>>? = coroutineScope {
        val chapterEntity = db.chapterDao().getByUrl(chapterUrl) ?: return@coroutineScope null
        val mangaEntity = db.mangaDao().getById(chapterEntity.mangaId) ?: return@coroutineScope null

        val candidates = sourceManager.getAllSources()
            .filter { it.info.id != excludeSourceId }
            .sortedByDescending { sourceManager.sourcePriority(it.info.id) }

        val matches = candidates.map { src ->
            async {
                val res = withTimeoutOrNull(7_000L) { src.search(mangaEntity.title, 1) } as? MangaResult.Success
                val best = res?.data?.items
                    ?.filter { FuzzyMatcher.isValidTitleMatch(mangaEntity.title, it.title) }
                    ?.maxByOrNull { FuzzyMatcher.titleSimilarity(mangaEntity.title, it.title) }
                if (best != null && FuzzyMatcher.titleSimilarity(mangaEntity.title, best.title) >= 0.70) {
                    src to best
                } else null
            }
        }.awaitAll().filterNotNull()

        for ((src, match) in matches) {
            val chRes = withTimeoutOrNull(7_000L) { src.getChapters(match.url) } as? MangaResult.Success ?: continue
            val matchingChapter = chRes.data.firstOrNull { ch ->
                (chapterEntity.number != null && ch.number != null && ch.number == chapterEntity.number) ||
                    ch.name.equals(chapterEntity.name, ignoreCase = true)
            } ?: continue
            val pagesRes = withTimeoutOrNull(8_000L) { src.getChapterPages(matchingChapter.url) }
            if (pagesRes is MangaResult.Success && pagesRes.data.isNotEmpty()) {
                return@coroutineScope pagesRes
            }
        }
        null
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
