# ERUMI NATIVE — SOURCE INTEGRATION DOCUMENTATION

## Overview

Erumi Native integrates multiple anime data and streaming providers. To ensure high availability and resilient playback, sources are organized with primary providers and dynamic fallbacks.

---

## 1. Catalog & Discovery Sources

### 1.1 AllAnime
- **Protocol:** GraphQL
- **API Endpoint:** `https://api.mkissa.net/api`
- **Referer:** `https://mkissa.to`
- **Capabilities:**
  - `shows(search: {query: ...})`: Full-text search with support for Japanese, English, and romanized titles.
  - `shows(search: {sortBy: "Latest_Update"})`: Live catalog feed of recently updated episodes.
  - `shows(search: {sortBy: "Trending"})`: Current trending and popular anime.
  - `shows(search: {genres: [...]})`: Filter by genre.

### 1.2 AniList
- **Protocol:** GraphQL
- **API Endpoint:** `https://graphql.anilist.co`
- **Capabilities:**
  - `Page.media(sort: [POPULARITY_DESC])`: High-quality fallback discovery feed.
  - `Page.media(search: $title)`: Metadata enrichment (1080p extraLarge posters, banners, descriptions, scores).
  - `media.relations`: Relational graph queries providing complete prequel, sequel, and side-story timelines.

---

## 2. Streaming Video Sources

### 2.1 HiAnime (Primary Video Engine)
- **Protocol:** HTTP Scraper & JSON API
- **Base URL:** `https://hianime.at`
- **Process:**
  1. Searches anime title via `/search?keyword={query}`.
  2. Scores matching titles via `FuzzyMatcher` (verifying exact season and part numbers).
  3. Queries episode list via `/api/theme/episode/list/{aid}` or `/ajax/v2/episode/list/{aid}`.
  4. Retrieves server list via `/api/theme/episode/servers?episodeId={epId}`.
  5. Decodes server hash (Base64) to find embed URL (e.g., `zokoanime.video` or `megaplay.buzz`).
  6. Fetches embed page and locates `window.__P = "..."`.
  7. Decrypts `__P` using `OtakuCipher` XOR key `otaku-embed-v1` to extract direct M3U8 streaming link and WebVTT subtitles.

### 2.2 AniDB (Secondary Video Fallback)
- **Protocol:** REST API & Embed Scraper
- **Base URL:** `https://anidb.app`
- **Process:**
  1. Title lookup via `/search/suggestions?q={title}`.
  2. Episode lookup via `/api/frontend/anime/{aid}/episodes`.
  3. Language resolution via `/api/frontend/episode/{epId}/languages` (`eng` for dub, `jpn` for sub).
  4. Embed page extraction of M3U8 source (`file: '...'`).

### 2.3 AniNeko (Tertiary Video Fallback)
- **Protocol:** HTML Scraper
- **Base URL:** `https://anineko.to`
- **Process:**
  1. Searches `/browser?keyword={query}`.
  2. Resolves slug and navigates to `/watch/{slug}/ep-{number}`.
  3. Parses server panels (`nv-server-grid`) for requested audio track.
  4. Extracts M3U8 streams from embed iframes.

---

## 3. Fallback Waterfall Architecture

```
User Requests Episode
         │
         ▼
[HiAnime Primary Resolver] ──(Success)──> Play Stream
         │ (Fails / No Stream)
         ▼
[Title Prefix & Subtitle Matcher] ──(Success)──> Play Stream
         │ (Fails)
         ▼
[AniList Alternate Title Resolver] ──(Success)──> Play Stream
         │ (Fails)
         ▼
[AniDB Fallback Resolver] ──(Success)──> Play Stream
         │ (Fails)
         ▼
[AniNeko Fallback Resolver] ──(Success)──> Play Stream
         │ (Fails)
         ▼
Report Error to User with Retry Button
```
