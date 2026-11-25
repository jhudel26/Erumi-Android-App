package com.erumi.nativeapp.core.updater

import com.erumi.nativeapp.BuildConfig
import java.net.URI

/**
 * Centralized configuration for the Cloudflare R2 automatic update system.
 * All update endpoints and trusted hosts are derived from here.
 */
object UpdateConfig {
    const val TAG = "ErumiUpdater"

    /**
     * Public HTTPS URL for the Cloudflare R2 update manifest.
     * Configured via gradle.properties / CI property `UPDATE_MANIFEST_URL`.
     */
    val manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL

    /**
     * Optional Base64-encoded X.509 ECDSA P-256 public key for verifying manifest signatures.
     * When non-empty, manifests without a valid `signature` are rejected.
     */
    val manifestPublicKeyBase64: String = BuildConfig.UPDATE_PUBLIC_KEY

    /**
     * Explicit allowlist of HTTPS hosts permitted for manifest and APK downloads.
     * Prevents downloading APKs from arbitrary or untrusted domains.
     */
    val allowedHosts: Set<String> by lazy {
        val manifestHost = runCatching { URI(manifestUrl).host?.lowercase() }.getOrNull()
        buildSet {
            if (!manifestHost.isNullOrBlank()) add(manifestHost)
        }
    }

    /** Minimum interval between automatic background update checks (6 hours). */
    const val CHECK_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L

    /** Network timeouts for update manifest & APK downloads. */
    const val CONNECT_TIMEOUT_SEC: Long = 20L
    const val READ_TIMEOUT_SEC: Long = 60L

    /** Subdirectory inside app-private filesDir where downloaded APKs are stored. */
    const val UPDATES_DIR_NAME: String = "updates"
}
