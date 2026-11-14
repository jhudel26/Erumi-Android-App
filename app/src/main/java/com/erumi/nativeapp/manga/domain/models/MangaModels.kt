package com.erumi.nativeapp.manga.domain.models

enum class MangaContentType {
    MANGA,
    MANHWA,
    MANHUA,
    UNKNOWN
}

enum class MangaPublicationStatus {
    ONGOING,
    COMPLETED,
    ON_HOLD,
    CANCELLED,
    UNKNOWN
}

enum class MangaLibraryStatus {
    READING,
    COMPLETED,
    ON_HOLD,
    PLAN_TO_READ,
    DROPPED
}

enum class MangaReadingMode {
    VERTICAL,
    HORIZONTAL
}

enum class MangaReadingDirection {
    LTR,
    RTL
}

enum class MangaImageScale {
    FIT_WIDTH,
    FIT_HEIGHT,
    ORIGINAL
}

typealias MangaDownloadStatus = com.erumi.nativeapp.domain.model.DownloadStatus

enum class MangaSortOption {
    POPULARITY,
    LATEST_UPDATES,
    RECENTLY_ADDED,
    ALPHABETICAL,
    RATING
}

data class Manga(
    val id: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val coverUrl: String? = null,
    val latestChapter: String? = null,
    val status: MangaPublicationStatus = MangaPublicationStatus.UNKNOWN,
    val contentType: MangaContentType = MangaContentType.UNKNOWN,
    val rating: Double? = null,
    val genres: List<String> = emptyList()
)

data class MangaDetails(
    val manga: Manga,
    val alternativeTitles: List<String> = emptyList(),
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val chapterCount: Int = 0,
    val updatedAt: Long? = null
)

data class MangaChapter(
    val id: String,
    val mangaId: String,
    val sourceId: String,
    val url: String,
    val name: String,
    val number: Float? = null,
    val uploadedAt: Long? = null,
    val isRead: Boolean = false,
    val isDownloaded: Boolean = false
) : com.erumi.nativeapp.shared.domain.models.MediaChapter {
    override val chapterId: String get() = id
    override val chapterName: String get() = name
    override val chapterNumber: Float? get() = number
    override val isReadOrWatched: Boolean get() = isRead
    override val isOfflineAvailable: Boolean get() = isDownloaded
}

data class MangaPage(
    val index: Int,
    val imageUrl: String
)

data class MangaReadingProgress(
    val mangaId: String,
    val sourceId: String,
    val mangaTitle: String,
    val coverUrl: String?,
    val chapterId: String,
    val chapterUrl: String,
    val chapterName: String,
    val chapterNumber: Float?,
    val pageIndex: Int,
    val pageCount: Int,
    val percentage: Float,
    val updatedAt: Long
) : com.erumi.nativeapp.shared.domain.models.MediaProgress {
    override val mediaId: String get() = mangaId
    override val mediaTitle: String get() = mangaTitle
    override val mediaCoverUrl: String? get() = coverUrl
    override val mediaSubtitle: String get() = chapterName
    override val progressFraction: Float get() = (percentage / 100f).coerceIn(0f, 1f)
    override val progressPercentage: Int get() = percentage.toInt()
    override val lastUpdated: Long get() = updatedAt
}

data class MangaHistoryEntry(
    val mangaId: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String?,
    val chapterId: String?,
    val chapterName: String?,
    val pageIndex: Int,
    val openedAt: Long
)

data class MangaLibraryItem(
    val mangaId: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String?,
    val contentType: MangaContentType,
    val status: MangaLibraryStatus,
    val latestChapter: String?,
    val addedAt: Long,
    val updatedAt: Long
)

data class MangaDownloadItem(
    val id: String,
    val mangaId: String,
    val sourceId: String,
    val mangaTitle: String,
    val chapterId: String,
    val chapterUrl: String,
    val chapterName: String,
    val coverUrl: String?,
    val status: MangaDownloadStatus,
    val progress: Int,
    val localDir: String?,
    val error: String?,
    val createdAt: Long
)

data class MangaSourceInfo(
    val id: String,
    val name: String,
    val baseUrl: String,
    val supportsSearch: Boolean = true,
    val supportsLatest: Boolean = true,
    val supportsPopular: Boolean = true,
    val supportsGenres: Boolean = true,
    val supportsDownloads: Boolean = true
)

data class MangaPagedResult(
    val items: List<Manga>,
    val hasNextPage: Boolean
)

data class MangaReaderSettings(
    val readingMode: MangaReadingMode = MangaReadingMode.VERTICAL,
    val readingDirection: MangaReadingDirection = MangaReadingDirection.LTR,
    val imageScale: MangaImageScale = MangaImageScale.FIT_WIDTH,
    val fullscreen: Boolean = true,
    val autoSaveProgress: Boolean = true
)

data class MangaDownloadSettings(
    val wifiOnly: Boolean = true,
    val downloadLocation: String = "manga_downloads"
)

sealed class MangaResult<out T> {
    data class Success<T>(val data: T) : MangaResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : MangaResult<Nothing>()
}
