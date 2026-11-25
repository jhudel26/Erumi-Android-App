package com.erumi.nativeapp.core.updater

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "erumi_update_prefs")

data class UpdateStateSnapshot(
    val lastSuccessfulCheckAtMs: Long = 0L,
    val dismissedVersionCode: Int = 0,
    val pendingApkPath: String? = null,
    val pendingVersionCode: Int = 0,
    val pendingSha256: String? = null
)

class UpdatePreferences(private val context: Context) {

    private object Keys {
        val LAST_CHECK_AT_MS = longPreferencesKey("last_check_at_ms")
        val DISMISSED_VERSION_CODE = intPreferencesKey("dismissed_version_code")
        val PENDING_APK_PATH = stringPreferencesKey("pending_apk_path")
        val PENDING_VERSION_CODE = intPreferencesKey("pending_version_code")
        val PENDING_SHA256 = stringPreferencesKey("pending_sha256")
    }

    val snapshotFlow: Flow<UpdateStateSnapshot> = context.updateDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            UpdateStateSnapshot(
                lastSuccessfulCheckAtMs = prefs[Keys.LAST_CHECK_AT_MS] ?: 0L,
                dismissedVersionCode = prefs[Keys.DISMISSED_VERSION_CODE] ?: 0,
                pendingApkPath = prefs[Keys.PENDING_APK_PATH],
                pendingVersionCode = prefs[Keys.PENDING_VERSION_CODE] ?: 0,
                pendingSha256 = prefs[Keys.PENDING_SHA256]
            )
        }

    suspend fun getSnapshot(): UpdateStateSnapshot = snapshotFlow.first()

    suspend fun recordSuccessfulCheck(timestampMs: Long = System.currentTimeMillis()) {
        context.updateDataStore.edit { it[Keys.LAST_CHECK_AT_MS] = timestampMs }
    }

    suspend fun setDismissedVersionCode(versionCode: Int) {
        context.updateDataStore.edit { it[Keys.DISMISSED_VERSION_CODE] = versionCode }
    }

    suspend fun savePendingApk(path: String, versionCode: Int, sha256: String) {
        context.updateDataStore.edit {
            it[Keys.PENDING_APK_PATH] = path
            it[Keys.PENDING_VERSION_CODE] = versionCode
            it[Keys.PENDING_SHA256] = sha256
        }
    }

    suspend fun clearPendingApk() {
        context.updateDataStore.edit {
            it.remove(Keys.PENDING_APK_PATH)
            it.remove(Keys.PENDING_VERSION_CODE)
            it.remove(Keys.PENDING_SHA256)
        }
    }
}
