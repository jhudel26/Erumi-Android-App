# ERUMI MIGRATION ANALYSIS
## Complete Technical Architecture, Source Inventory, and Kotlin Migration Strategy

---

## 1. Existing Project Architecture

The original **Erumi** project (v2.8.2) is designed as a hybrid system with a desktop-hosted Python backend and multi-client frontends (Vanilla JS Web SPA and a Capacitor 7 Android container).

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ORIGINAL ARCHITECTURE                           │
├────────────────────────────────────────────────────────────────────────┤
│  [Windows PC / Host]                                                  │
│    ├── server_app.py (CustomTkinter GUI Controller: ErumiServer.exe)  │
│    └── web_server.py (Multi-threaded HTTP Server on :3000)            │
│          ├── In-Memory TTL Cache (Search, Episodes, Metadata, Stream)  │
│          ├── Reverse Proxy & HLS M3U8 Rewriter (/api/proxy)            │
│          ├── Disguise Header Stripper (\x89PNG ... IEND)               │
│          ├── Scrapers: AllAnime GQL, AniList GQL, HiAnime, AniDB,      │
│          │             AniNeko, TMDB, Jikan                            │
│          └── MPV Desktop Launcher (/api/play-mpv)                      │
│                                                                        │
│  [Clients]                                                            │
│    ├── Web Browser (http://localhost:3000 or http://<LAN_IP>:3000)     │
│    └── Android APK (Capacitor WebView Wrapper)                         │
│          └── Requires manual input of PC IP (e.g. 192.168.0.10:3000)   │
│              to connect to ErumiServer.exe over Wi-Fi                  │
└────────────────────────────────────────────────────────────────────────┘
```

### Architectural Deficiencies in Original App:
1. **Host Dependency:** The Android APK cannot function independently without `ErumiServer.exe` active on a PC on the same Wi-Fi subnet.
2. **WebView Overhead:** The user interface runs inside an Android WebView wrapped by Capacitor, leading to frame drops, touch latency, and sub-optimal media decoding.
3. **Fragile Network Bridging:** If PC Wi-Fi IP changes (DHCP lease renewal), the mobile app breaks until reconfigured manually.
4. **No Standalone Background Execution:** Downloads and offline playback were not supported natively on mobile; streams relied on real-time proxying through the PC server.

---

## 2. Complete Inventory of Existing Features

| Category | Feature | Original Implementation | Status / Viability in Native Android |
| :--- | :--- | :--- | :--- |
| **Catalog & Discovery** | 3D 3-Card Carousel | Web CSS 3D transforms (`card-prev`, `card-active`, `card-next`) with autoplay | Fully migratable via Jetpack Compose `HorizontalPager` / Custom Carousel |
| | Latest Releases Feed | `/api/latest` endpoint (AllAnime GQL sorted by `Latest_Update`, fallback to AniList) | Directly migratable to OkHttp/Retrofit Kotlin module |
| | Trending / Popular Anime | `/api/popular` endpoint (AllAnime `Trending`, fallback to AniList `POPULARITY_DESC`) | Directly migratable to Kotlin Coroutines pipeline |
| | Genre Filtering | 13 genres (Action, Adventure, Comedy, Drama, Fantasy, Romance, Sci-Fi, etc.) | Directly migratable |
| | Recommendations Shelf | `/api/recommendations` based on seed titles via AniList GraphQL relations | Directly migratable with local Room seed memory |
| **Search Engine** | Title Search | `/api/search` with AllAnime GQL + AniList fallback | Directly migratable |
| | Fuzzy Matching & Scoring | Character bigram similarity, exact match bonuses, sequel/season weighting | Directly migratable into Kotlin `FuzzyMatcher` utility |
| | Popular Search Chips | Static quick tag buttons in Search View | Directly migratable |
| | Search History | Stored in browser `localStorage` | Migratable to Room `SearchHistoryEntity` |
| **Anime Details** | Hero Poster & Banner | Fetched via `/api/metadata` (AniList extraLarge/banner) | Migratable using Coil image loader |
| | Multi-Season / Franchise | AniList GraphQL sequel/prequel relational batching | Directly migratable |
| | Synopsis & Metadata | Format, Season Year, Episode Count, Status, Score, Genres | Directly migratable |
| | Episode Range Tabs | Pagination into chunks of 50 episodes | Migratable to Jetpack Compose tab row |
| **Streaming & Player** | HLS Streaming | HLS.js in browser through `/api/proxy` | Migratable to AndroidX Media3 ExoPlayer |
| | Disguise Header Stripping | Strips `\x89PNG ... IEND` from TS segments in Python | Migratable via custom Media3 `DataSource` |
| | Referer Injection | Custom HTTP headers per CDN host in proxy | Supported in Media3 `HttpDataSource` / `DisguiseHeaderDataSource` |
| | Multi-Source Waterfall | HiAnime -> AniDB -> AniNeko | Directly migratable into Kotlin resolver pipeline |
| | Sub / Dub Audio Toggle | Checks server type (`sub` vs `dub`) and requests stream | Migratable directly in player UI |
| | Subtitle Engine | Custom WebVTT parser & renderer overlay | Migratable via Media3 subtitle tracks + Compose Canvas / Text overlay |
| | Playback Speed Controls | 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x | Native Media3 `setPlaybackSpeed()` |
| | Skip Intro Button | Timestamp detection or 85s skip | Migratable |
| | Auto-Next Episode | Automatic progression upon media completion | Handled by ExoPlayer `Player.Listener.onPlaybackStateChanged` |
| | Landscape Auto-Rotation | Forced orientation lock in fullscreen | Handled via Android `Activity.requestedOrientation` |
| **User Library** | Continue Watching | Browser `localStorage` (`erumi_history`) with resume timestamp and % | Migratable to Room `ContinueWatchingEntity` |
| | Watch History | History list with timestamps, durations, and purge capability | Migratable to Room `WatchHistoryEntity` |
| | Watchlist / Bookmarks | Toggle saved shows in `localStorage` | Migratable to Room `WatchlistEntity` |
| **Downloads** | Offline Downloads | None in original app (streaming only) | Built from scratch using Android `WorkManager` & Media3 DownloadManager |
| **Settings** | Configuration | `/api/settings` backed by `erumi_config.json` | Migratable to Jetpack DataStore Preferences |
| **Desktop Integrations**| MPV Player Launch | `/api/play-mpv` spawns subprocess `mpv.exe` | Desktop-specific; replaced by native ExoPlayer controls |
| | LAN Share QR Code | `/api/network-info` with qrserver.com API | Desktop-specific; not needed in standalone app |

---

## 3. Backend Endpoint Inventory

The Python backend (`web_server.py`) exposed the following HTTP REST endpoints:

### 1. `GET /api/status`
- **Purpose:** Server health check and desktop capabilities.
- **Parameters:** None.
- **Response:**
  ```json
  {
    "status": "ok",
    "version": "v2.8.2",
    "cli_ready": true,
    "mpv_available": true,
    "mpv_path": "...",
    "local_ip": "192.168.1.10",
    "lan_url": "http://192.168.1.10:3000"
  }
  ```
- **Kotlin Replacement:** Internal device status / connectivity observer via `ConnectivityManager`.

### 2. `GET /api/search?q={query}`
- **Purpose:** Multi-source search anime catalog.
- **Query Parameters:** `q` (string, required).
- **Backend Flow:**
  1. Check in-memory cache (TTL: 3600s).
  2. Query AllAnime GraphQL `shows(search: {query: ...})`.
  3. If empty, query with `translationType: "dub"`.
  4. If empty, fallback to AniList GraphQL `Page.media(search: ...)`.
  5. Rank results using `score_search_result(query, item)`.
- **Response:**
  ```json
  {
    "success": true,
    "data": {
      "type": "search_results",
      "results": [
        {
          "id": "allanime-...",
          "title": "Solo Leveling",
          "name": "Ore dake Level Up na Ken",
          "englishName": "Solo Leveling",
          "session": "allanime:...",
          "episodes": 12,
          "year": 2024,
          "type": "TV",
          "status": "Finished",
          "genres": ["Action", "Fantasy"],
          "poster": "https://...",
          "banner": "https://...",
          "index": 1
        }
      ]
    }
  }
  ```

### 3. `GET /api/latest?limit={limit}`
- **Purpose:** Home screen latest anime releases.
- **Query Parameters:** `limit` (default: 18).
- **Backend Flow:** AllAnime GraphQL with `sort_by="Latest_Update"`, fallback to AniList `UPDATED_AT_DESC` / `TRENDING_DESC`.
- **Response:** Array of anime items formatted identical to search results.

### 4. `GET /api/popular`
- **Purpose:** Trending / popular anime catalog.
- **Backend Flow:** AllAnime GraphQL with `sort_by="Trending"`, fallback to AniList `POPULARITY_DESC`.
- **Response:** Array of anime items formatted identical to search results.

### 5. `GET /api/browse?page={page}&limit={limit}&genre={genre}&status={status}&year={year}`
- **Purpose:** Filterable anime catalog grid with pagination.
- **Backend Flow:** Query AllAnime or AniList with genre, status, and year parameters.
- **Response:**
  ```json
  {
    "success": true,
    "data": {
      "type": "search_results",
      "results": [...],
      "hasMore": true
    }
  }
  ```

### 6. `GET /api/metadata?q={title}&year={year}`
- **Purpose:** High-resolution posters, backdrops, AniList relations (franchise/sequels), and synopsis.
- **Query Parameters:** `q` (title, required), `year` (optional).
- **Backend Flow:**
  1. AniList GraphQL `Page.media(search: $title)`.
  2. Parse seasons/parts (`parse_season_and_part()`) to find exact season match.
  3. Relational franchise resolver: fetches `SEQUEL`, `PREQUEL`, `SIDE_STORY` edges.
  4. Fallback to TMDB TV Search API (`https://api.themoviedb.org/3/search/tv?api_key=2dca580c2a14b55200e784d157207b4d`).
- **Response:**
  ```json
  {
    "success": true,
    "data": {
      "id": 151807,
      "title": { "romaji": "...", "english": "...", "userPreferred": "..." },
      "status": "FINISHED",
      "format": "TV",
      "episodes": 12,
      "seasonYear": 2024,
      "coverImage": { "extraLarge": "...", "large": "...", "medium": "..." },
      "bannerImage": "...",
      "averageScore": 84,
      "description": "...",
      "genres": ["Action", "Adventure", "Fantasy"],
      "relations": { "edges": [...] }
    }
  }
  ```

### 7. `GET /api/poster?showId={showId}&title={title}`
- **Purpose:** Artwork lookup prioritizing configured sources: AllAnime -> AniList -> Jikan -> TMDB.
- **Response:** `{"success": true, "data": {"poster": "...", "banner": "...", "source": "anilist"}}`.

### 8. `GET /api/episodes?query={title}&mode={mode}&index={index}`
- **Purpose:** Retrieve verified episode list for an anime.
- **Backend Flow:**
  1. Look up anime title from search.
  2. Query HiAnime episode list (`/api/theme/episode/list/{aid}`).
  3. If HiAnime has no episodes, fallback to generating nominal episodes based on metadata episode count.
- **Response:**
  ```json
  {
    "success": true,
    "data": {
      "type": "episodes",
      "anime": { "id": "...", "title": "...", "year": 2024, "episodes": 12 },
      "episodes": [
        { "id": "hianime:18944-ep-121852", "session": "...", "episodeNumber": "1", "epId": "121852" }
      ]
    }
  }
  ```

### 9. `GET /api/stream?query={title}&episode={episode}&audio={sub|dub}`
- **Purpose:** Stream resolution with multi-provider waterfall.
- **Backend Flow:**
  1. HiAnime stream resolver (Server list -> embed page -> OtakuCipher blob decryption -> m3u8 source URL + WebVTT subtitles).
  2. HiAnime prefix & alternate title fallback.
  3. AniDB fallback (`https://anidb.app/search/suggestions` -> episodes -> languages -> embed).
  4. AniNeko fallback (`https://anineko.to/browser` -> watch -> server grid -> embed -> m3u8).
- **Response:**
  ```json
  {
    "success": true,
    "data": {
      "type": "streams",
      "anime": { "title": "Solo Leveling" },
      "episodes": [
        {
          "episodeNumber": 1,
          "url": "https://.../master.m3u8",
          "stream": {
            "provider": "hianime",
            "server": "HD-1",
            "url": "https://.../master.m3u8",
            "directUrl": "https://.../master.m3u8",
            "quality": "Auto",
            "audio": "sub",
            "isHls": true,
            "referer": "https://megaplay.buzz/",
            "subtitles": [
              { "lang": "English", "src": "https://.../eng.vtt" }
            ]
          },
          "subtitles": [...]
        }
      ]
    }
  }
  ```

### 10. `GET /api/proxy?url={target_url}&referer={referer}`
- **Purpose:** HLS reverse proxy, CORS header injection, disguise header stripping, and playlist rewriting.
- **Response:** Streaming video chunks or rewritten M3U8 playlists.
- **Kotlin Replacement:** Handled natively inside ExoPlayer using custom `DataSource.Factory` with custom OkHttp client and header injection.

### 11. `GET /api/recommendations?titles={title1|title2}&limit={limit}`
- **Purpose:** Relational recommendations based on user history or active anime.

### 12. `GET /api/settings` & `POST /api/settings`
- **Purpose:** Read and persist playback, cache, and server preferences.

---

## 4. Anime Source Inventory

| Source Name | Protocol / Format | Roles | Auth Required | Provider Reliability |
| :--- | :--- | :--- | :--- | :--- |
| **AllAnime** | GraphQL (`https://api.mkissa.net/api`) | Primary Catalog, Search, Latest, Trending | None (Public API with Referer `https://mkissa.to`) | High. Provides rich episode metadata and show edges. |
| **AniList** | GraphQL (`https://graphql.anilist.co`) | Metadata, Posters, Banners, Franchise Relations, Fallback Catalog | None (Public API with UA) | Very High (99.9% uptime). High rate limit tolerance for standard queries. |
| **HiAnime** | HTML Scraping & JSON API (`https://hianime.at`) | Primary Video Streams, Episode IDs, Subtitles | None (AJAX headers `X-Requested-With: XMLHttpRequest`) | Medium-High. Requires OtakuCipher deobfuscation for embed blobs. |
| **AniDB** | JSON API & Embeds (`https://anidb.app`) | Secondary Stream Fallback (HLS streams) | None | Medium. Stream URLs expire within 3-6 minutes. |
| **AniNeko** | HTML Scraping (`https://anineko.to`) | Tertiary Stream Fallback | None | Medium. HTML structure may change periodically. |
| **TMDB** | REST API (`api.themoviedb.org`) | Metadata & Artwork Fallback | API Key (`2dca580c2a14b55200e784d157207b4d`) | Very High. |
| **Jikan (MAL)**| REST API (`api.jikan.moe/v4`) | Artwork Fallback | None | High, but rate-limited to 3 req/sec. |

---

## 5. Existing Scraping and Extraction Mechanisms

### 5.1 AllAnime GraphQL Scraper
- **Endpoint:** `https://api.mkissa.net/api`
- **Required Headers:**
  - `User-Agent`: Desktop Chrome
  - `Origin`: `https://mkissa.to`
  - `Referer`: `https://mkissa.to`
  - `Content-Type`: `application/json`
- **Queries:**
  - `shows(search: {query: ...}, translationType: "sub", limit: 40)`
  - `shows(search: {sortBy: "Latest_Update"}, limit: 18)`
  - `shows(search: {sortBy: "Trending"}, limit: 40)`

### 5.2 AniList GraphQL Scraper
- **Endpoint:** `https://graphql.anilist.co`
- **Query Structure:** Single query fetching `media` with `title { romaji english userPreferred }`, `coverImage { extraLarge large medium }`, `bannerImage`, `relations { edges { relationType node { id title format seasonYear status } } }`.
- **Relational Batching:** Batch queries missing franchise IDs (`ids: [Int]`) to assemble full prequel/sequel timelines without multiple round-trips.

### 5.3 HiAnime Stream Resolver & OtakuCipher
- **Process:**
  1. Search: `GET https://hianime.at/search?keyword={title}`.
  2. Parse HTML matches: `<h3 class="film-name"><a href="/{slug}" title="{name}">`.
  3. Match title using `_is_valid_title_match()` and season/part filtering.
  4. Fetch episode list: `GET https://hianime.at/api/theme/episode/list/{aid}`.
  5. Fetch server list: `GET https://hianime.at/api/theme/episode/servers?episodeId={epId}`.
  6. Decode server hash: `base64.b64decode(server.hash)` -> `embedUrl` (e.g. `https://megaplay.buzz/embed-2/...`).
  7. Fetch embed HTML: Find `window.__P = "..."`.
  8. **OtakuCipher Deobfuscation:**
     ```kotlin
     fun deobfuscateOtakuBlob(blob: String): String {
         val raw = Base64.decode(blob, Base64.DEFAULT)
         val key = "otaku-embed-v1".toByteArray(Charsets.UTF_8)
         val out = ByteArray(raw.size)
         for (i in raw.indices) {
             out[i] = (raw[i].toInt() xor key[i % key.size].toInt()).toByte()
         }
         return String(out, Charsets.UTF_8)
     }
     ```
  9. Resulting JSON provides `{"src": "https://.../master.m3u8", "subtitles": [{"lang": "English", "src": "https://.../eng.vtt"}]}`.

### 5.4 Search Matching & Scoring Logic
The Python backend's scoring logic:
1. Exact normalized title match: `+100,000` points.
2. Prefix match (`"title "`): `+20,000` points.
3. Substring match: `+5,000` points.
4. Season / Part consistency:
   - Season matches requested season: `+30,000` points.
   - Season mismatch: `-50,000` points.
5. Special / Movie penalty: If query did not ask for movie/special/OVA, penalize specials by `-200,000` points.
6. Airing / TV priority: Non-TV finished single-episode specials penalized `-200,000` points.

---

## 6. Streaming & HLS Implementation

In the original Python implementation:
- Upstream M3U8 playlists and TS segments were fetched through `urllib.request`.
- `strip_disguise_header(buf)` removed the leading `\x89PNG ... IEND` bytes added by video CDNs (`vivibebe`, `ibyteimg`, `dramahot`) to prevent hotlinking.
- `rewrite_m3u8()` parsed all child URIs in the M3U8 playlist and rewritten them to `/api/proxy?url=...&referer=...`.

### Native Android Architecture:
In the Android standalone app, **no HTTP server or localhost proxy is required for Media3 ExoPlayer**.
ExoPlayer allows inserting a custom `DataSource.Factory`:
1. `OkHttpDataSource.Factory` sets custom `User-Agent` and `Referer`.
2. A custom `DisguiseHeaderDataSource` wraps the upstream data source:
   - Reads the initial bytes of each stream segment.
   - If bytes start with `\x89PNG`, it locates `IEND` + 4 CRC bytes and slices them off before passing bytes to ExoPlayer's HLS demuxer.
   - This directly decodes anti-hotlinked streams without running an embedded proxy or rewriting playlist text.

---

## 7. Existing Database or Storage Implementation

The original app had **zero database**:
- Browser `localStorage` was used for:
  - `erumi_history`: JSON dictionary of `{ animeTitle: { lastEp, progress, duration, timestamp, poster, ... } }`.
  - `erumi_watchlist`: JSON array of saved anime items.
  - `erumi_server_url`: The PC server IP.
  - `erumi_meta_cache_v2`: Metadata cache.
- Python server:
  - `CLI_CACHE`: In-memory Python dictionary with time-based invalidation.
  - `erumi_config.json`: Flat JSON file on disk.

### Room Database Migration:
In **Erumi Native**, all storage is migrated to a structured SQLite database via **Room**:
- `AnimeEntity`
- `EpisodeEntity`
- `WatchHistoryEntity`
- `ContinueWatchingEntity`
- `WatchlistEntity`
- `DownloadEntity`
- `PlaybackPreferenceEntity`
- `SearchHistoryEntity`
- App preferences managed via **Jetpack DataStore Preferences**.

---

## 8. Android Limitations & Considerations

1. **Background Execution:** Android enforces strict background execution limits (Doze mode, battery saver). Long-running episode downloads cannot run in an arbitrary thread; they must use `WorkManager` with a foreground notification service.
2. **Network Security:** Android 9+ blocks cleartext HTTP traffic by default. Network security configuration must permit legitimate HTTP stream endpoints (or use HTTPS across all providers).
3. **Storage Access Framework (SAF):** Offline downloads must be stored in app-scoped external storage (`context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)`) to avoid requiring invasive storage permissions on Android 13+ (API 33).
4. **Orientation & System Bars:** Cinema playback in landscape requires managing system bars (`WindowInsetsControllerCompat`) for edge-to-edge immersive viewing.

---

## 9. Proposed Kotlin Replacements

| Original Python / JS Component | Proposed Kotlin Android Architecture |
| :--- | :--- |
| `web_server.py` HTTP Server | Native Kotlin Repository & Scraper Modules (`engine/sources/`) |
| `AllAnimeGql`, `HiAnime`, `AniList` | OkHttp 4.12 + Retrofit / Gson / Kotlinx Serialization |
| `strip_disguise_header()` | `DisguiseHeaderDataSource : DataSource` in Media3 pipeline |
| `rewrite_m3u8()` | Unnecessary; Media3 handles redirects, sub-playlists, and custom headers |
| `HLS.js` HTML5 Video | `androidx.media3.exoplayer.ExoPlayer` + `media3.ui.PlayerView` |
| `localStorage` | Android Room Database (`AppDatabase`) + AndroidX DataStore |
| Web SPA Frontend (`index.html`, `style.css`) | Jetpack Compose + Material 3 Theme (`#090D12` Dark Cinema) |
| Download Subsystem (previously nonexistent) | Android `WorkManager` with persistent download state in Room |
| `ErumiServer.exe` Desktop Controller | **Completely Eliminated** — 100% standalone Android execution |

---

## 10. Direct Migration vs Architectural Changes

### 10.1 Features Migrated Directly:
- AllAnime GraphQL catalog queries (Search, Latest, Popular, Genre Browse).
- AniList GraphQL metadata and relational franchise timeline resolution.
- HiAnime search, episode list extraction, server parsing, and OtakuCipher blob decryption.
- Fuzzy matching, bigram similarity, title cleaning, and season/part consistency filters.
- Dual Sub/Dub audio toggle logic.
- Playback resume time tracking and percentage calculation.
- Glassmorphic dark cinema theme (#090D12, #14B8A6, #18232C).

### 10.2 Features Requiring Architectural Changes:
- **Streaming Pipeline:** Replaced Python reverse proxy and M3U8 string rewriting with Media3 `DisguiseHeaderDataSource` and `DefaultHlsMediaSource`.
- **UI Architecture:** Replaced DOM manipulation and CSS 3D transforms with declarative Jetpack Compose (`HorizontalPager`, LazyColumn, LazyRow, AnimatedVisibility).
- **Persistence:** Replaced unindexed browser `localStorage` strings with relational Room database tables with DAO queries and Flow observables.
- **Offline Downloads:** Engineered a real download engine utilizing Android `WorkManager` to download segments/MP4 files directly to app storage for offline ExoPlayer playback.

### 10.3 Features That Cannot Be Guaranteed Due to External Provider Restrictions:
- **AniDB & AniNeko Streams:** Both providers frequently cycle upstream video hosting CDNs with strict IP-binding and 3-minute token expirations. HiAnime remains the primary reliable streaming provider, with AniDB/AniNeko configured as secondary fallback options.
- **Desktop MPV Player Integration:** Desktop-specific feature (`mpv.exe` execution) is replaced with native ExoPlayer in-app playback.
- **Local Network QR Share:** Desktop-to-phone casting is obsolete because Erumi Native runs directly on the phone.

---

## Conclusion & Implementation Roadmap
The analysis confirms that **Erumi can be 100% migrated to a standalone native Android application**.
By translating the Python scraping and deobfuscation logic into clean Kotlin modules, leveraging AndroidX Media3 ExoPlayer with a custom disguise-header datasource, and storing user library state in Room, the application will operate completely independently without needing any PC or external server.
