<div align="center">

<img src="app/src/main/res/drawable/erumi_logo.png" alt="Erumi Native Logo" width="180" />

# E R U M I &nbsp; N A T I V E &nbsp; エルミ

**A Cinema-Grade Standalone Android Anime Streaming Platform**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.02.00-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Latest-757575?logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![Media3 ExoPlayer](https://img.shields.io/badge/Media3%20ExoPlayer-1.3.0-00A8E8?logo=exoplayer&logoColor=white)](https://developer.android.com/media/media3/exoplayer)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-green)](https://developer.android.com/about/versions)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34-blue)](https://developer.android.com/about/versions/14)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

*Built with Clean Architecture · MVVM · Kotlin Coroutines · Flow*

</div>

---

## ⚠️ Important Disclaimer

> **EDUCATIONAL PURPOSE ONLY**
>
> This project **does not host, store, or distribute any copyrighted content**. All media is fetched in real-time from publicly available third-party sources (AllAnime, AniList, HiAnime, AniDB, AniNeko). This source code is strictly for **educational and research purposes**, demonstrating high-performance HTTP architecture, HLS streaming, and native Android development.
>
> **The developers assume NO LIABILITY for any misuse.** Users are solely responsible for ensuring their usage complies with local laws and the Terms of Service of source websites. This project is NOT for commercial use.

---

## ✨ Key Highlights

| Feature | Description |
|---------|-------------|
| **🎯 100% Standalone** | No Windows PC, no companion server, no WebView — runs entirely on your Android device |
| **🎬 Cinema Player** | Media3 ExoPlayer with HLS adaptive bitrate (1080p/720p), hardware acceleration, WebVTT subtitles |
| **🔍 Smart Search** | Multi-source fuzzy matching across AllAnime & AniList GraphQL with season/part detection |
| **📥 Offline Downloads** | Full episode downloads via WorkManager for offline playback without internet |
| **🌙 Dark Cinema Design** | Material 3 theme optimized for OLED (`#090D12` background, `#14B8A6` teal accent) |
| **🛡️ Anti-Hotlinking** | Real-time PNG disguise header stripping (`\x89PNG...IEND`) in Media3 DataSource pipeline |
| **📚 Local Library** | Continue watching, watch history, watchlist — all stored locally in Room database |

---

## 📱 Screenshots

<div align="center">

| Home | Search | Anime Details |
|:---:|:---:|:---:|
| ![Home](Screenshots/home.png) | ![Search](Screenshots/search.png) | ![Details](Screenshots/animedetailpage.png) |

| Settings | Downloads | Video Player |
|:---:|:---:|:---:|
| ![Settings](Screenshots/settings.png) | ![Downloads](Screenshots/offlinedownload.png) | ![Player](Screenshots/player.png) |

</div>

---

## 🏗️ Architecture

Erumi Native follows **Clean Architecture** with **MVVM** pattern, organized in three layers:

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                           │
│  • Jetpack Compose UI (Material 3 Dark Cinema Theme)           │
│  • ViewModels (StateFlow & Kotlin Coroutines)                  │
│  • Navigation Component (ErumiNavGraph / MangaNavGraph)        │
└───────────────────────────────────▲─────────────────────────────┘
                                    │
┌───────────────────────────────────┴─────────────────────────────┐
│                       DOMAIN LAYER                              │
│  • Business Models (Anime, Episode, StreamSource, Manga)       │
│  • Repository Interfaces                                       │
│  • Engine Resolvers (EpisodeResolver, FuzzyMatcher, OtakuCipher)│
└───────────────────────────────────▲─────────────────────────────┘
                                    │
┌───────────────────────────────────┴─────────────────────────────┐
│                        DATA LAYER                               │
│  • Data Sources (AllAnime, AniList, HiAnime, AniDB, AniNeko)   │
│  • Manga Sources (MangaDex, AsuraScans, FlameComics, etc.)     │
│  • Room Database (AppDatabase & DAOs)                          │
│  • DataStore Preferences                                       │
│  • Media3 DataSource & Download Service                        │
└─────────────────────────────────────────────────────────────────┘
```

### Project Structure

```
app/src/main/java/com/erumi/nativeapp/
├── core/
│   ├── common/           # Constants, Resource wrapper (Success/Error/Loading)
│   ├── database/         # Room AppDatabase, DAOs, Entities, Converters
│   ├── datastore/        # UserPreferencesDataStore (Jetpack DataStore)
│   ├── download/         # Media3 ErumiDownloadService, ErumiDownloadUtil
│   ├── image/            # ImageResolver, CoilConfig, Providers (Jikan, Kitsu)
│   ├── network/          # ApiClient, DisguiseHeaderDataSource, ErumiDataSourceFactory
│   └── utils/            # FuzzyMatcher, OtakuCipher
├── data/
│   └── repository/       # AnimeRepository, UserLibraryRepository, MangaRepository
├── domain/
│   └── model/            # Business domain entities (Anime, Episode, StreamSource, etc.)
├── engine/
│   ├── sources/          # AllAnimeSource, AniListSource, HiAnimeSource, AniDBSource, AniNekoSource
│   └── resolver/         # EpisodeResolver (waterfall pipeline)
├── features/
│   ├── home/             # Hero banner, continue watching, categories, latest releases
│   ├── search/           # Debounced search, history, popular tags, results grid
│   ├── anime_details/    # Synopsis, franchise timeline, episode list, trailer
│   ├── player/           # Media3 cinema player, landscape controls, skip intro
│   ├── watchlist/        # Saved anime with search & long-press removal
│   ├── history/          # Watch history with progress bars & purge options
│   ├── downloads/        # Offline download manager with pause/resume/queue controls
│   ├── settings/         # Quality, audio, subtitles, cache, battery optimization
│   └── browse/           # Genre/year/status filtered catalog with pagination
├── manga/
│   ├── data/             # Sources, database, repository, download manager
│   ├── domain/           # Manga models (MangaDetails, Chapter, Page)
│   ├── presentation/     # Home, Search, Details, Reader, Library, Downloads
│   └── navigation/       # MangaNavGraph
├── shared/
│   ├── domain/models/    # Shared media models (MediaChapter, MediaProgress)
│   ├── navigation/       # BaseNavGraph, BaseScreen
│   └── ui/
│       ├── components/   # MediaPosterCard, ContinueMediaCard, SectionHeader, etc.
│       └── tour/         # ErumiTourState, ErumiTourOverlay (onboarding)
├── navigation/           # ErumiNavGraph, Screen (sealed class)
├── ui/
│   ├── components/       # Reusable UI components (Buttons, Loaders, Dialogs)
│   ├── experience/       # ExperienceSelectorScreen (Anime/Manga selector)
│   ├── splash/           # ErumiSplashScreen (branded splash)
│   └── theme/            # Color, Type, Theme (Dark Cinema)
├── ErumiApplication.kt   # Application class, ImageLoaderFactory, lifecycle
└── MainActivity.kt       # Entry point, experience routing, splash handling
```

---

## 🎬 Video Player Architecture

```
Upstream Video CDN (vivibebe, ibyteimg, dramahot, etc.)
                         │
                         │ (HTTP/HTTPS Stream Chunks)
                         ▼
           [DefaultHttpDataSource / OkHttp]
             • Injects Referer, Origin, User-Agent
             • Supports Range requests & HTTP redirects
                         │
                         ▼
        [DisguiseHeaderDataSource]  ← Custom Media3 DataSource
             • Scans initial packet bytes (buffer up to 64KB)
             • Locates \x89PNG ... IEND (+ 4 CRC bytes)
             • Strips disguise header on-the-fly
                         │
                         ▼
            [HlsMediaSource / ExoPlayer]
             • Adaptive Bitrate Streaming (1080p, 720p, Auto)
             • Hardware Accelerated Video Decoding
             • WebVTT Subtitle Synchronization
```

### Anti-Hotlinking PNG Disguise Stripping

Certain anime CDNs disguise MPEG-TS video chunks as 1×1 pixel PNG images by prepending an 8-byte PNG header and minimal metadata chunk ending in `IEND` + 4 CRC bytes. `DisguiseHeaderDataSource` handles this transparently in the media pipeline — no proxy server required.

---

## 🔍 Data Sources

### Catalog & Discovery

| Source | Protocol | Endpoint | Role |
|--------|----------|----------|------|
| **AllAnime** | GraphQL | `https://api.mkissa.net/api` | Primary catalog, search, latest, trending, genre browse |
| **AniList** | GraphQL | `https://graphql.anilist.co` | Metadata enrichment, posters, banners, franchise relations, fallback catalog |

### Video Streaming (Waterfall Priority)

```
User Requests Episode
         │
         ▼
[HiAnime Primary] ──(Success)──> Play Stream
         │ (Fails / No Stream)
         ▼
[Title Prefix & Subtitle Matcher] ──(Success)──> Play Stream
         │ (Fails)
         ▼
[AniList Alternate Title Resolver] ──(Success)──> Play Stream
         │ (Fails)
         ▼
[AniDB Fallback] ──(Success)──> Play Stream
         │ (Fails)
         ▼
[AniNeko Fallback] ──(Success)──> Play Stream
         │ (Fails)
         ▼
Report Error to User with Retry
```

| Source | Protocol | Base URL | Capabilities |
|--------|----------|----------|--------------|
| **HiAnime** | HTML/JSON + OtakuCipher | `https://hianime.at` | Primary streams, episode lists, WebVTT subtitles, Sub/Dub |
| **AniDB** | REST + Embed Scraper | `https://anidb.app` | Secondary fallback, language resolution |
| **AniNeko** | HTML Scraper | `https://anineko.to` | Tertiary fallback, server grid parsing |

### Manga Sources

| Source | Protocol | Base URL |
|--------|----------|----------|
| **MangaDex** | REST API | `https://api.mangadex.org` |
| **AsuraScans** | HTML Scraper | `https://asuracomic.net` |
| **FlameComics** | HTML Scraper | `https://flamecomics.xyz` |
| **ManhuaPlus** | HTML Scraper | `https://manhuaplus.org` |
| **ManhuaScan** | HTML Scraper | `https://manhuascan.io` |
| **Manganato** | HTML Scraper | `https://manganato.com` |

---

## 🎨 Dark Cinema Design System

| Role | Color | Hex |
|------|-------|-----|
| **Background (Deep)** | Cinema Black | `#090D12` |
| **Surface** | Elevated Dark | `#0A1218` |
| **Surface Elevated** | Card Background | `#101D27` |
| **Card** | Content Card | `#162430` |
| **Primary Accent** | Teal | `#14B8A6` |
| **Primary Hover** | Light Teal | `#2DD4BF` |
| **Text Primary** | Pure White | `#F3F9FC` |
| **Text Secondary** | Muted | `#94A9B8` |
| **Text Dim** | Subtle | `#5A7082` |
| **Border** | Subtle | `#1F2D3A` |

- **Typography**: Material 3 type scale with `Inter` font family
- **Icons**: Material Icons Extended + custom drawables
- **Motion**: Smooth transitions, crossfade image loading, shimmer placeholders

---

## 💾 Database Schema (Room)

### Entities

| Entity | Primary Key | Description |
|--------|-------------|-------------|
| `AnimeEntity` | `id: String` | Cached anime metadata, synopsis, titles, episode count, ratings |
| `EpisodeEntity` | `id: String` | Cached episode numbers and provider IDs for fast offline lookup |
| `WatchHistoryEntity` | `id: String` (`animeId_ep`) | Complete watch log with timestamps, position, duration |
| `ContinueWatchingEntity` | `animeId: String` | Active resume shelf items (>5s and <95% progress) |
| `WatchlistEntity` | `animeId: String` | User-saved anime bookmarks with cover art |
| `DownloadEntity` | `id: String` (`animeId_ep`) | Download queue status, progress, local file paths, byte sizes |
| `SearchHistoryEntity` | `query: String` | Recent search queries ordered by `searchedAt DESC` |
| `ImageCacheEntity` | `animeId: String` | Resolved poster/banner URLs with source tracking (7-day expiry) |

### DataStore Preferences

Stored in `erumi_preferences.preferences_pb`:

| Key | Type | Default |
|-----|------|---------|
| `preferred_quality` | String | `"1080p"` |
| `auto_next_episode` | Boolean | `true` |
| `default_audio` | String | `"sub"` |
| `default_subtitle_lang` | String | `"en"` |
| `subtitle_size` | Float | `1.0f` |
| `skip_intro` | Boolean | `true` |
| `skip_intro_seconds` | Int | `85` |
| `wifi_only_downloads` | Boolean | `false` |
| `app_theme` | String | `"Dark Cinema"` |

---

## 🛠️ Tech Stack

### Core
| Component | Technology | Version |
|-----------|------------|---------|
| **Language** | Kotlin | 1.9.22 |
| **UI Framework** | Jetpack Compose | BOM 2024.02.00 |
| **Design System** | Material 3 | Latest |
| **Architecture** | MVVM + Clean Architecture | — |
| **Min SDK** | Android 8.0 (API 26) | — |
| **Target SDK** | Android 14 (API 34) | — |
| **JVM Target** | Java 17 | — |

### Media & Networking
| Purpose | Library | Version |
|---------|---------|---------|
| **Media Engine** | AndroidX Media3 ExoPlayer | 1.3.0 |
| **HLS Support** | media3-exoplayer-hls | 1.3.0 |
| **Player UI** | media3-ui | 1.3.0 |
| **Downloads** | media3-database | 1.3.0 |
| **Networking** | OkHttp | 4.12.0 |
| **Serialization** | Gson | 2.10.1 |
| **HTML Parsing** | Jsoup | 1.17.2 |
| **Image Loading** | Coil Compose | 2.6.0 |

### Data & Background
| Purpose | Library | Version |
|---------|---------|---------|
| **Database** | AndroidX Room | 2.6.1 |
| **Preferences** | AndroidX DataStore | 1.0.0 |
| **Work Manager** | AndroidX WorkManager | 2.9.0 |
| **Coroutines** | kotlinx-coroutines-android | 1.7.3 |

---

## 🚀 Getting Started

### Prerequisites

- **JDK 17** or higher
- **Android SDK** (API 34 / Android 14)
- **Gradle 8.3+** (wrapper included)
- **Android Studio** (Koala or later recommended)

### Clone & Build

```bash
# Clone the repository
git clone https://github.com/jhudel26/Erumi-Android-App.git
cd Erumi-Android-App

# Build Debug APK (Linux/macOS)
./gradlew assembleDebug

# Build Debug APK (Windows)
gradlew.bat assembleDebug
# Or use the provided batch script:
build_apk.bat
```

**Output:** `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK

```bash
# Configure signing in app/build.gradle.kts first (release { signingConfig ... })
./gradlew assembleRelease
```

**Output:** `app/build/outputs/apk/release/app-release-unsigned.apk`

### Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📦 Installation Options

### Option 1: Build from Source
Follow the build instructions above to compile and install the APK.

### Option 2: Pre-built Release
Download the latest release from the [Releases](https://github.com/jhudel26/Erumi-Android-App/releases) page.

> **Note:** Enable "Install from Unknown Sources" in device settings (Settings → Security → Unknown Sources).

---

## ⚙️ Configuration

### Network (ApiClient)
- OkHttp connection pooling with 30s connect / 60s read / 30s write timeouts
- SSL tolerance for compatibility
- Automatic Referer/Origin injection per CDN host
- Exponential backoff retry for 429/503 responses
- Manga source referer mapping (Manganato, ManhuaPlus, AsuraScans, etc.)

### Player Settings (Settings Screen)
- **Video Quality**: 1080p / 720p / Auto
- **Default Audio**: Sub / Dub
- **Subtitle Language**: EN (extensible)
- **Subtitle Size**: 0.5× – 2.0×
- **Auto-Next Episode**: On/Off
- **Skip Intro**: On/Off (customizable duration, default 85s)
- **Playback Speed**: 0.5× – 2.0×

### Storage Management
- Clear image cache
- Manage downloaded episodes (pause/resume/delete)
- View storage usage breakdown
- Wi-Fi only download toggle
- Battery optimization exemption for background downloads

---

## 🔧 Development

### Code Style
- Kotlin Coding Conventions (official)
- Jetpack Compose best practices
- Clean Architecture layer separation
- Structured concurrency with Coroutines/Flow

### Testing
```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

### Lint & Formatting
```bash
./gradlew lint
./gradlew ktlintCheck
./gradlew ktlintFormat
```

---

## 📈 Release History

### Version 1.3.0 — *November 14, 2026*
**Stability, Performance & UX Improvements**

**Critical Fixes:**
- Removed `fallbackToDestructiveMigration()` — user library data now preserved across updates
- Fixed `DisguiseHeaderDataSource` buffering (64KB) for split disguise headers
- Resolved `NoClassDefFoundError` on Android < API 26 (Base64 fallback)
- Fixed Jikan v4 JSON array parsing with language-type filters
- Fixed coroutine/listener leaks in ViewModels and DownloadUtil
- Fixed UTF-8 character corruption across UI

**Performance:**
- Centralized `ImageResolver` singleton with LRU caching
- Reduced DB writes: 1s → 5s progress delta minimum
- Replaced full-table scans with targeted DAO queries
- Parallel subtitle downloads during offline preparation

**Features:**
- Watchlist & Watch History real-time search
- Long-press removal with confirmation dialog
- Manual episode marking (`markEpisodeAsWatched`)
- Customizable subtitle size & intro skip duration
- Paginated search with `loadNextPage()`
- Android 13+ `POST_NOTIFICATIONS` runtime permission

### Version 1.1.0 — *November 9, 2026*
**Critical Bug Fixes & Download Management**

- Fixed `DisguiseHeaderDataSource` `ArrayIndexOutOfBoundsException`
- Fixed ImageResolver concurrency & null returns
- Fixed AniList `ClassCastException` (JsonNull handling)
- **Pause/Resume Downloads** with persisted states
- **Pause All / Resume All** queue actions
- **Background Downloads** with battery optimization handling
- Release build minification & resource shrinking enabled
- Room schema export enabled
- Increased timeouts (30s/60s/30s)
- Max parallel downloads: 2 → 3

---

## 🗺️ Roadmap

### Planned Features
- [ ] Chromecast support
- [ ] Background audio playback (MediaSession)
- [ ] Custom playlists / collections
- [ ] Cross-device sync (Account/Cloud)
- [ ] More subtitle languages
- [ ] Recommendation engine (collaborative filtering)
- [ ] Enhanced manga reader (long-strip, webtoon modes)

### Known Issues
- Some anime lack subtitles in all languages
- Download speeds depend on network/CDN conditions
- Certain episodes may have limited quality options
- AniDB/AniNeko streams have short token expiration (3–6 min)

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/amazing-feature`
3. **Commit** your changes: `git commit -m 'Add amazing feature'`
4. **Push** to the branch: `git push origin feature/amazing-feature`
5. **Open** a Pull Request

### Code Review Checklist
- [ ] Follows Kotlin coding conventions
- [ ] Uses Jetpack Compose idioms
- [ ] Maintains Clean Architecture boundaries
- [ ] Includes tests for new logic
- [ ] Updates documentation if needed

---

## 📄 License

Distributed under the **MIT License**. See [LICENSE](LICENSE) for more information.

---

## 🙏 Acknowledgments

- **AllAnime** — GraphQL API for catalog data
- **AniList** — GraphQL API for metadata & relations
- **HiAnime** — Primary video streaming source
- **AniDB / AniNeko** — Fallback streaming sources
- **MangaDex / AsuraScans / FlameComics / ManhuaPlus / Manganato** — Manga sources
- **AndroidX Media3 Team** — ExoPlayer & Media3 libraries
- **Coil Team** — Image loading library
- **Community** — For testing, feedback, and suggestions

---

## 👨‍💻 Developer

<div align="center">

**Built by [jhudel](https://jhudel.vercel.app/)**

エルミ • **E R U M I** • エルミ

*Cinema-grade anime streaming, reimagined for native Android.*

</div>

---

<div align="center">

[![GitHub Stars](https://img.shields.io/github/stars/jhudel26/Erumi-Android-App?style=social)](https://github.com/jhudel26/Erumi-Android-App/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/jhudel26/Erumi-Android-App?style=social)](https://github.com/jhudel26/Erumi-Android-App/network/members)
[![GitHub Issues](https://img.shields.io/github/issues/jhudel26/Erumi-Android-App)](https://github.com/jhudel26/Erumi-Android-App/issues)

</div>