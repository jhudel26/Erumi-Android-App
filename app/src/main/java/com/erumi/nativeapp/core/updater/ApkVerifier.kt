package com.erumi.nativeapp.core.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

sealed class ApkVerificationResult {
    data class Verified(val apkFile: File) : ApkVerificationResult()
    data class Rejected(val reason: String) : ApkVerificationResult()
}

/**
 * Mandatory security gate before launching Android's PackageInstaller.
 * Verifies:
 * 1. File existence and non-zero size
 * 2. SHA-256 digest matches the trusted manifest hash
 * 3. Archive packageName matches the installed applicationId
 * 4. Archive signing certificate matches the installed app's signing certificate(s)
 *
 * Invalid or tampered APK files are deleted immediately.
 */
class ApkVerifier(private val context: Context) {

    suspend fun verify(apkFile: File, manifest: UpdateManifest): ApkVerificationResult =
        withContext(Dispatchers.IO) {
            if (!apkFile.exists() || !apkFile.isFile || apkFile.length() <= 0L) {
                deleteQuietly(apkFile)
                return@withContext ApkVerificationResult.Rejected("Downloaded APK file is missing or empty.")
            }

            // 1. SHA-256 verification
            val expectedSha = manifest.sha256.trim().lowercase()
            val actualSha = computeSha256(apkFile)
            if (actualSha == null || !actualSha.equals(expectedSha, ignoreCase = true)) {
                Log.e(UpdateConfig.TAG, "SHA-256 mismatch! expected=$expectedSha actual=$actualSha")
                deleteQuietly(apkFile)
                return@withContext ApkVerificationResult.Rejected(
                    "Security check failed: APK integrity hash mismatch."
                )
            }

            // 2. Archive PackageInfo & Package ID verification
            val archiveInfo = getArchivePackageInfo(apkFile)
            if (archiveInfo == null) {
                deleteQuietly(apkFile)
                return@withContext ApkVerificationResult.Rejected(
                    "Downloaded file is not a valid Android package."
                )
            }

            if (archiveInfo.packageName != context.packageName) {
                Log.e(
                    UpdateConfig.TAG,
                    "Package ID mismatch! expected=${context.packageName} actual=${archiveInfo.packageName}"
                )
                deleteQuietly(apkFile)
                return@withContext ApkVerificationResult.Rejected(
                    "Security check failed: APK package ID does not match this app."
                )
            }

            // 3. Signing certificate verification
            val installedCerts = getInstalledSigningCertHashes()
            val archiveCerts = extractSigningCertHashes(archiveInfo)

            if (installedCerts.isEmpty() || archiveCerts.isEmpty() ||
                installedCerts.intersect(archiveCerts).isEmpty()
            ) {
                Log.e(UpdateConfig.TAG, "Signing certificate mismatch!")
                deleteQuietly(apkFile)
                return@withContext ApkVerificationResult.Rejected(
                    "Security check failed: APK signing certificate is not trusted."
                )
            }

            ApkVerificationResult.Verified(apkFile)
        }

    internal fun computeSha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun getArchivePackageInfo(apkFile: File): PackageInfo? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }
    }

    private fun getInstalledSigningCertHashes(): Set<String> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags)
            }
        }.getOrNull() ?: return emptySet()

        return extractSigningCertHashes(info)
    }

    private fun extractSigningCertHashes(packageInfo: PackageInfo): Set<String> {
        val signatures: List<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.orEmpty().toList()
            } else {
                signingInfo.signingCertificateHistory.orEmpty().toList()
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures.orEmpty().toList()
        }

        return signatures.mapNotNull { sig ->
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                digest.joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }.toSet()
    }

    private fun deleteQuietly(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }
}
