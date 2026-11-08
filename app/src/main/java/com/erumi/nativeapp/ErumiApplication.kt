package com.erumi.nativeapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.erumi.nativeapp.core.download.ErumiDownloadService
import com.erumi.nativeapp.core.network.ApiClient

class ErumiApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ErumiDownloadService.CHANNEL_ID,
                "Erumi Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of offline anime episode downloads"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(ApiClient.okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
