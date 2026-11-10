package com.erumi.nativeapp.core.download

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.erumi.nativeapp.core.database.AppDatabase
import com.erumi.nativeapp.core.network.ErumiDataSourceFactory
import com.erumi.nativeapp.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
object ErumiDownloadUtil {

    private const val TAG = "ErumiDownload"
    private const val DOWNLOAD_CONTENT_DIRECTORY = "erumi_downloads"

    // Bound coroutine scope — cancelled in release() to avoid leaks (B3/A5)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var databaseProvider: DatabaseProvider? = null

    @Volatile
    private var downloadCache: SimpleCache? = null

    @Volatile
    private var downloadManager: DownloadManager? = null

    private var progressPollerJob: Job? = null

    private fun startProgressPoller(context: Context, manager: DownloadManager) {
        if (progressPollerJob?.isActive == true) return
        progressPollerJob = scope.launch {
            while (isActive) {
                val currentDownloads = manager.currentDownloads
                val activeDownloads = currentDownloads.filter {
                    it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED || it.state == Download.STATE_RESTARTING
                }
                if (activeDownloads.isEmpty()) {
                    break
                }
                val db = AppDatabase.getInstance(context)
                for (download in activeDownloads) {
                    val rawProgress = download.percentDownloaded
                    val progress = if (rawProgress >= 0f) rawProgress.toInt().coerceIn(0, 100) else 0
                    val status = if (download.state == Download.STATE_DOWNLOADING) DownloadStatus.DOWNLOADING else DownloadStatus.PENDING
                    db.downloadDao().updateProgress(
                        id = download.request.id,
                        status = status,
                        progress = progress,
                        downloadedBytes = download.bytesDownloaded,
                        totalBytes = download.contentLength.coerceAtLeast(0L)
                    )
                }
                delay(1000)
            }
        }
    }

    @Synchronized
    fun getDatabaseProvider(context: Context): DatabaseProvider {
        var provider = databaseProvider
        if (provider == null) {
            provider = StandaloneDatabaseProvider(context.applicationContext)
            databaseProvider = provider
        }
        return provider
    }

    @Synchronized
    fun getDownloadCache(context: Context): SimpleCache {
        var cache = downloadCache
        if (cache == null) {
            val downloadDir = File(context.applicationContext.getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
            cache = SimpleCache(downloadDir, NoOpCacheEvictor(), getDatabaseProvider(context))
            downloadCache = cache
        }
        return cache
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        var manager = downloadManager
        if (manager == null) {
            val upstreamFactory = ErumiDataSourceFactory()
            manager = DownloadManager(
                context.applicationContext,
                getDatabaseProvider(context),
                getDownloadCache(context),
                upstreamFactory,
                Executors.newFixedThreadPool(3) // matches maxParallelDownloads (P7)
            ).apply {
                maxParallelDownloads = 3
                addListener(object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?
                    ) {
                        val stateStr = when (download.state) {
                            0 -> "QUEUED"
                            1 -> "STOPPED"
                            2 -> "DOWNLOADING"
                            3 -> "COMPLETED"
                            4 -> "FAILED"
                            5 -> "REMOVING"
                            6 -> "RESTARTING"
                            else -> "UNKNOWN(${download.state})"
                        }
                        Log.d(TAG, "onDownloadChanged: id=${download.request.id} state=$stateStr bytes=${download.bytesDownloaded}/${download.contentLength} err=${finalException?.javaClass?.simpleName}: ${finalException?.message}")
                        if (finalException != null) {
                            Log.e(TAG, "Download FAILED for ${download.request.id} (state=$stateStr):", finalException)
                        }

                        val status = when (download.state) {
                            Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
                            Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
                            Download.STATE_FAILED -> DownloadStatus.FAILED
                            Download.STATE_STOPPED -> DownloadStatus.PAUSED
                            else -> DownloadStatus.PENDING
                        }
                        val rawProg = download.percentDownloaded
                        val progress = if (rawProg >= 0f) rawProg.toInt().coerceIn(0, 100) else 0
                        val downloadedBytes = download.bytesDownloaded
                        val totalBytes = download.contentLength.coerceAtLeast(0L)

                        scope.launch {
                            val db = AppDatabase.getInstance(context)
                            db.downloadDao().updateProgress(
                                id = download.request.id,
                                status = status,
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )
                        }

                        if (download.state == Download.STATE_DOWNLOADING || download.state == Download.STATE_QUEUED) {
                            startProgressPoller(context, downloadManager)
                        }
                    }

                    override fun onDownloadRemoved(
                        downloadManager: DownloadManager,
                        download: Download
                    ) {
                        Log.d(TAG, "onDownloadRemoved: id=${download.request.id}")
                        scope.launch {
                            val db = AppDatabase.getInstance(context)
                            db.downloadDao().deleteDownload(download.request.id)
                        }
                    }
                })
            }
            downloadManager = manager
        }
        return manager
    }

    fun updateRequirements(context: Context, wifiOnly: Boolean) {
        val req = if (wifiOnly) {
            Requirements(Requirements.NETWORK_UNMETERED)
        } else {
            Requirements(0)
        }
        try {
            getDownloadManager(context).requirements = req
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update download requirements", e)
        }
    }

    fun pauseDownload(context: Context, downloadId: String) {
        try {
            val downloadManager = getDownloadManager(context)
            val currentDownloads = downloadManager.currentDownloads
            val download = currentDownloads.firstOrNull { it.request.id == downloadId }
            if (download != null) {
                // Use 1 as the stop reason for user-initiated pause
                downloadManager.setStopReason(downloadId, 1)
                Log.d(TAG, "Paused download: $downloadId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause download: $downloadId", e)
        }
    }

    fun resumeDownload(context: Context, downloadId: String) {
        try {
            val downloadManager = getDownloadManager(context)
            // Use 0 as the stop reason for none (no stop reason)
            downloadManager.setStopReason(downloadId, 0)
            // Media3 will automatically resume downloads when stop reason is cleared
            Log.d(TAG, "Resumed download: $downloadId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume download: $downloadId", e)
        }
    }

    fun getDownloadState(context: Context, downloadId: String): Int? {
        return try {
            val downloadManager = getDownloadManager(context)
            val currentDownloads = downloadManager.currentDownloads
            currentDownloads.firstOrNull { it.request.id == downloadId }?.state
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get download state: $downloadId", e)
            null
        }
    }

    fun buildReadOnlyCacheDataSource(context: Context, referer: String? = null): DataSource.Factory {
        val app = context.applicationContext
        val upstreamFactory = ErumiDataSourceFactory(customReferer = referer)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(getDownloadCache(app))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Wrap with DefaultDataSource so local file:// (offline subtitles), content:// and asset://
        // are loaded directly via FileDataSource without hitting OkHttp or network!
        return DefaultDataSource.Factory(app, cacheFactory)
    }

    /**
     * Releases the SimpleCache and cancels all coroutines. Call from Application.onTerminate()
     * or a ProcessLifecycleObserver to prevent SimpleCache database corruption (A5).
     */
    fun release() {
        progressPollerJob?.cancel()
        scope.cancel()
        downloadCache?.release()
        downloadCache = null
    }
}
