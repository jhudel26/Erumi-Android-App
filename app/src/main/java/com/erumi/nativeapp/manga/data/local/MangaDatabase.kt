package com.erumi.nativeapp.manga.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MangaEntity::class,
        MangaChapterEntity::class,
        MangaLibraryEntity::class,
        MangaReadingProgressEntity::class,
        MangaHistoryEntity::class,
        MangaDownloadEntity::class,
        MangaSearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MangaDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): MangaChapterDao
    abstract fun libraryDao(): MangaLibraryDao
    abstract fun progressDao(): MangaReadingProgressDao
    abstract fun historyDao(): MangaHistoryDao
    abstract fun downloadDao(): MangaDownloadDao
    abstract fun searchHistoryDao(): MangaSearchHistoryDao

    companion object {
        const val DATABASE_NAME = "erumi_manga_db"

        @Volatile
        private var INSTANCE: MangaDatabase? = null

        fun getInstance(context: Context): MangaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MangaDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
