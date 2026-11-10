# Erumi Native — Release Notes

This document contains the release history of **Erumi Native**, including new features, performance improvements, bug fixes, and technical updates.

---

## Version 1.2.0
### Stability, Performance & User Experience Improvements

### Stability & Critical Fixes

- **Database Data Protection:** Removed `fallbackToDestructiveMigration()` from the Room database configuration to prevent user library data—including watch history, watchlists, and downloads—from being deleted during application updates.
- **Improved Video Stream Handling:** Reworked `DisguiseHeaderDataSource` to support buffering up to 64 KB across packet boundaries, preventing stream corruption when CDN disguise headers span multiple reads.
- **Legacy Android Compatibility:** Removed the `java.util.Base64` fallback in `OtakuCipher`, resolving `NoClassDefFoundError` crashes on Android versions below API 26.
- **Jikan API Compatibility:** Updated artwork extraction to correctly handle the Jikan v4 JSON array structure and apply appropriate language-type filters.
- **Lifecycle & Coroutine Management:** Improved flow collector and download listener lifecycle management in `DetailViewModel` and `ErumiDownloadUtil`, reducing the risk of coroutine and listener leaks.
- **Character Encoding:** Fixed corrupted UTF-8 characters across cards, carousels, and download screens.

### Performance Improvements

- **Shared Image Resolver:** Centralized `ImageResolver` through a singleton factory, reducing redundant database instances and providing consistent in-memory LRU caching.
- **Optimized Watch Progress Updates:** Reduced database writes from one-second intervals to a minimum five-second progress delta.
- **Efficient Database Queries:** Replaced full-table scans with targeted DAO queries, including `getDownloadByAnimeId`.
- **Parallel Subtitle Downloads:** Improved offline download preparation by fetching subtitle tracks concurrently using Kotlin coroutines.

### Features & User Experience

- **Watchlist Search:** Added real-time search functionality to the Watchlist screen.
- **Watch History Search:** Added real-time search functionality to the Watch History screen.
- **Watchlist Management:** Added long-press interactions with a confirmation dialog for removing titles.
- **Manual Episode Tracking:** Added `markEpisodeAsWatched()` to allow users to mark episodes as watched directly from the anime detail screen.
- **Customizable Subtitle Size:** Connected the user's subtitle size preference directly to ExoPlayer's `SubtitleView`.
- **Configurable Intro Skipping:** Added customizable intro skip duration, with an 85-second default and automatic suppression after skipping.
- **Paginated Search:** Implemented page-based pagination through `loadNextPage()` to improve search browsing.
- **Android 13+ Notification Permission:** Added runtime permission handling for `POST_NOTIFICATIONS` to support foreground download progress notifications.

---

## Version 1.1.0
### Critical Bug Fixes & Download Management Enhancements

### Bug Fixes & Reliability

- **DisguiseHeaderDataSource:** Fixed a potential `ArrayIndexOutOfBoundsException` when parsing PNG disguise headers. Improved buffer validation and handling of split or chunked headers.
- **ImageResolver:** Resolved a concurrency issue in request deduplication that could result in null returns. Improved synchronization, cleanup, error handling, logging, and cache management.
- **AnimeRepository:** Added safe handling for null `Context` parameters during repository initialization and improved ImageResolver initialization error handling.
- **AniListSource:** Fixed `ClassCastException` errors caused by attempting to cast `JsonNull` to `JsonObject`. Added null-safe JSON parsing across AniList API responses.
- **Episode Metadata:** Improved episode count resolution for titles with incomplete metadata.
- **Image Loading:** Eliminated unnecessary fallback requests caused by AniList JSON parsing errors, improving image loading performance on detail pages.

### Download Management Enhancements

**Pause & Resume Downloads**

- Added the ability to pause individual episode downloads.
- Added the ability to resume paused downloads.
- Persisted download states in the database.
- Integrated pause and resume functionality with the Media3 APIs.

**Download Queue Management**

- Added **Pause All** to pause active downloads simultaneously.
- Added **Resume All** to resume paused downloads.
- Implemented real-time tracking of active downloads.
- Improved control over multiple simultaneous downloads.

**Background Downloads**

- Enabled downloads to continue when the application is minimized or running in the background.
- Added support for continuing downloads while switching between applications.
- Added battery optimization exemption handling.
- Supported downloads while the screen is off when battery optimization restrictions are disabled.

> **Note:** Enable the Background Downloads option in Settings and follow the battery optimization instructions to use the full background download functionality.

### Battery Optimization Settings

- Added a dedicated **Background Downloads** setting.
- Added a clear enabled/disabled status indicator.
- Included instructions for configuring battery optimization.
- Supported devices running Android 6.0 (API 23) and above.

### Technical Improvements

**Release Build Optimization**

- Enabled code minification using `isMinifyEnabled = true`.
- Enabled resource shrinking to reduce release APK size.

**Database Management**

- Enabled Room schema export using `exportSchema = true`.
- Improved database schema tracking and migration planning.

**Network Configuration**

- Increased connection timeout from 15 to 30 seconds.
- Increased read timeout from 25 to 60 seconds.
- Increased write timeout from 20 to 30 seconds.
- Improved reliability for media streaming on slower network connections.

**Error Logging**

- Added more detailed error logging to `HiAnimeSource` and `AniListSource`.
- Improved network failure diagnostics with contextual information, including search queries, anime titles, and episode numbers.

**Foreground Service Stability**

- Removed automatic download service initialization from `Application.onCreate`.
- Changed service startup to occur only when required through Media3 `DownloadService`.
- Improved foreground service lifecycle management.
- Resolved startup crashes associated with download service initialization timing.

**Android Manifest Updates**

- Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission.
- Updated service declarations for background download support.
- Improved notification channel configuration.

**Git Repository Maintenance**

- Updated `.gitignore` to exclude APK files and release artifacts from source control.

### Interface & Navigation Improvements

**Downloads Screen**

- Added individual pause and resume controls.
- Added Pause All and Resume All queue actions.
- Improved download status indicators.
- Enhanced download queue management.

**Settings Screen**

- Added a Background Downloads section under Download settings.
- Added download background activity status indicators.
- Included user-friendly explanations and battery optimization instructions.

**Navigation**

- Integrated battery optimization settings navigation into `MainActivity`.
- Connected the Settings screen to the required permission flow.
- Improved navigation for configuring background downloads.

### Performance Improvements

**Image Loading**

- Improved AniList response handling.
- Reduced unnecessary fallback requests to Jikan and Kitsu.
- Improved anime artwork loading performance on detail pages.

**Download Performance**

- Increased maximum parallel downloads from 2 to 3.
- Increased the download thread pool size from 3 to 4.
- Improved download queue throughput.

### Compatibility & Data Integrity

- Preserved existing application functionality.
- Maintained compatibility with existing downloads.
- Added graceful error handling across the application.
- Improved resource cleanup and service lifecycle management.
- No database migrations required for this release.
- No user data loss expected.
- No additional user action required for existing downloads.

### Known Issues

No known issues documented for this release.

---

## Release Information

| Version | Release Date |
|---|---|
| 1.2.0 | Not specified |
| 1.1.0 | November 9, 2026 |

---

**Erumi Native**

*Built to make discovering, streaming, and managing anime a better experience.*