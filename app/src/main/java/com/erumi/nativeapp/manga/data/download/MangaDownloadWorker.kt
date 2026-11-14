package com.erumi.nativeapp.manga.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.erumi.nativeapp.manga.data.local.MangaDatabase
import com.erumi.nativeapp.manga.data.sources.MangaHttpClient
import com.erumi.nativeapp.manga.data.sources.MangaSourceManager
import com.erumi.nativeapp.manga.domain.models.MangaDownloadStatus
import com.erumi.nativeapp.manga.domain.models.MangaResult
import okhttp3.Request
import java.io.File
import java.net.URLDecoder

class MangaDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val db = MangaDatabase.getInstance(appContext)
    private val sources = MangaSourceManager()

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val entity = db.downloadDao().get(downloadId) ?: return Result.failure()

        db.downloadDao().updateStatus(
            id = downloadId,
            status = MangaDownloadStatus.DOWNLOADING.name,
            progress = 0,
            error = null,
            localDir = entity.localDir
        )

        val source = sources.get(entity.sourceId)
            ?: return fail(downloadId, "Source unavailable: ${entity.sourceId}")

        // URL-decode the chapterUrl to handle any encoding issues
        val decodedChapterUrl = try {
            URLDecoder.decode(entity.chapterUrl, "UTF-8")
        } catch (e: Exception) {
            entity.chapterUrl
        }

        android.util.Log.d("MangaDownload", "Fetching pages from: $decodedChapterUrl")

        // Try the URL as-is first
        val pages = when (val result = source.getChapterPages(decodedChapterUrl)) {
            is MangaResult.Success -> {
                android.util.Log.d("MangaDownload", "Successfully fetched ${result.data.size} pages")
                result.data
            }
            is MangaResult.Error -> {
                android.util.Log.e("MangaDownload", "Failed to fetch pages with original URL: ${result.message}")
                // Try with trailing slash
                val withSlash = if (!decodedChapterUrl.endsWith("/")) "$decodedChapterUrl/" else decodedChapterUrl
                android.util.Log.d("MangaDownload", "Retrying with trailing slash: $withSlash")
                when (val retryResult = source.getChapterPages(withSlash)) {
                    is MangaResult.Success -> {
                        android.util.Log.d("MangaDownload", "Successfully fetched ${retryResult.data.size} pages with trailing slash")
                        retryResult.data
                    }
                    is MangaResult.Error -> {
                        android.util.Log.e("MangaDownload", "Failed to fetch pages with trailing slash: ${retryResult.message}")
                        // Try with .html extension
                        val withHtml = if (decodedChapterUrl.endsWith("/")) decodedChapterUrl.dropLast(1) + ".html" else decodedChapterUrl + ".html"
                        android.util.Log.d("MangaDownload", "Retrying with .html: $withHtml")
                        when (val htmlResult = source.getChapterPages(withHtml)) {
                            is MangaResult.Success -> {
                                android.util.Log.d("MangaDownload", "Successfully fetched ${htmlResult.data.size} pages with .html")
                                htmlResult.data
                            }
                            is MangaResult.Error -> {
                                android.util.Log.e("MangaDownload", "Failed to fetch pages with .html: ${htmlResult.message}")
                                return fail(downloadId, "Chapter not found (tried: original, /, .html)")
                            }
                        }
                    }
                }
            }
        }

        if (pages.isEmpty()) {
            return fail(downloadId, "No pages available to download")
        }

        val dir = File(
            applicationContext.filesDir,
            "manga_downloads/${entity.mangaId}/${entity.chapterId.hashCode()}"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            return fail(downloadId, "Unable to create download directory")
        }

        pages.forEachIndexed { index, page ->
            if (isStopped) {
                db.downloadDao().updateStatus(
                    downloadId,
                    MangaDownloadStatus.CANCELLED.name,
                    index,
                    "Cancelled",
                    dir.absolutePath
                )
                return Result.failure()
            }

            val outFile = File(dir, "%03d.jpg".format(index))
            if (!outFile.exists() || outFile.length() == 0L) {
                val ok = downloadImage(page.imageUrl, outFile, entity.chapterUrl)
                if (!ok) {
                    return fail(downloadId, "Failed downloading page ${index + 1}")
                }
            }

            val progress = ((index + 1) * 100) / pages.size
            db.downloadDao().updateStatus(
                downloadId,
                MangaDownloadStatus.DOWNLOADING.name,
                progress,
                null,
                dir.absolutePath
            )
            setProgress(workDataOf("progress" to progress))
        }

        // Validate: all expected files present and non-empty
        val missing = pages.indices.any { index ->
            val f = File(dir, "%03d.jpg".format(index))
            !f.exists() || f.length() == 0L
        }
        if (missing) {
            return fail(downloadId, "Downloaded chapter failed validation")
        }

        db.downloadDao().updateStatus(
            downloadId,
            MangaDownloadStatus.COMPLETED.name,
            100,
            null,
            dir.absolutePath
        )
        return Result.success()
    }

    private fun downloadImage(url: String, outFile: File, referer: String): Boolean {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MangaHttpClient.USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Referer", referer)
                .build()
            MangaHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val bytes = response.body?.bytes() ?: return false
                if (bytes.isEmpty()) return false
                outFile.outputStream().use { it.write(bytes) }
                true
            }
        }.getOrDefault(false)
    }

    private suspend fun fail(downloadId: String, message: String): Result {
        db.downloadDao().updateStatus(
            downloadId,
            MangaDownloadStatus.FAILED.name,
            0,
            message,
            null
        )
        return Result.failure()
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
    }
}
