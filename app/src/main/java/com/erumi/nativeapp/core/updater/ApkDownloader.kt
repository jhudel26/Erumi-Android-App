package com.erumi.nativeapp.core.updater

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed class ApkDownloadResult {
    data class Success(val apkFile: File) : ApkDownloadResult()
    data class Error(val message: String, val cause: Throwable? = null) : ApkDownloadResult()
    data object Cancelled : ApkDownloadResult()
}

/**
 * Reliable OkHttp-based APK downloader with progress reporting, HTTP Range resume,
 * insufficient-storage guard, and duplicate-download prevention.
 */
class ApkDownloader(
    private val context: Context,
    private val client: OkHttpClient = UpdateChecker.defaultClient
) {
    private val downloadMutex = Mutex()

    fun updatesDir(): File = File(context.filesDir, UpdateConfig.UPDATES_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    fun targetApkFile(versionCode: Int): File =
        File(updatesDir(), "erumi-v$versionCode.apk")

    fun cleanupOtherVersions(keepVersionCode: Int? = null) {
        val keepName = keepVersionCode?.let { "erumi-v$it.apk" }
        updatesDir().listFiles()?.forEach { file ->
            if (file.name != keepName) {
                runCatching { file.delete() }
            }
        }
    }

    suspend fun download(
        manifest: UpdateManifest,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): ApkDownloadResult = withContext(Dispatchers.IO) {
        if (downloadMutex.isLocked) {
            return@withContext ApkDownloadResult.Error("A download is already in progress.")
        }

        downloadMutex.withLock {
            val finalFile = targetApkFile(manifest.versionCode)
            val partFile = File(updatesDir(), "${finalFile.name}.part")

            // If a completed file already exists, return it so ApkVerifier can validate it.
            if (finalFile.exists() && finalFile.length() > 0L) {
                return@withLock ApkDownloadResult.Success(finalFile)
            }

            try {
                val existingBytes = if (partFile.exists()) partFile.length() else 0L
                val requestBuilder = Request.Builder()
                    .url(manifest.apkUrl)
                    .get()
                if (existingBytes > 0L) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    // Handle 416 Range Not Satisfiable by restarting cleanly
                    if (response.code == 416) {
                        partFile.delete()
                        return@withLock ApkDownloadResult.Error("Download state reset. Please retry.")
                    }
                    if (!response.isSuccessful && response.code != 206) {
                        return@withLock ApkDownloadResult.Error(
                            "Download failed (HTTP ${response.code})."
                        )
                    }

                    val body = response.body
                        ?: return@withLock ApkDownloadResult.Error("Empty response from update server.")

                    val isResuming = response.code == 206 && existingBytes > 0L
                    if (!isResuming && partFile.exists()) {
                        partFile.delete()
                    }

                    val contentLength = body.contentLength()
                    val startOffset = if (isResuming) existingBytes else 0L
                    val totalBytes = when {
                        contentLength > 0L -> startOffset + contentLength
                        (manifest.fileSizeBytes ?: 0L) > 0L -> manifest.fileSizeBytes!!
                        else -> -1L
                    }

                    // Check available storage before writing
                    val requiredBytes = if (totalBytes > 0L) {
                        (totalBytes - startOffset).coerceAtLeast(0L) + 10L * 1024L * 1024L
                    } else {
                        50L * 1024L * 1024L
                    }
                    if (!hasEnoughStorage(updatesDir(), requiredBytes)) {
                        return@withLock ApkDownloadResult.Error(
                            "Insufficient device storage to download the update."
                        )
                    }

                    var downloaded = startOffset
                    var lastReportedPercent = -1

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, isResuming).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read

                                val percent = if (totalBytes > 0L) {
                                    ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                if (percent != lastReportedPercent || downloaded == totalBytes) {
                                    lastReportedPercent = percent
                                    onProgress(percent, downloaded, totalBytes)
                                }
                            }
                            output.flush()
                        }
                    }

                    if (!partFile.exists() || partFile.length() <= 0L) {
                        partFile.delete()
                        return@withLock ApkDownloadResult.Error("Downloaded file is empty.")
                    }

                    if (finalFile.exists()) finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        return@withLock ApkDownloadResult.Error("Failed to finalize downloaded APK.")
                    }

                    onProgress(100, finalFile.length(), finalFile.length())
                    ApkDownloadResult.Success(finalFile)
                }
            } catch (e: CancellationException) {
                Log.i(UpdateConfig.TAG, "APK download cancelled")
                ApkDownloadResult.Cancelled
            } catch (e: IOException) {
                Log.w(UpdateConfig.TAG, "APK download IO error: ${e.message}", e)
                ApkDownloadResult.Error("Download interrupted. Check your connection and retry.", e)
            } catch (e: Exception) {
                Log.e(UpdateConfig.TAG, "Unexpected APK download error", e)
                ApkDownloadResult.Error("Download failed: ${e.message ?: "Unknown error"}", e)
            }
        }
    }

    private fun hasEnoughStorage(dir: File, requiredBytes: Long): Boolean {
        return runCatching {
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes >= requiredBytes
        }.getOrDefault(true)
    }
}
