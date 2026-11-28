package com.erumi.nativeapp.core.updater

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lifecycle-aware coordinator for checking, downloading, verifying, and installing
 * app updates from Cloudflare R2.
 */
class UpdateManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val preferences = UpdatePreferences(appContext)
    private val checker = UpdateChecker(appContext)
    private val downloader = ApkDownloader(appContext)
    private val verifier = ApkVerifier(appContext)
    private val installer = ApkInstaller(appContext)

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null
    private var checkJob: Job? = null
    private var lastManifest: UpdateManifest? = null
    private var lastMandatory: Boolean = false
    @Volatile private var hasCheckedThisSession: Boolean = false

    /**
     * Checks for updates if [force] is true, on the first check of the app session,
     * or if the last successful check is older than [UpdateConfig.CHECK_INTERVAL_MS].
     * Safe to call on startup and foreground resume.
     */
    fun checkForUpdates(force: Boolean = false) {
        if (downloadJob?.isActive == true) return
        if (checkJob?.isActive == true) return

        val isFirstSessionCheck = !hasCheckedThisSession
        hasCheckedThisSession = true

        checkJob = scope.launch {
            val (installedCode, installedName) = checker.getInstalledVersion()

            // Clean up any leftover APK from an already-installed version
            val snapshot = preferences.getSnapshot()
            if (snapshot.pendingVersionCode > 0 && snapshot.pendingVersionCode <= installedCode) {
                downloader.cleanupOtherVersions(keepVersionCode = null)
                preferences.clearPendingApk()
            }

            if (!force && !isFirstSessionCheck) {
                val elapsed = System.currentTimeMillis() - snapshot.lastSuccessfulCheckAtMs
                if (snapshot.lastSuccessfulCheckAtMs > 0L && elapsed < UpdateConfig.CHECK_INTERVAL_MS) {
                    return@launch
                }
            } else if (force) {
                _uiState.value = UpdateUiState.Checking
            }

            when (val result = checker.checkForUpdate()) {
                is UpdateCheckResult.UpToDate -> {
                    preferences.recordSuccessfulCheck()
                    if (force) {
                        _uiState.value = UpdateUiState.UpToDate(installedName, installedCode)
                    } else if (_uiState.value is UpdateUiState.Checking) {
                        _uiState.value = UpdateUiState.Idle
                    }
                }

                is UpdateCheckResult.UpdateAvailable -> {
                    preferences.recordSuccessfulCheck()
                    lastManifest = result.manifest
                    lastMandatory = result.isMandatory

                    // Skip optional prompt if user tapped "Remind Me Later" for this version
                    if (!force && !result.isMandatory &&
                        snapshot.dismissedVersionCode == result.manifest.versionCode
                    ) {
                        return@launch
                    }

                    // If we already downloaded & can verify this exact version, jump to ReadyToInstall
                    val existingApk = downloader.targetApkFile(result.manifest.versionCode)
                    if (existingApk.exists() && existingApk.length() > 0L) {
                        _uiState.value = UpdateUiState.Verifying(result.manifest, result.isMandatory)
                        when (verifier.verify(existingApk, result.manifest)) {
                            is ApkVerificationResult.Verified -> {
                                _uiState.value = UpdateUiState.ReadyToInstall(
                                    result.manifest,
                                    result.isMandatory,
                                    existingApk
                                )
                                return@launch
                            }
                            is ApkVerificationResult.Rejected -> {
                                preferences.clearPendingApk()
                            }
                        }
                    }

                    _uiState.value = UpdateUiState.Available(
                        manifest = result.manifest,
                        currentVersionName = result.installedVersionName,
                        currentVersionCode = result.installedVersionCode,
                        isMandatory = result.isMandatory,
                        isUnsupportedVersion = result.isUnsupportedVersion
                    )
                }

                is UpdateCheckResult.Error -> {
                    Log.w(UpdateConfig.TAG, "Update check error: ${result.message}")
                    if (force) {
                        _uiState.value = UpdateUiState.Error(
                            message = result.message,
                            isMandatory = false,
                            canRetry = true,
                            manifest = null
                        )
                    } else if (_uiState.value is UpdateUiState.Checking) {
                        _uiState.value = UpdateUiState.Idle
                    }
                }
            }
        }
    }

    fun startDownload(manifest: UpdateManifest, isMandatory: Boolean) {
        if (downloadJob?.isActive == true) return
        lastManifest = manifest
        lastMandatory = isMandatory

        downloadJob = scope.launch {
            downloader.cleanupOtherVersions(keepVersionCode = manifest.versionCode)
            _uiState.value = UpdateUiState.Downloading(
                manifest = manifest,
                isMandatory = isMandatory,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = manifest.fileSizeBytes ?: -1L
            )

            when (val dlResult = downloader.download(manifest) { pct, downloaded, total ->
                _uiState.value = UpdateUiState.Downloading(
                    manifest = manifest,
                    isMandatory = isMandatory,
                    progressPercent = pct,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }) {
                is ApkDownloadResult.Success -> {
                    preferences.savePendingApk(
                        path = dlResult.apkFile.absolutePath,
                        versionCode = manifest.versionCode,
                        sha256 = manifest.sha256
                    )
                    verifyAndPrepareInstall(dlResult.apkFile, manifest, isMandatory)
                }

                is ApkDownloadResult.Error -> {
                    _uiState.value = UpdateUiState.Error(
                        message = dlResult.message,
                        isMandatory = isMandatory,
                        canRetry = true,
                        manifest = manifest
                    )
                }

                ApkDownloadResult.Cancelled -> {
                    if (isMandatory) {
                        _uiState.value = UpdateUiState.Available(
                            manifest = manifest,
                            currentVersionName = checker.getInstalledVersion().second,
                            currentVersionCode = checker.getInstalledVersion().first,
                            isMandatory = true,
                            isUnsupportedVersion = true
                        )
                    } else {
                        _uiState.value = UpdateUiState.Idle
                    }
                }
            }
        }
    }

    private suspend fun verifyAndPrepareInstall(
        apkFile: File,
        manifest: UpdateManifest,
        isMandatory: Boolean
    ) {
        _uiState.value = UpdateUiState.Verifying(manifest, isMandatory)
        when (val verifyResult = verifier.verify(apkFile, manifest)) {
            is ApkVerificationResult.Verified -> {
                _uiState.value = UpdateUiState.ReadyToInstall(manifest, isMandatory, verifyResult.apkFile)
            }
            is ApkVerificationResult.Rejected -> {
                preferences.clearPendingApk()
                _uiState.value = UpdateUiState.Error(
                    message = verifyResult.reason,
                    isMandatory = isMandatory,
                    canRetry = true,
                    manifest = manifest
                )
            }
        }
    }

    fun installVerifiedApk(apkFile: File, manifest: UpdateManifest, isMandatory: Boolean) {
        when (val launchResult = installer.launchInstall(apkFile)) {
            InstallLaunchResult.Launched -> Unit
            InstallLaunchResult.PermissionRequired -> {
                _uiState.value = UpdateUiState.PermissionRequired(manifest, isMandatory, apkFile)
            }
            is InstallLaunchResult.Error -> {
                _uiState.value = UpdateUiState.Error(
                    message = launchResult.message,
                    isMandatory = isMandatory,
                    canRetry = true,
                    manifest = manifest
                )
            }
        }
    }

    fun createInstallPermissionIntent(): Intent = installer.createUnknownSourcesSettingsIntent()

    /**
     * Called after the user returns from Android's "Allow from this source" Settings screen.
     */
    fun onInstallPermissionSettingsReturned() {
        val state = _uiState.value
        if (state is UpdateUiState.PermissionRequired) {
            if (installer.canRequestPackageInstalls()) {
                _uiState.value = UpdateUiState.ReadyToInstall(
                    manifest = state.manifest,
                    isMandatory = state.isMandatory,
                    apkFile = state.apkFile
                )
                installVerifiedApk(state.apkFile, state.manifest, state.isMandatory)
            }
        }
    }

    fun remindMeLater(manifest: UpdateManifest) {
        scope.launch {
            preferences.setDismissedVersionCode(manifest.versionCode)
            _uiState.value = UpdateUiState.Idle
        }
    }

    fun cancelDownload() {
        val current = _uiState.value
        if (current is UpdateUiState.Downloading && !current.isMandatory) {
            downloadJob?.cancel()
            _uiState.value = UpdateUiState.Idle
        }
    }

    fun retryLastAction() {
        val state = _uiState.value
        val manifest = (state as? UpdateUiState.Error)?.manifest ?: lastManifest
        if (manifest != null) {
            startDownload(manifest, lastMandatory)
        } else {
            checkForUpdates(force = true)
        }
    }

    fun dismissDialog() {
        val state = _uiState.value
        val isMandatory = when (state) {
            is UpdateUiState.Available -> state.isMandatory
            is UpdateUiState.Downloading -> state.isMandatory
            is UpdateUiState.Verifying -> state.isMandatory
            is UpdateUiState.ReadyToInstall -> state.isMandatory
            is UpdateUiState.PermissionRequired -> state.isMandatory
            is UpdateUiState.Error -> state.isMandatory
            else -> false
        }
        if (!isMandatory) {
            _uiState.value = UpdateUiState.Idle
        }
    }

    companion object {
        @Volatile
        private var instance: UpdateManager? = null

        fun getInstance(context: Context): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
