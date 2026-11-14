package com.erumi.nativeapp.manga.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.erumi.nativeapp.manga.domain.models.MangaDownloadSettings
import com.erumi.nativeapp.manga.domain.models.MangaImageScale
import com.erumi.nativeapp.manga.domain.models.MangaReadingDirection
import com.erumi.nativeapp.manga.domain.models.MangaReadingMode
import com.erumi.nativeapp.manga.domain.models.MangaReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.mangaDataStore: DataStore<Preferences> by preferencesDataStore(name = "erumi_manga_preferences")
private val Context.experienceDataStore: DataStore<Preferences> by preferencesDataStore(name = "erumi_experience_prefs")

enum class ErumiExperience {
    SELECTOR,
    ANIME,
    MANGA
}

class ExperiencePreferences(private val context: Context) {
    private object Keys {
        val LAST_EXPERIENCE = stringPreferencesKey("last_experience")
        val SKIP_SELECTOR = booleanPreferencesKey("skip_selector_on_launch")
        val ANIME_TUTORIAL_COMPLETED = booleanPreferencesKey("anime_tutorial_completed")
        val MANGA_TUTORIAL_COMPLETED = booleanPreferencesKey("manga_tutorial_completed")
    }

    val lastExperience: Flow<ErumiExperience> = context.experienceDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            when (prefs[Keys.LAST_EXPERIENCE]) {
                ErumiExperience.ANIME.name -> ErumiExperience.ANIME
                ErumiExperience.MANGA.name -> ErumiExperience.MANGA
                else -> ErumiExperience.SELECTOR
            }
        }

    val skipSelectorOnLaunch: Flow<Boolean> = context.experienceDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.SKIP_SELECTOR] ?: false }

    val animeTutorialCompleted: Flow<Boolean> = context.experienceDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.ANIME_TUTORIAL_COMPLETED] ?: false }

    val mangaTutorialCompleted: Flow<Boolean> = context.experienceDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.MANGA_TUTORIAL_COMPLETED] ?: false }

    suspend fun setLastExperience(experience: ErumiExperience) {
        context.experienceDataStore.edit {
            it[Keys.LAST_EXPERIENCE] = experience.name
        }
    }

    suspend fun setSkipSelectorOnLaunch(skip: Boolean) {
        context.experienceDataStore.edit {
            it[Keys.SKIP_SELECTOR] = skip
        }
    }

    suspend fun setAnimeTutorialCompleted(completed: Boolean) {
        context.experienceDataStore.edit {
            it[Keys.ANIME_TUTORIAL_COMPLETED] = completed
        }
    }

    suspend fun setMangaTutorialCompleted(completed: Boolean) {
        context.experienceDataStore.edit {
            it[Keys.MANGA_TUTORIAL_COMPLETED] = completed
        }
    }
}

class MangaPreferencesDataStore(private val context: Context) {
    private object Keys {
        val ACTIVE_SOURCE = stringPreferencesKey("active_source")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val READING_DIRECTION = stringPreferencesKey("reading_direction")
        val IMAGE_SCALE = stringPreferencesKey("image_scale")
        val FULLSCREEN = booleanPreferencesKey("fullscreen_reader")
        val AUTO_SAVE = booleanPreferencesKey("auto_save_progress")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val DOWNLOAD_LOCATION = stringPreferencesKey("download_location")
    }

    val activeSourceId: Flow<String> = context.mangaDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.ACTIVE_SOURCE] ?: "manhuaplus" }

    val readerSettings: Flow<MangaReaderSettings> = context.mangaDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            MangaReaderSettings(
                readingMode = prefs[Keys.READING_MODE]?.let {
                    runCatching { MangaReadingMode.valueOf(it) }.getOrDefault(MangaReadingMode.HORIZONTAL)
                } ?: MangaReadingMode.HORIZONTAL,
                readingDirection = prefs[Keys.READING_DIRECTION]?.let {
                    runCatching { MangaReadingDirection.valueOf(it) }.getOrDefault(MangaReadingDirection.LTR)
                } ?: MangaReadingDirection.LTR,
                imageScale = prefs[Keys.IMAGE_SCALE]?.let {
                    runCatching { MangaImageScale.valueOf(it) }.getOrDefault(MangaImageScale.FIT_WIDTH)
                } ?: MangaImageScale.FIT_WIDTH,
                fullscreen = prefs[Keys.FULLSCREEN] ?: true,
                autoSaveProgress = prefs[Keys.AUTO_SAVE] ?: true
            )
        }

    val downloadSettings: Flow<MangaDownloadSettings> = context.mangaDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            MangaDownloadSettings(
                wifiOnly = prefs[Keys.WIFI_ONLY] ?: true,
                downloadLocation = prefs[Keys.DOWNLOAD_LOCATION] ?: "manga_downloads"
            )
        }

    suspend fun setActiveSource(sourceId: String) {
        context.mangaDataStore.edit { it[Keys.ACTIVE_SOURCE] = sourceId }
    }

    suspend fun updateReaderSettings(settings: MangaReaderSettings) {
        context.mangaDataStore.edit {
            it[Keys.READING_MODE] = settings.readingMode.name
            it[Keys.READING_DIRECTION] = settings.readingDirection.name
            it[Keys.IMAGE_SCALE] = settings.imageScale.name
            it[Keys.FULLSCREEN] = settings.fullscreen
            it[Keys.AUTO_SAVE] = settings.autoSaveProgress
        }
    }

    suspend fun updateDownloadSettings(settings: MangaDownloadSettings) {
        context.mangaDataStore.edit {
            it[Keys.WIFI_ONLY] = settings.wifiOnly
            it[Keys.DOWNLOAD_LOCATION] = settings.downloadLocation
        }
    }
}
