package com.erumi.nativeapp.core.download

import android.app.Notification
import androidx.annotation.OptIn
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
    }

    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
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
