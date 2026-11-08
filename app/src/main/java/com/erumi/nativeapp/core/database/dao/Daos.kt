package com.erumi.nativeapp.core.database.dao

import androidx.room.*
import com.erumi.nativeapp.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnime(anime: AnimeEntity)

    @Query("SELECT * FROM anime WHERE id = :id LIMIT 1")
    suspend fun getAnimeById(id: String): AnimeEntity?
}

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getAllHistoryFlow(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId ORDER BY watchedAt DESC")
    suspend fun getHistoryForAnime(animeId: String): List<WatchHistoryEntity>

    @Query("UPDATE watch_history SET poster = :poster WHERE animeId = :animeId AND (poster IS NULL OR poster = '')")
    suspend fun updatePosterIfMissing(animeId: String, poster: String)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllHistory()
}

@Dao
interface ContinueWatchingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContinueWatching(item: ContinueWatchingEntity)

    @Query("SELECT * FROM continue_watching ORDER BY updatedAt DESC")
    fun getContinueWatchingFlow(): Flow<List<ContinueWatchingEntity>>

    @Query("SELECT * FROM continue_watching WHERE animeId = :animeId LIMIT 1")
    suspend fun getByAnimeId(animeId: String): ContinueWatchingEntity?

    @Query("UPDATE continue_watching SET poster = :poster WHERE animeId = :animeId AND (poster IS NULL OR poster = '')")
    suspend fun updatePosterIfMissing(animeId: String, poster: String)

    @Query("DELETE FROM continue_watching WHERE animeId = :animeId")
    suspend fun removeByAnimeId(animeId: String)
}

@Dao
interface WatchlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlist(item: WatchlistEntity)

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getWatchlistFlow(): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE animeId = :animeId)")
    fun isInWatchlistFlow(animeId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE animeId = :animeId)")
    suspend fun isInWatchlist(animeId: String): Boolean

    @Query("DELETE FROM watchlist WHERE animeId = :animeId")
    suspend fun removeFromWatchlist(animeId: String)
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE LOWER(animeTitle) = LOWER(:animeTitle) AND episodeNumber = :episodeNumber LIMIT 1")
    suspend fun getDownloadByEpisode(animeTitle: String, episodeNumber: Int): DownloadEntity?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)

    @Query("UPDATE downloads SET status = :status, progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateProgress(id: String, status: com.erumi.nativeapp.domain.model.DownloadStatus, progress: Int, downloadedBytes: Long, totalBytes: Long)
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 15")
    fun getRecentSearchesFlow(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAllSearches()
}
