package com.erumi.nativeapp.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.erumi.nativeapp.core.common.Constants
import com.erumi.nativeapp.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.DATASTORE_PREFS_NAME)

class UserPreferencesDataStore(private val context: Context) {

    private object PreferencesKeys {
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val AUTO_NEXT_EPISODE = booleanPreferencesKey("auto_next_episode")
        val DEFAULT_AUDIO = stringPreferencesKey("default_audio")
        val DEFAULT_SUBTITLE_LANG = stringPreferencesKey("default_subtitle_lang")
        val SUBTITLE_SIZE = floatPreferencesKey("subtitle_size")
        val SKIP_INTRO = booleanPreferencesKey("skip_intro")
        val SKIP_INTRO_SECONDS = intPreferencesKey("skip_intro_seconds")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val APP_THEME = stringPreferencesKey("app_theme")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserSettings(
                preferredQuality = preferences[PreferencesKeys.PREFERRED_QUALITY] ?: Constants.DEFAULT_PREFERRED_QUALITY,
                autoNextEpisode = preferences[PreferencesKeys.AUTO_NEXT_EPISODE] ?: Constants.DEFAULT_AUTO_NEXT,
                defaultAudio = preferences[PreferencesKeys.DEFAULT_AUDIO] ?: Constants.DEFAULT_AUDIO_LANG,
                defaultSubtitleLang = preferences[PreferencesKeys.DEFAULT_SUBTITLE_LANG] ?: Constants.DEFAULT_SUB_LANG,
                subtitleSize = preferences[PreferencesKeys.SUBTITLE_SIZE] ?: 1.0f,
                skipIntro = preferences[PreferencesKeys.SKIP_INTRO] ?: Constants.DEFAULT_SKIP_INTRO,
                skipIntroSeconds = preferences[PreferencesKeys.SKIP_INTRO_SECONDS] ?: Constants.DEFAULT_SKIP_INTRO_SECONDS,
                wifiOnlyDownloads = preferences[PreferencesKeys.WIFI_ONLY_DOWNLOADS] ?: false,
                appTheme = preferences[PreferencesKeys.APP_THEME] ?: "Dark Cinema"
            )
        }

    suspend fun updatePreferredQuality(quality: String) {
        context.dataStore.edit { it[PreferencesKeys.PREFERRED_QUALITY] = quality }
    }

    suspend fun updateAutoNextEpisode(autoNext: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_NEXT_EPISODE] = autoNext }
    }

    suspend fun updateDefaultAudio(audio: String) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_AUDIO] = audio }
    }

    suspend fun updateDefaultSubtitleLang(lang: String) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_SUBTITLE_LANG] = lang }
    }

    suspend fun updateSubtitleSize(size: Float) {
        context.dataStore.edit { it[PreferencesKeys.SUBTITLE_SIZE] = size }
    }

    suspend fun updateSkipIntro(skip: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SKIP_INTRO] = skip }
    }

    suspend fun updateWifiOnlyDownloads(wifiOnly: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.WIFI_ONLY_DOWNLOADS] = wifiOnly }
    }

    suspend fun updateAppTheme(theme: String) {
        context.dataStore.edit { it[PreferencesKeys.APP_THEME] = theme }
    }

    suspend fun updateSkipIntroSeconds(seconds: Int) {
        context.dataStore.edit { it[PreferencesKeys.SKIP_INTRO_SECONDS] = seconds }
    }
}