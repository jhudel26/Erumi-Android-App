package com.erumi.nativeapp.manga.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(manga: MangaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MangaEntity>)

    @Query("SELECT * FROM manga WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId AND url = :url LIMIT 1")
    suspend fun getByUrl(sourceId: String, url: String): MangaEntity?
}

@Dao
interface MangaChapterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chapters: List<MangaChapterEntity>)

    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId ORDER BY number DESC, name DESC")
    suspend fun getForManga(mangaId: String): List<MangaChapterEntity>

    @Query("SELECT * FROM manga_chapters WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MangaChapterEntity?

    @Query("UPDATE manga_chapters SET isRead = 1 WHERE id = :chapterId")
    suspend fun markRead(chapterId: String)
}

@Dao
interface MangaLibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MangaLibraryEntity)

    @Query("DELETE FROM manga_library WHERE mangaId = :mangaId")
    suspend fun remove(mangaId: String)

    @Query("SELECT * FROM manga_library ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MangaLibraryEntity>>

    @Query("SELECT * FROM manga_library WHERE mangaId = :mangaId LIMIT 1")
    suspend fun get(mangaId: String): MangaLibraryEntity?

    @Query("SELECT * FROM manga_library WHERE status = :status ORDER BY title ASC")
    fun observeByStatus(status: String): Flow<List<MangaLibraryEntity>>
}

@Dao
interface MangaReadingProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: MangaReadingProgressEntity)

    @Query("SELECT * FROM manga_reading_progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MangaReadingProgressEntity>>

    @Query("SELECT * FROM manga_reading_progress WHERE mangaId = :mangaId LIMIT 1")
    suspend fun get(mangaId: String): MangaReadingProgressEntity?

    @Query("DELETE FROM manga_reading_progress WHERE mangaId = :mangaId")
    suspend fun delete(mangaId: String)

    @Query("DELETE FROM manga_reading_progress")
    suspend fun clear()
}

@Dao
interface MangaHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MangaHistoryEntity)

    @Query("SELECT * FROM manga_history ORDER BY openedAt DESC")
    fun observeAll(): Flow<List<MangaHistoryEntity>>

    @Query("DELETE FROM manga_history WHERE mangaId = :mangaId")
    suspend fun delete(mangaId: String)

    @Query("DELETE FROM manga_history")
    suspend fun clear()
}

@Dao
interface MangaDownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MangaDownloadEntity)

    @Query("SELECT * FROM manga_downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MangaDownloadEntity>>

    @Query("SELECT * FROM manga_downloads WHERE id = :id LIMIT 1")
    suspend fun get(id: String): MangaDownloadEntity?

    @Query("SELECT * FROM manga_downloads WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getByChapter(chapterId: String): MangaDownloadEntity?

    @Query("SELECT * FROM manga_downloads WHERE chapterUrl = :chapterUrl LIMIT 1")
    suspend fun getByChapterUrl(chapterUrl: String): MangaDownloadEntity?

    @Query("DELETE FROM manga_downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE manga_downloads SET status = :status, progress = :progress, error = :error, localDir = :localDir WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, progress: Int, error: String?, localDir: String?)
}

@Dao
interface MangaSearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MangaSearchHistoryEntity)

    @Query("SELECT * FROM manga_search_history ORDER BY searchedAt DESC LIMIT 30")
    fun observeRecent(): Flow<List<MangaSearchHistoryEntity>>

    @Query("DELETE FROM manga_search_history")
    suspend fun clear()

    @Query("DELETE FROM manga_search_history WHERE query = :query")
    suspend fun delete(query: String)
}
