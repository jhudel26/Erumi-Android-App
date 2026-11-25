# Erumi Native Android Automatic Update System Guide

## 1. Cloudflare R2 Bucket Structure

Configure a Cloudflare R2 bucket with your public HTTPS domain (`https://pub-ea7a053f2308483e90a9d1c4efdcc55c.r2.dev`):

```text
android/
├── update.json
└── releases/
    ├── erumi-v1.3.0.apk
    └── erumi-v1.4.0.apk
```

Set the manifest URL in `gradle.properties` (or pass `-PUPDATE_MANIFEST_URL=...` during release builds):

```properties
UPDATE_MANIFEST_URL=https://pub-ea7a053f2308483e90a9d1c4efdcc55c.r2.dev/android/update.json
UPDATE_PUBLIC_KEY=<OPTIONAL_BASE64_X509_ECDSA_P256_PUBLIC_KEY>
```

## 2. Example Production `update.json`

```json
{
  "versionCode": 6,
  "versionName": "1.4.0",
  "apkUrl": "https://pub-ea7a053f2308483e90a9d1c4efdcc55c.r2.dev/android/releases/erumi-v1.4.0.apk",
  "sha256": "REPLACE_WITH_ACTUAL_64_CHAR_HEX_SHA256",
  "mandatory": false,
  "minimumSupportedVersion": 5,
  "releaseNotes": [
    "Fixed Manganato search fallback",
    "Added automatic in-app updater"
  ],
  "publishedAt": "2026-11-25T12:00:00Z",
  "fileSizeBytes": 26214400
}
```

## 3. Calculating APK SHA-256

- **Windows (PowerShell)**:
  ```powershell
  (Get-FileHash .\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256).Hash.ToLower()
  ```
- **Linux / macOS**:
  ```bash
  sha256sum app/build/outputs/apk/release/app-release.apk
  ```

## 4. Publishing a New Release

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts` (keep `applicationId = "com.erumi.nativeapp"` unchanged).
2. Sign the release APK with your **original release keystore** (never lose or rotate without `signingCertificateHistory`, otherwise Android rejects in-place updates).
3. Compute the exact SHA-256 of the signed release APK.
4. Upload the APK to `android/releases/erumi-v<versionName>.apk` in Cloudflare R2 (never overwrite older releases so rollback remains possible).
5. Update `android/update.json` with the new `versionCode`, `versionName`, `apkUrl`, `sha256`, `fileSizeBytes`, `releaseNotes`, and `publishedAt` (and `signature` if `UPDATE_PUBLIC_KEY` is configured).
6. Verify `https://pub-ea7a053f2308483e90a9d1c4efdcc55c.r2.dev/android/update.json` returns HTTP 200 and valid JSON, then test updating from an older installed APK.
