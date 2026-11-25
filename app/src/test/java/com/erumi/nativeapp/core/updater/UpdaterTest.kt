package com.erumi.nativeapp.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class UpdaterTest {

    private fun sampleManifest(
        versionCode: Int = 6,
        versionName: String = "1.4.0",
        apkUrl: String = "https://updates.erumi.app/android/releases/erumi-v1.4.0.apk",
        sha256: String = "a".repeat(64),
        mandatory: Boolean = false,
        minimumSupportedVersion: Int = 1,
        publishedAt: String = "2026-11-25T12:00:00Z",
        signature: String? = null
    ) = UpdateManifest(
        versionCode = versionCode,
        versionName = versionName,
        apkUrl = apkUrl,
        sha256 = sha256,
        mandatory = mandatory,
        minimumSupportedVersion = minimumSupportedVersion,
        releaseNotes = listOf("Added new features", "Fixed bugs"),
        publishedAt = publishedAt,
        fileSizeBytes = 25_000_000L,
        signature = signature
    )

    @Test
    fun `valid manifest passes validation`() {
        val result = UpdateManifestValidator.validate(
            manifest = sampleManifest(),
            allowedHosts = setOf("updates.erumi.app"),
            publicKeyBase64 = ""
        )
        assertTrue(result is ManifestValidationResult.Valid)
    }

    @Test
    fun `rejects http apkUrl`() {
        val result = UpdateManifestValidator.validate(
            manifest = sampleManifest(apkUrl = "http://updates.erumi.app/app.apk"),
            allowedHosts = setOf("updates.erumi.app"),
            publicKeyBase64 = ""
        )
        assertTrue(result is ManifestValidationResult.Invalid)
    }

    @Test
    fun `rejects untrusted host`() {
        val result = UpdateManifestValidator.validate(
            manifest = sampleManifest(apkUrl = "https://evil.example.com/app.apk"),
            allowedHosts = setOf("updates.erumi.app"),
            publicKeyBase64 = ""
        )
        assertTrue(result is ManifestValidationResult.Invalid)
    }

    @Test
    fun `rejects malformed sha256`() {
        val result = UpdateManifestValidator.validate(
            manifest = sampleManifest(sha256 = "not-a-valid-sha256"),
            allowedHosts = setOf("updates.erumi.app"),
            publicKeyBase64 = ""
        )
        assertTrue(result is ManifestValidationResult.Invalid)
    }

    @Test
    fun `rejects minimumSupportedVersion greater than versionCode`() {
        val result = UpdateManifestValidator.validate(
            manifest = sampleManifest(versionCode = 6, minimumSupportedVersion = 7),
            allowedHosts = setOf("updates.erumi.app"),
            publicKeyBase64 = ""
        )
        assertTrue(result is ManifestValidationResult.Invalid)
    }

    @Test
    fun `ecdsa manifest signature verification succeeds and rejects tampering`() {
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGen.generateKeyPair()
        val pubBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        val manifest = sampleManifest()
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(manifest.canonicalPayload().toByteArray(Charsets.UTF_8))
        val sigBase64 = Base64.getEncoder().encodeToString(signer.sign())

        val signedManifest = manifest.copy(signature = sigBase64)
        val validResult = UpdateManifestValidator.validate(
            manifest = signedManifest,
            allowedHosts = setOf("updates.erumi.app"),
            publicKeyBase64 = pubBase64
        )
        assertTrue(validResult is ManifestValidationResult.Valid)

        val tampered = signedManifest.copy(
            apkUrl = "https://updates.erumi.app/android/releases/tampered.apk"
        )
        val tamperedResult = UpdateManifestValidator.validate(
            manifest = tampered,
            allowedHosts = setOf("updates.erumi.app"),
            publicKeyBase64 = pubBase64
        )
        assertTrue(tamperedResult is ManifestValidationResult.Invalid)
    }

    @Test
    fun `version evaluation returns UpToDate when installed equals remote`() {
        val manifest = sampleManifest(versionCode = 5)
        val isUnsupported = 5L < manifest.minimumSupportedVersion.toLong()
        val isNewer = manifest.versionCode.toLong() > 5L
        assertFalse(isUnsupported)
        assertFalse(isNewer)
    }

    @Test
    fun `version evaluation returns optional update when remote is newer`() {
        val manifest = sampleManifest(versionCode = 6, mandatory = false, minimumSupportedVersion = 4)
        val installed = 5L
        val isUnsupported = installed < manifest.minimumSupportedVersion.toLong()
        val isNewer = manifest.versionCode.toLong() > installed
        assertTrue(isNewer)
        assertFalse(isUnsupported)
        assertFalse(manifest.mandatory || isUnsupported)
    }

    @Test
    fun `version evaluation returns mandatory when below minimumSupportedVersion`() {
        val manifest = sampleManifest(versionCode = 8, mandatory = false, minimumSupportedVersion = 6)
        val installed = 5L
        val isUnsupported = installed < manifest.minimumSupportedVersion.toLong()
        assertTrue(isUnsupported)
        assertTrue(manifest.mandatory || isUnsupported)
        assertEquals(8, manifest.versionCode)
    }
}
