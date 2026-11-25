package com.erumi.nativeapp.core.updater

import com.google.gson.annotations.SerializedName
import java.io.File
import java.net.URI
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * Remote update manifest schema hosted on Cloudflare R2 (`android/update.json`).
 */
data class UpdateManifest(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("apkUrl") val apkUrl: String,
    @SerializedName("sha256") val sha256: String,
    @SerializedName("mandatory") val mandatory: Boolean = false,
    @SerializedName("minimumSupportedVersion") val minimumSupportedVersion: Int = 0,
    @SerializedName("releaseNotes") val releaseNotes: List<String> = emptyList(),
    @SerializedName("publishedAt") val publishedAt: String,
    @SerializedName("fileSizeBytes") val fileSizeBytes: Long? = null,
    @SerializedName("signature") val signature: String? = null
) {
    /**
     * Canonical payload string signed by the release private key (`SHA256withECDSA`).
     * Order and formatting must match the release signing script.
     */
    fun canonicalPayload(): String = buildString {
        append(versionCode).append('\n')
        append(versionName.trim()).append('\n')
        append(apkUrl.trim()).append('\n')
        append(sha256.trim().lowercase()).append('\n')
        append(mandatory).append('\n')
        append(minimumSupportedVersion).append('\n')
        append(publishedAt.trim()).append('\n')
        append(releaseNotes.joinToString("|") { it.trim() })
    }
}

sealed class ManifestValidationResult {
    data object Valid : ManifestValidationResult()
    data class Invalid(val reason: String) : ManifestValidationResult()
}

object UpdateManifestValidator {
    private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")

    fun validate(
        manifest: UpdateManifest,
        allowedHosts: Set<String> = UpdateConfig.allowedHosts,
        publicKeyBase64: String = UpdateConfig.manifestPublicKeyBase64
    ): ManifestValidationResult {
        if (manifest.versionCode <= 0) {
            return ManifestValidationResult.Invalid("Invalid versionCode: ${manifest.versionCode}")
        }
        if (manifest.versionName.isBlank()) {
            return ManifestValidationResult.Invalid("Blank versionName")
        }
        if (manifest.minimumSupportedVersion < 0) {
            return ManifestValidationResult.Invalid("Negative minimumSupportedVersion")
        }
        if (manifest.minimumSupportedVersion > manifest.versionCode) {
            return ManifestValidationResult.Invalid(
                "minimumSupportedVersion (${manifest.minimumSupportedVersion}) exceeds versionCode (${manifest.versionCode})"
            )
        }

        val uri = runCatching { URI(manifest.apkUrl.trim()) }.getOrNull()
            ?: return ManifestValidationResult.Invalid("Malformed apkUrl")
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return ManifestValidationResult.Invalid("apkUrl must use HTTPS")
        }
        val host = uri.host?.lowercase().orEmpty()
        if (allowedHosts.isNotEmpty() && host !in allowedHosts) {
            return ManifestValidationResult.Invalid("Untrusted APK host: $host")
        }

        if (!SHA256_REGEX.matches(manifest.sha256.trim())) {
            return ManifestValidationResult.Invalid("Malformed SHA-256 hash")
        }

        val parsedTime = runCatching { Instant.parse(manifest.publishedAt.trim()) }.getOrNull()
        if (parsedTime == null) {
            return ManifestValidationResult.Invalid("Invalid ISO-8601 publishedAt timestamp")
        }

        if (publicKeyBase64.isNotBlank()) {
            val sig = manifest.signature?.trim()
            if (sig.isNullOrEmpty()) {
                return ManifestValidationResult.Invalid("Missing manifest signature")
            }
            if (!verifyEcdsaSignature(manifest.canonicalPayload(), sig, publicKeyBase64)) {
                return ManifestValidationResult.Invalid("Invalid manifest signature")
            }
        }

        return ManifestValidationResult.Valid
    }

    internal fun verifyEcdsaSignature(
        payload: String,
        signatureBase64: String,
        publicKeyBase64: String
    ): Boolean = runCatching {
        val keyBytes = Base64.getDecoder().decode(publicKeyBase64.trim())
        val sigBytes = Base64.getDecoder().decode(signatureBase64.trim())
        val keySpec = X509EncodedKeySpec(keyBytes)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(keySpec)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        verifier.verify(sigBytes)
    }.getOrDefault(false)
}

sealed class UpdateCheckResult {
    data class UpToDate(val installedVersionCode: Long) : UpdateCheckResult()
    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val isMandatory: Boolean,
        val isUnsupportedVersion: Boolean,
        val installedVersionCode: Long,
        val installedVersionName: String
    ) : UpdateCheckResult()
    data class Error(val message: String, val cause: Throwable? = null) : UpdateCheckResult()
}

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data class UpToDate(val currentVersionName: String, val currentVersionCode: Long) : UpdateUiState()
    data class Available(
        val manifest: UpdateManifest,
        val currentVersionName: String,
        val currentVersionCode: Long,
        val isMandatory: Boolean,
        val isUnsupportedVersion: Boolean
    ) : UpdateUiState()
    data class Downloading(
        val manifest: UpdateManifest,
        val isMandatory: Boolean,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState()
    data class Verifying(
        val manifest: UpdateManifest,
        val isMandatory: Boolean
    ) : UpdateUiState()
    data class ReadyToInstall(
        val manifest: UpdateManifest,
        val isMandatory: Boolean,
        val apkFile: File
    ) : UpdateUiState()
    data class PermissionRequired(
        val manifest: UpdateManifest,
        val isMandatory: Boolean,
        val apkFile: File
    ) : UpdateUiState()
    data class Error(
        val message: String,
        val isMandatory: Boolean,
        val canRetry: Boolean,
        val manifest: UpdateManifest? = null
    ) : UpdateUiState()
}
