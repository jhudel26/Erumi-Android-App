package com.erumi.nativeapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.erumi.nativeapp.domain.model.DownloadStatus

@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val englishName: String?,
    val romajiName: String?,
    val poster: String?,
    val banner: String?,
    val description: String?,
    val genres: String, // Comma separated
    val releaseYear: Int?,
    val episodes: Int,
    val score: Double?,
    val status: String?,
    val format: String?,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val animeId: String,
    val episodeNumber: Int,
    val epId: String,
    val title: String,
    val thumbnail: String?
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String, // animeId + "_" + episodeNumber
    val animeId: String,
    val animeTitle: String,
    val poster: String?,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val animeId: String,
    val animeTitle: String,
    val poster: String?,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val animeId: String,
    val title: String,
    val poster: String?,
    val banner: String?,
    val releaseYear: Int?,
    val score: Double?,
    val status: String?,
    val format: String?,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String, // animeId + "_" + episodeNumber
    val animeId: String,
    val animeTitle: String,
    val episodeNumber: Int,
    val poster: String?,
    val streamUrl: String,
    val localFilePath: String?,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)
