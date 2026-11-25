package com.erumi.nativeapp.core.updater

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Checks the Cloudflare R2 update manifest and evaluates version eligibility
 * using `versionCode` (never `versionName` string comparison).
 */
class UpdateChecker(
    private val context: Context,
    private val client: OkHttpClient = defaultClient,
    private val gson: Gson = Gson()
) {

    suspend fun checkForUpdate(
        manifestUrl: String = UpdateConfig.manifestUrl
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val (installedCode, installedName) = getInstalledVersion()

        runCatching {
            val request = Request.Builder()
                .url(manifestUrl)
                .get()
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error(
                        message = "Update server returned HTTP ${response.code}"
                    )
                }

                val body = response.body?.string()?.trim().orEmpty()
                if (body.isEmpty()) {
                    return@withContext UpdateCheckResult.Error("Empty update manifest")
                }

                val manifest = try {
                    gson.fromJson(body, UpdateManifest::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.w(UpdateConfig.TAG, "Invalid manifest JSON", e)
                    return@withContext UpdateCheckResult.Error("Invalid update metadata", e)
                } ?: return@withContext UpdateCheckResult.Error("Empty update metadata")

                when (val validation = UpdateManifestValidator.validate(manifest)) {
                    is ManifestValidationResult.Invalid -> {
                        Log.w(UpdateConfig.TAG, "Manifest rejected: ${validation.reason}")
                        return@withContext UpdateCheckResult.Error(
                            message = "Update metadata failed security validation: ${validation.reason}"
                        )
                    }
                    ManifestValidationResult.Valid -> Unit
                }

                evaluateManifest(manifest, installedCode, installedName)
            }
        }.getOrElse { throwable ->
            val msg = when (throwable) {
                is IOException -> "Unable to reach update server. Check your internet connection."
                else -> "Failed to check for updates."
            }
            Log.w(UpdateConfig.TAG, "Update check failed: ${throwable.message}", throwable)
            UpdateCheckResult.Error(msg, throwable)
        }
    }

    internal fun evaluateManifest(
        manifest: UpdateManifest,
        installedVersionCode: Long,
        installedVersionName: String
    ): UpdateCheckResult {
        val isUnsupported = installedVersionCode < manifest.minimumSupportedVersion.toLong()
        val isNewerAvailable = manifest.versionCode.toLong() > installedVersionCode

        return if (isNewerAvailable || isUnsupported) {
            UpdateCheckResult.UpdateAvailable(
                manifest = manifest,
                isMandatory = manifest.mandatory || isUnsupported,
                isUnsupportedVersion = isUnsupported,
                installedVersionCode = installedVersionCode,
                installedVersionName = installedVersionName
            )
        } else {
            UpdateCheckResult.UpToDate(installedVersionCode)
        }
    }

    fun getInstalledVersion(): Pair<Long, String> {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        val code = PackageInfoCompat.getLongVersionCode(info)
        val name = info.versionName ?: "0.0.0"
        return code to name
    }

    companion object {
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(UpdateConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(UpdateConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
