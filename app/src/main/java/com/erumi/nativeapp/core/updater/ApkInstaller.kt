package com.erumi.nativeapp.core.updater

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

sealed class InstallLaunchResult {
    data object Launched : InstallLaunchResult()
    data object PermissionRequired : InstallLaunchResult()
    data class Error(val message: String) : InstallLaunchResult()
}

/**
 * Launches Android's native PackageInstaller confirmation UI using a secure
 * FileProvider `content://` URI. Never attempts silent installation.
 */
class ApkInstaller(private val context: Context) {

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createUnknownSourcesSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun launchInstall(apkFile: File): InstallLaunchResult {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return InstallLaunchResult.Error("Installer package is missing.")
        }

        if (!canRequestPackageInstalls()) {
            return InstallLaunchResult.PermissionRequired
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            InstallLaunchResult.Launched
        } catch (e: ActivityNotFoundException) {
            Log.e(UpdateConfig.TAG, "Android package installer unavailable", e)
            InstallLaunchResult.Error("Android package installer is unavailable on this device.")
        } catch (e: Exception) {
            Log.e(UpdateConfig.TAG, "Failed to launch APK installer", e)
            InstallLaunchResult.Error("Unable to start installation: ${e.message ?: "Unknown error"}")
        }
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
