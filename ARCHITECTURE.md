# ERUMI NATIVE — ARCHITECTURE DOCUMENTATION

## Overview

**Erumi Native** (`com.erumi.native`) is a 100% standalone Android application built with modern Android best practices, Clean Architecture, and MVVM (Model-View-ViewModel). It eliminates all dependencies on external servers, local network servers, Python runtimes, and WebView wrappers.

---

## Clean Architecture Layers

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

## Key Modules and Packages

### 1. `core/`
- **`network/`**:
  - `ApiClient`: Singleton OkHttpClient with HTTP connection pooling, SSL tolerance, and User-Agent headers.
  - `DisguiseHeaderDataSource`: Custom Media3 DataSource that inspects segment byte headers and strips PNG anti-hotlink disguises (`\x89PNG ... IEND`).
  - `ErumiDataSourceFactory`: Media3 DataSource.Factory that dynamically supplies referers and origin headers based on destination CDN hosts.
- **`database/`**:
  - `AppDatabase`: Room SQLite database instance holding Watch History, Continue Watching, Watchlist, Offline Downloads, and Search History.
  - `entity/`: Room entity declarations.
  - `dao/`: Typed DAO query interfaces.
- **`datastore/`**:
  - `UserPreferencesDataStore`: Key-value configuration using Jetpack DataStore Preferences for user playback and application settings.
- **`download/`**:
  - `ErumiDownloadService`: Media3 DownloadService providing foreground notification and segment downloads.
  - `ErumiDownloadUtil`: Media3 SimpleCache and DownloadManager singletons.
- **`utils/`**:
  - `FuzzyMatcher`: Character bigram similarity, title normalizer, and season/part consistency scorer.
  - `OtakuCipher`: XOR cipher deobfuscation for HiAnime player embeds.

### 2. `domain/`
- `model/`: Plain Kotlin data classes (`Anime`, `Episode`, `StreamSource`, `SubtitleTrack`, `WatchProgress`, `DownloadItem`, `UserSettings`).

### 3. `engine/`
- **`sources/`**:
  - `AnimeSource`: Common interface for catalog search, episode retrieval, and stream resolution.
  - `AllAnimeSource`: GraphQL API client for catalog, search, and latest releases.
  - `AniListSource`: AniList GraphQL client for metadata, scoring, and prequel/sequel relations.
  - `HiAnimeSource`: Primary video stream scraper with OtakuCipher decryption.
  - `AniDBSource`: Secondary stream fallback.
  - `AniNekoSource`: Tertiary stream fallback.
- **`resolver/`**:
  - `EpisodeResolver`: Waterfall stream orchestrator trying HiAnime -> title prefixes -> alternate titles -> AniDB -> AniNeko.

### 4. `data/repository/`
- `AnimeRepository`: Unified repository exposing StateFlow/Flow streams of anime discovery and playback data.
- `UserLibraryRepository`: Local data access layer connecting Room database and DataStore to the presentation layer.

### 5. `features/`
- `home/`: Home screen with Hero banner, continue watching carousel, categories, and latest releases feed.
- `search/`: Search bar with debounced query execution, recent search history, popular tag chips, and results grid.
- `anime_details/`: Detailed anime information, poster/banner art, synopsis, franchise timeline switcher, and episode list.
- `player/`: Media3 ExoPlayer cinema playback, custom landscape controls, skip intro, subtitle selector, and audio switcher.
- `watchlist/`: User bookmarks with one-click navigation and removal.
- `history/`: Watch history log with timestamps, progress bars, and purge options.
- `downloads/`: Offline downloaded episodes manager with offline ExoPlayer playback.
- `settings/`: Quality, audio language, auto-next, and storage cache settings.
