package com.erumi.nativeapp.manga.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "manga",
    indices = [Index(value = ["sourceId", "url"], unique = true)]
)
data class MangaEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val author: String?,
    val artist: String?,
    val genres: String,
    val status: String,
    val contentType: String,
    val rating: Double?,
    val alternativeTitles: String,
    val latestChapter: String?,
    val chapterCount: Int,
    val updatedAt: Long
)

@Entity(
    tableName = "manga_chapters",
    indices = [Index("mangaId"), Index(value = ["sourceId", "url"], unique = true)]
)
data class MangaChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val sourceId: String,
    val url: String,
    val name: String,
    val number: Float?,
    val uploadedAt: Long?,
    val isRead: Boolean = false
)

@Entity(tableName = "manga_library")
data class MangaLibraryEntity(
    @PrimaryKey val mangaId: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String?,
    val contentType: String,
    val status: String,
    val latestChapter: String?,
    val addedAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "manga_reading_progress")
data class MangaReadingProgressEntity(
    @PrimaryKey val mangaId: String,
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
)

@Entity(tableName = "manga_history")
data class MangaHistoryEntity(
    @PrimaryKey val mangaId: String,
    val sourceId: String,
    val title: String,
    val coverUrl: String?,
    val chapterId: String?,
    val chapterName: String?,
    val pageIndex: Int,
    val openedAt: Long
)

@Entity(
    tableName = "manga_downloads",
    indices = [Index(value = ["chapterId"], unique = true)]
)
data class MangaDownloadEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val sourceId: String,
    val mangaTitle: String,
    val chapterId: String,
    val chapterUrl: String,
    val chapterName: String,
    val coverUrl: String?,
    val status: String,
    val progress: Int,
    val localDir: String?,
    val error: String?,
    val createdAt: Long
)

@Entity(tableName = "manga_search_history")
data class MangaSearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long
)
