# Release Notes - Erumi Native Android App

## Version 1.1.0 - Critical Bug Fixes & Download Enhancements

### 🐛 Critical Bug Fixes

**Fixed DisguiseHeaderDataSource Array Bounds Issue**
- Resolved potential `ArrayIndexOutOfBoundsException` when parsing PNG disguise headers
- Improved buffer bounds validation to prevent crashes during media streaming
- Enhanced header parsing logic for better handling of split/chunked headers

**Fixed ImageResolver Race Condition**
- Resolved concurrent request deduplication issue that could cause null returns
- Improved synchronization and cleanup behavior for in-progress resolutions
- Added proper error handling and logging for image resolution failures
- Enhanced cache management to prevent memory leaks

**Fixed AnimeRepository Null Context Handling**
- Added safe null checks for Context parameter in repository initialization
- Improved error handling when ImageResolver fails to initialize
- Ensured repository works correctly across all ViewModels with/without context

**Fixed AniListSource JSON Null Handling**
- Resolved `ClassCastException: JsonNull cannot be cast to JsonObject` errors
- Added null-safe JSON parsing across all AniList API responses
- **Result**: Significantly faster image loading on detail pages (no more slow fallback chain)
- Fixed episode count resolution for series with partial metadata

### 🚀 High-Priority Improvements

**Release Build Optimization**
- Enabled code minification (`isMinifyEnabled = true`) for release builds
- Enabled resource shrinking to reduce APK size
- **Result**: Smaller, more efficient release APKs

**Database Schema Management**
- Enabled Room schema export (`exportSchema = true`)
- Better migration planning and validation for future database updates
- Improved long-term database maintenance

**Network Timeout Optimization**
- Increased connect timeout: 15s → 30s
- Increased read timeout: 25s → 60s (better for video streaming)
- Increased write timeout: 20s → 30s
- **Result**: More reliable streaming on slow connections

**Enhanced Error Logging**
- Added detailed error logging in HiAnimeSource and AniListSource
- All network failures now log specific error context (query, anime title, episode number)
- **Result**: Easier debugging and issue identification in production

### ✨ New Features

**Download Management Enhancements**

Pause/Resume Downloads
- Added pause button for individual episode downloads
- Added resume button for paused downloads
- Download state persisted in database
- Proper Media3 API integration for pause/resume functionality

Download Queue Management
- Added "Pause All" button to stop all active downloads at once
- Added "Resume All" button to restart all paused downloads
- Real-time active download count tracking
- Perfect for managing multiple downloads efficiently

Background Download Support
- Downloads now continue when app is minimized
- Downloads continue when user switches to another app
- Added battery optimization exemption in Settings
- Downloads continue even when screen is off (if battery optimization disabled)
- **Note**: Requires user to enable "Background Downloads" in Settings for full functionality

Battery Optimization Settings
- Added "Background Downloads" toggle in Settings
- Clear UI showing current status (enabled/disabled)
- Instructions for users to enable background downloads
- Available on Android 6.0+ (API 23+)

**New Utility Classes**
- `DownloadHelper.kt`: Battery optimization and background download utilities
- Enhanced `ErumiDownloadUtil.kt` with pause/resume methods
- Enhanced `UserLibraryRepository.kt` with queue management methods

### 🔧 Technical Improvements

**Fixed Foreground Service Crash**
- Removed automatic download service startup from Application.onCreate
- Service now starts only when needed (via Media3 DownloadService)
- Proper foreground service lifecycle management
- **Result**: App no longer crashes on startup due to service initialization timing

**Enhanced AndroidManifest**
- Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission
- Updated service declarations for better background support
- Improved notification channel configuration

**Improved Git Configuration**
- Updated `.gitignore` to exclude APK files and release artifacts
- Cleaner repository with only source code tracked

### 📱 UI/UX Improvements

**Downloads Screen**
- Added pause/resume buttons for individual downloads
- Added "Pause All" / "Resume All" queue management buttons
- Clear visual indicators for download states
- Better user control over download queue

**Settings Screen**
- Added "Background Downloads" section in Downloads settings
- Clear status display (enabled/disabled)
- User-friendly explanation of feature
- Easy access to battery optimization settings

**Navigation**
- Integrated battery optimization intent handling in MainActivity
- Connected Settings screen to permission flow
- Smooth navigation for enabling background downloads

### 🎯 Performance Improvements

**Image Loading**
- Fixed AniList JSON null handling that was causing slow fallback chain
- Images now load significantly faster on detail pages
- Reduced unnecessary API calls to Jikan/Kitsu fallbacks

**Download Performance**
- Increased max parallel downloads: 2 → 3
- Increased thread pool size: 3 → 4
- Better download queue throughput

### 🔒 Stability & Reliability

- No breaking changes to existing functionality
- All existing features preserved
- Backward compatible with current downloads
- Graceful error handling throughout
- Proper resource cleanup

### 📝 Known Issues

- None at this time

### 🔄 Migration Notes

- No database migrations required
- No user data loss
- Existing downloads will continue to work
- No action required from users

### 🙏 Credits

This release includes bug fixes, performance improvements, and new download management features to enhance the Erumi Native Android app experience.

---

**Release Date**: November 9, 2026
**Version**: 1.1.0
