package com.erumi.nativeapp.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.erumi.nativeapp.core.common.Constants
import com.erumi.nativeapp.core.database.dao.*
import com.erumi.nativeapp.core.database.entity.*
import com.erumi.nativeapp.domain.model.DownloadStatus

class DatabaseConverters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus {
        return try {
            DownloadStatus.valueOf(value)
        } catch (e: Exception) {
            DownloadStatus.PENDING
        }
    }
}

@Database(
    entities = [
        AnimeEntity::class,
        EpisodeEntity::class,
        WatchHistoryEntity::class,
        ContinueWatchingEntity::class,
        WatchlistEntity::class,
        DownloadEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun animeDao(): AnimeDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
