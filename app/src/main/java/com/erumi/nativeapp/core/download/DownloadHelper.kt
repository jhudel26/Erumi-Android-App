package com.erumi.nativeapp.core.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi

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
     * Check if downloads can run in background
     * Battery optimization + foreground service status
     */
    fun canDownloadInBackground(context: Context): Boolean {
        return isIgnoringBatteryOptimizations(context)
    }
}
