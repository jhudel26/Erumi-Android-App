package com.erumi.nativeapp.core.download

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

object DownloadHelper {

    /**
     * Check if the app is exempt from battery optimizations
     * This is important for downloads to continue in the background
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    /**
     * Request to disable battery optimizations for the app
     * Returns the Intent that should be started to request the permission
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getBatteryOptimizationIntent(context: Context): Intent {
        val intent = Intent()
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:${context.packageName}")
        return intent
    }

    /**
     * Check if notifications are enabled (essential for foreground service download progress)
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.areNotificationsEnabled()
        }
    }

    /**
     * Check if downloads can reliably run in background (E4):
     * Requires battery optimization exemption AND notification permissions.
     */
    fun canDownloadInBackground(context: Context): Boolean {
        return isIgnoringBatteryOptimizations(context) && areNotificationsEnabled(context)
    }
}
