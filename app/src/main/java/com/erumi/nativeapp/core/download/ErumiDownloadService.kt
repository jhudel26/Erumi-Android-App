package com.erumi.nativeapp.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import com.erumi.nativeapp.R

@OptIn(UnstableApi::class)
class ErumiDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.app_name,
    0
) {

    companion object {
        const val CHANNEL_ID = "erumi_downloads_channel"
        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val ACTION_PAUSE_DOWNLOAD = "com.erumi.nativeapp.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.erumi.nativeapp.RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.erumi.nativeapp.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }

    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Erumi Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of offline anime episode downloads"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun getDownloadManager(): DownloadManager {
        return ErumiDownloadUtil.getDownloadManager(this)
    }

    override fun getScheduler() = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.app_icon,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
}
