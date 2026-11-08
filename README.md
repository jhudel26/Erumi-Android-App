<div align="center">
  <img src="app/src/main/res/drawable/erumi_logo.png" alt="Erumi Native" width="120" />

  # E R U M I &nbsp; N A T I V E &nbsp; エルミ

  **100% STANDALONE NATIVE ANDROID ANIME STREAMING APPLICATION**

  ---

  <br>

  <img src="https://img.shields.io/badge/PLATFORM-ANDROID-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/LANGUAGE-KOTLIN-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-JETPACK%20COMPOSE-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/MEDIA-MEDIA3%20EXOPLAYER-FF6F00?style=for-the-badge" alt="Media3 ExoPlayer" />
  <img src="https://img.shields.io/badge/STORAGE-ROOM%20DB-009688?style=for-the-badge&logo=sqlite&logoColor=white" alt="Room DB" />
  <img src="https://img.shields.io/badge/LICENSE-MIT-green?style=for-the-badge" alt="MIT License" />
</div>

<br>

> A cinema-grade, standalone native Android anime streaming platform built with Jetpack Compose, Material 3, and AndroidX Media3 ExoPlayer.
>
> **NO Windows PC needed. NO ErumiServer.exe needed. NO local Wi-Fi proxy needed. NO WebView.**

---

## 📸 Screenshots

### Home Screen
<img src="Screenshots/home.png" alt="Home Screen" width="300" />

### Search Screen
<img src="Screenshots/search.png" alt="Search Screen" width="300" />

### Anime Details
<img src="Screenshots/animedetailpage.png" alt="Anime Details" width="300" />

### Video Player
<img src="Screenshots/player.png" alt="Video Player" width="300" />

### Downloads
<img src="Screenshots/offlinedownload.png" alt="Downloads" width="300" />

### Offline Detail
<img src="Screenshots/offlinedetailpage.png" alt="Offline Detail" width="300" />

### Settings
<img src="Screenshots/settings.png" alt="Settings" width="300" />

---

## ✨ Highlights & Key Features

### 📱 100% Standalone Execution
All catalog scraping, search fuzzy matching, episode discovery, stream resolution, and anti-hotlink decryption execute directly on the Android phone. No external dependencies or companion applications required.

### 🎬 Cinema Video Player (AndroidX Media3 ExoPlayer)
- **Native HLS (`.m3u8`) and adaptive bitrate playback** with auto-quality (1080p, 720p)
- **Landscape fullscreen** with auto-orientation lock and immersive system bars
- **On-the-fly Disguise Header Stripping**: Intercepts and slices leading `\x89PNG ... IEND` anti-hotlink disguises directly in the Media3 DataSource pipeline
- **Subtitle selection** with WebVTT overlay tracks
- **Sub / Dub instant audio switching**
- **Playback speed selection** (0.5×, 0.75×, 1×, 1.25×, 1.5×, 2×)
- **85-second intro skip button**
- **Auto-next episode playback**

### ⚡ Advanced Anime Search & Scraper Engine
- **Multi-source search** across **AllAnime GraphQL** and **AniList GraphQL**
- **Smart Scoring & Fuzzy Matching**: Character bigram similarity, exact title match weighting, and strict season/part consistency checks
- **Waterfall episode resolution**: Tries multiple sources (HiAnime → AniDB → AniNeko) for maximum availability

### 📚 Local User Library (Room Database)
- **Continue Watching**: Live episode resume cards with percentage progress
- **Watch History**: Complete watch history logs with purge options
- **Watchlist**: Personal bookmarking for your favorite anime
- **Search History**: Instant search tag suggestions from recent queries
- **Offline Downloads**: Download supported episodes for offline playback with zero internet connection

### 🎨 Dark Cinema Design System
- Primary accent: `#14B8A6` (Teal)
- Deep cinema background: `#090D12`
- Responsive layouts, glass cards, and smooth Material 3 transitions
- Optimized for OLED displays with true black backgrounds

---

## 🏗️ Architecture

Erumi Native is structured around **Clean Architecture** and **MVVM** pattern:

```
app/
  src/main/java/com/erumi/nativeapp/
    ├── core/
    │   ├── common/        # Constants, Resource wrapper
    │   ├── database/      # Room AppDatabase, DAOs, and Entities
    │   ├── datastore/     # UserPreferencesDataStore
    │   ├── download/      # Media3 ErumiDownloadService and DownloadManager
    │   ├── network/       # ApiClient, DisguiseHeaderDataSource, ErumiDataSourceFactory
    │   └── utils/         # FuzzyMatcher, OtakuCipher
    ├── data/
    │   └── repository/    # AnimeRepository, UserLibraryRepository
    ├── domain/
    │   └── model/         # Business domain entities (Anime, Episode, StreamSource, etc.)
    ├── engine/
    │   ├── sources/       # AllAnimeSource, AniListSource, HiAnimeSource, AniDBSource, AniNekoSource
    │   └── resolver/      # EpisodeResolver waterfall pipeline
    ├── features/
    │   ├── home/          # Home screen with Featured Banner & Continue Watching
    │   ├── search/        # Search bar, recent tags, and poster results
    │   ├── anime_details/ # Synopsis, relations, and episode drawer
    │   ├── player/        # Media3 cinema player and custom landscape controls
    │   ├── watchlist/     # Saved anime collection
    │   ├── history/       # Watch history logs
    │   ├── downloads/     # Offline download manager
    │   └── settings/      # Playback, cache, and app settings
    ├── navigation/        # Jetpack Compose Navigation graph
    ├── ui/
    │   ├── components/    # Reusable AnimeCards, HeroBanner, Skeletons
    │   └── theme/         # Color, Type, and Theme definitions
    ├── ErumiApplication.kt
    └── MainActivity.kt
```

### Architecture Layers

```
┌────────────────────────────────────────────────────────┐
│                   PRESENTATION LAYER                   │
│   • Jetpack Compose UI (Material 3 Dark Cinema Theme)  │
│   • ViewModels (StateFlow & Coroutines)                │
│   • Navigation Component (ErumiNavGraph)               │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                      DOMAIN LAYER                      │
│   • Business Models (Anime, Episode, StreamSource)     │
│   • Repository Interfaces                              │
│   • Engine Resolvers (EpisodeResolver, FuzzyMatcher)   │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                       DATA LAYER                       │
│   • Data Sources / Scrapers (AllAnime, AniList,        │
│     HiAnime, AniDB, AniNeko)                           │
│   • Room Database (AppDatabase & DAOs)                 │
│   • DataStore Preferences                              │
│   • Media3 DataSource & Download Service               │
└────────────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

### Core Technologies
- **Language:** Kotlin 1.9.22
- **UI Framework:** Jetpack Compose (BOM 2024.02.00) + Material 3
- **Architecture:** MVVM with Clean Architecture
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)

### Media & Networking
- **Media Engine:** AndroidX Media3 ExoPlayer 1.3.0
  - `media3-exoplayer` - Core player
  - `media3-exoplayer-hls` - HLS streaming support
  - `media3-ui` - Player UI controls
  - `media3-database` - Download caching
  - `media3-datasource-okhttp` - Network data source
- **Networking:** OkHttp 4.12.0 + Gson 2.10.1
- **Image Loading:** Coil Compose 2.6.0

### Data Storage
- **Database:** AndroidX Room 2.6.1 (SQLite) with KAPT
- **Preferences:** AndroidX DataStore Preferences 1.0.0

### Background Processing
- **Background Work:** AndroidX WorkManager 2.9.0
- **Coroutines:** kotlinx-coroutines-android 1.7.3

### Data Sources
- **AllAnime** - GraphQL API for catalog and search
- **AniList** - GraphQL API for metadata and relations
- **HiAnime** - Primary video stream source with OtakuCipher decryption
- **AniDB** - Secondary stream fallback
- **AniNeko** - Tertiary stream fallback

---

## 🗄️ Database Schema

### Room Database Entities

| Entity Name | Primary Key | Description |
| :--- | :--- | :--- |
| `AnimeEntity` | `id: String` | Cached anime metadata, synopsis, titles, episodes count, and ratings |
| `EpisodeEntity` | `id: String` | Cached episode numbers and provider IDs for fast offline lookup |
| `WatchHistoryEntity` | `id: String` (`animeId_ep`) | Comprehensive watch history log with timestamps, position, and duration |
| `ContinueWatchingEntity` | `animeId: String` | Active resume shelf items for unfinished episodes (>5s and <95% complete) |
| `WatchlistEntity` | `animeId: String` | User-saved anime bookmarks with cover art and status |
| `DownloadEntity` | `id: String` (`animeId_ep`) | Download queue status, percentage progress, local file paths, and byte sizes |
| `SearchHistoryEntity` | `query: String` | Recent search queries ordered by `searchedAt DESC` |

### DataStore Preferences

Stored in `erumi_preferences.preferences_pb`:
- `preferred_quality`: String ("1080p", "720p", "Auto")
- `auto_next_episode`: Boolean (default: true)
- `default_audio`: String ("sub", "dub")
- `default_subtitle_lang`: String ("en")
- `subtitle_size`: Float (default: 1.0f)
- `skip_intro`: Boolean (default: true)
- `wifi_only_downloads`: Boolean (default: false)
- `app_theme`: String ("Dark Cinema")

---

## 🚀 Building the APK

### Prerequisites
- JDK 17 or higher (or Android Studio JBR)
- Android SDK (API 34)
- Gradle 8.3.0

### Clone the Repository
```bash
git clone https://github.com/yourusername/erumi-native.git
cd erumi-native
```

### Build Debug APK
```bash
./gradlew assembleDebug
```

Or on Windows via batch script:
```bat
build_apk.bat
```

Output location:
`app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK
```bash
./gradlew assembleRelease
```

Output location:
`app/build/outputs/apk/release/app-release-unsigned.apk`

**Note:** For release builds, you'll need to sign the APK with your keystore. Configure signing in `app/build.gradle.kts` under the `release` build type.

### Install on Device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## � Installation

### Option 1: Build from Source
Follow the building instructions above to compile and install the APK.

### Option 2: Download Pre-built APK
Download the latest release APK from the [Releases](https://github.com/yourusername/erumi-native/releases) page and install it on your Android device.

**Note:** You may need to enable "Install from Unknown Sources" in your device settings to install the APK.

---

## 🎯 Features in Detail

### Video Playback
- **Multi-quality streaming**: Automatically selects best quality based on network conditions
- **HLS support**: Native `.m3u8` streaming with adaptive bitrate
- **Subtitle tracks**: WebVTT subtitles with customizable size
- **Audio switching**: Instant toggle between sub and dub audio
- **Playback controls**: Play/pause, seek, speed control (0.5x - 2x)
- **Intro skip**: One-tap skip 85-second intros
- **Auto-advance**: Automatically plays next episode

### Search & Discovery
- **Fuzzy matching**: Smart search handles typos and alternative titles
- **Multi-source**: Search across AllAnime and AniList simultaneously
- **Season detection**: Correctly identifies different seasons and parts
- **Recent searches**: Quick access to your search history
- **Popular tags**: Browse trending anime categories

### Offline Downloads
- **Full episode downloads**: Download complete episodes for offline viewing
- **Download manager**: Track download progress and manage storage
- **Offline playback**: Watch downloaded content without internet
- **Wi-Fi only option**: Save mobile data by downloading only on Wi-Fi

### User Library
- **Continue watching**: Resume from where you left off
- **Watch history**: Complete log of all watched episodes
- **Watchlist**: Bookmark anime to watch later
- **Progress tracking**: Visual progress bars for each episode

---

## 🔧 Configuration

### Network Settings
The app uses OkHttp with custom configuration:
- Connection pooling for efficient network usage
- SSL tolerance for compatibility
- Custom User-Agent headers

### Player Settings
Customize your viewing experience in the Settings screen:
- Preferred video quality (1080p, 720p, Auto)
- Default audio language (Sub/Dub)
- Subtitle language and size
- Auto-next episode toggle
- Skip intro toggle

### Storage Management
- Clear cache to free up space
- Manage downloaded episodes
- View storage usage breakdown

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork the repository**
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Commit your changes** (`git commit -m 'Add some amazing feature'`)
4. **Push to the branch** (`git push origin feature/amazing-feature`)
5. **Open a Pull Request**

### Code Style
- Follow Kotlin coding conventions
- Use Jetpack Compose best practices
- Write clean, readable code with meaningful names
- Add comments for complex logic

### Testing
- Test your changes on multiple Android versions
- Verify video playback works correctly
- Test offline functionality
- Check database operations

---

## �📄 License

Distributed under the MIT License. See `LICENSE` for more information.

**Note:** This project is strictly for educational and research purposes. Please respect the copyright of the content providers.

---

## 🙏 Acknowledgments

- **AndroidX Team** - For the excellent Jetpack libraries
- **AllAnime** - For providing the anime catalog API
- **AniList** - For comprehensive anime metadata
- **HiAnime** - For video streaming sources
- **Kotlin Team** - For the amazing programming language

---

## 📞 Support

If you encounter any issues or have questions:
- Open an issue on GitHub
- Check existing issues for solutions
- Read the documentation in the `docs/` folder

---

## 🗺️ Roadmap

### Planned Features
- [ ] Chromecast support
- [ ] Background audio playback
- [ ] Custom playlists
- [ ] Sync watch history across devices
- [ ] Dark/Light theme toggle
- [ ] More language support for subtitles
- [ ] Recommendation engine based on watch history

### Known Issues
- Some anime may not have subtitles in all languages
- Download speeds depend on your network connection
- Certain episodes may have limited quality options

---

<div align="center">
  <p>Made with ❤️ by the Erumi Native Team</p>
  <p>エルミ • E R U M I</p>
</div>
