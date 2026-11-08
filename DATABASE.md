# ERUMI NATIVE — DATABASE & LOCAL STORAGE DOCUMENTATION

## 1. Storage Architecture

Erumi Native replaces the original unindexed browser `localStorage` strings with a structured SQLite database built on **AndroidX Room** and settings via **AndroidX DataStore Preferences**.

---

## 2. Room Database Entities

| Entity Name | Primary Key | Description |
| :--- | :--- | :--- |
| `AnimeEntity` | `id: String` | Cached anime metadata, synopsis, titles, episodes count, and ratings. |
| `EpisodeEntity` | `id: String` | Cached episode numbers and provider IDs for fast offline lookup. |
| `WatchHistoryEntity` | `id: String` (`animeId_ep`) | Comprehensive watch history log with watched timestamps, position, and total duration. |
| `ContinueWatchingEntity` | `animeId: String` | Active resume shelf items for unfinished episodes (>5s and <95% complete). |
| `WatchlistEntity` | `animeId: String` | User-saved anime bookmarks with cover art and status. |
| `DownloadEntity` | `id: String` (`animeId_ep`) | Download queue status, percentage progress, local file paths, and byte sizes. |
| `SearchHistoryEntity` | `query: String` | Recent search queries ordered by `searchedAt DESC`. |

---

## 3. DataStore Preferences

Stored in `erumi_preferences.preferences_pb`:
- `preferred_quality`: String ("1080p", "720p", "Auto")
- `auto_next_episode`: Boolean (default: true)
- `default_audio`: String ("sub", "dub")
- `default_subtitle_lang`: String ("en")
- `subtitle_size`: Float (default: 1.0f)
- `skip_intro`: Boolean (default: true)
- `wifi_only_downloads`: Boolean (default: false)
- `app_theme`: String ("Dark Cinema")
