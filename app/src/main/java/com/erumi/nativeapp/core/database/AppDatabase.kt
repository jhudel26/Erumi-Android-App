package com.erumi.nativeapp.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    // A4: Room TypeConverter for List<String> to support clean collections
    @TypeConverter
    fun fromStringList(list: List<String>?): String = list?.joinToString(",") ?: ""

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        return if (value.isNullOrBlank()) emptyList() else value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

// Migration from version 1 to 2: Add ImageCacheEntity
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create image_cache table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS image_cache (
                animeId TEXT PRIMARY KEY NOT NULL,
                resolvedPosterUrl TEXT,
                resolvedBannerUrl TEXT,
                source TEXT,
                resolvedAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

// Migration from version 2 to 3: No-op (schema unchanged)
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // No schema changes - this is a version bump only
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
        SearchHistoryEntity::class,
        ImageCacheEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun animeDao(): AnimeDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun imageCacheDao(): ImageCacheDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
