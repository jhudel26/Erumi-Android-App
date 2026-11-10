package com.erumi.nativeapp.engine.resolver

import com.erumi.nativeapp.core.utils.FuzzyMatcher
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.Episode
import com.erumi.nativeapp.domain.model.StreamSource
import com.erumi.nativeapp.engine.sources.AllAnimeSource
import com.erumi.nativeapp.engine.sources.AniDBSource
import com.erumi.nativeapp.engine.sources.AniListSource
import com.erumi.nativeapp.engine.sources.AniNekoSource
import com.erumi.nativeapp.engine.sources.HiAnimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpisodeResolver(
    private val hiAnimeSource: HiAnimeSource = HiAnimeSource(),
    private val aniDBSource: AniDBSource = AniDBSource(),
    private val aniNekoSource: AniNekoSource = AniNekoSource(),
    private val aniListSource: AniListSource = AniListSource(),
    private val allAnimeSource: AllAnimeSource = AllAnimeSource()
) {

    companion object {
        val TITLE_ANILIST_OVERRIDES = mapOf(
            "magic repo man" to 202250
        )
    }

    private val episodesMemoryCache = mutableMapOf<String, List<Episode>>()
    private val streamMemoryCache = mutableMapOf<String, StreamSource>()

    suspend fun getEpisodes(animeTitle: String, totalCatalogEpisodes: Int? = null): List<Episode> = withContext(Dispatchers.IO) {
        val cleanTitle = animeTitle.trim()
        val cacheKey = cleanTitle.lowercase()
        episodesMemoryCache[cacheKey]?.let { return@withContext it }

        android.util.Log.d("ErumiResolver", "getEpisodes for: $cleanTitle")

        // Try normal HiAnime candidates first

        val candidateTitles = mutableListOf<String>()
        candidateTitles.add(cleanTitle)
        candidateTitles.addAll(safePrefixCandidates(cleanTitle))

        for (cand in candidateTitles.distinct()) {
            android.util.Log.d("ErumiResolver", "  Trying candidate: $cand")
            val eps = hiAnimeSource.getEpisodes(cand)
            android.util.Log.d("ErumiResolver", "    -> ${eps.size} episodes found")
            if (eps.isNotEmpty()) {
                episodesMemoryCache[cacheKey] = eps
                return@withContext eps
            }
        }

        // Alternate catalog titles from a *validated* AniList hit.
        // Important: once English matches AniList, trust that media's romaji/synonyms as
        // HiAnime search keys. Do NOT require isValidTitleMatch(english, romaji) - those
        // share almost no words (Magic Repo Man ↔ Kashita Maryoku wa Revo Barai…).
        val searchItems = aniListSource.search(cleanTitle)
        android.util.Log.d("ErumiResolver", "AniList search returned ${searchItems.size} results")
        val match = pickValidAniListMatch(cleanTitle, searchItems)
        if (match != null) {
            android.util.Log.d("ErumiResolver", "AniList match: ${match.title} (en: ${match.englishName}, romaji: ${match.romajiName})")
            val altKeys = trustedHiAnimeKeys(match)
            android.util.Log.d("ErumiResolver", "Alternate keys: $altKeys")
            for (alt in altKeys) {
                if (alt.equals(cleanTitle, ignoreCase = true)) continue
                if (!seasonPartCompatible(cleanTitle, alt)) continue
                android.util.Log.d("ErumiResolver", "  Trying alt: $alt")
                val eps = hiAnimeSource.getEpisodes(alt)
                android.util.Log.d("ErumiResolver", "    -> ${eps.size} episodes found")
                if (eps.isNotEmpty()) {
                    episodesMemoryCache[cacheKey] = eps
                    return@withContext eps
                }
            }
        } else {
            android.util.Log.d("ErumiResolver", "No AniList match found")
        }

        // SAFETY FALLBACK: Magic Repo Man romaji title via AniList ID
        // AniList search returns 0 results for this anime, so we fetch by known ID
        val overrideId = TITLE_ANILIST_OVERRIDES.entries.firstOrNull { cleanTitle.contains(it.key, ignoreCase = true) }?.value
        if (overrideId != null) {
            android.util.Log.d("ErumiResolver", "Trying title override fallback via AniList ID $overrideId")
            val anilistMetadata = aniListSource.fetchById(overrideId)
            if (anilistMetadata != null) {
                android.util.Log.d("ErumiResolver", "  Fetched metadata from AniList: ${anilistMetadata.title} (romaji: ${anilistMetadata.romajiName})")
                val candidates = buildCandidateTitlesFromMetadata(anilistMetadata, cleanTitle)
                android.util.Log.d("ErumiResolver", "  Trying ${candidates.size} title candidates from metadata")
                for (cand in candidates) {
                    android.util.Log.d("ErumiResolver", "    - Trying: $cand")
                    val romajiEps = hiAnimeSource.getEpisodes(cand)
                    if (romajiEps.isNotEmpty()) {
                        android.util.Log.d("ErumiResolver", "\u2713 Magic Repo Man ID-based fallback worked: ${romajiEps.size} episodes with title: $cand")
                        episodesMemoryCache[cacheKey] = romajiEps
                        return@withContext romajiEps
                    }
                }
            } else {
                android.util.Log.d("ErumiResolver", "✗ Failed to fetch Magic Repo Man from AniList by ID")
            }
        }

        // Last resort: fabricate only when we know a real aired/catalog count.
        // Never invent "12" for airing shows with null AniList episodes (that caused
        // Magic Repo Man to show 12 fake eps with blank epIds).
        val count = when {
            totalCatalogEpisodes != null && totalCatalogEpisodes > 0 -> totalCatalogEpisodes
            match != null && match.episodes > 0 -> match.episodes
            else -> 0
        }
        if (count > 0) {
            val generated = (1..count).map { epNum ->
                Episode(episodeNumber = epNum, title = "Episode $epNum")
            }
            episodesMemoryCache[cacheKey] = generated
            return@withContext generated
        }

        emptyList()
    }

    suspend fun resolveStream(
        animeTitle: String,
        episodeNumber: Int,
        audio: String = "sub"
    ): StreamSource? = withContext(Dispatchers.IO) {
        val cleanTitle = animeTitle.trim()
        val cacheKey = "$cleanTitle-$episodeNumber-$audio".lowercase()
        streamMemoryCache[cacheKey]?.let { return@withContext it }

        android.util.Log.d("ErumiResolver", "Resolving stream for: $cleanTitle (ep $episodeNumber)")

        // 1. Primary: HiAnime with selected title
        val hiStream = hiAnimeSource.resolveStream(cleanTitle, episodeNumber, audio)
        if (hiStream != null) {
            android.util.Log.d("ErumiResolver", "\u2713 Primary HiAnime match found for: $cleanTitle")
            streamMemoryCache[cacheKey] = hiStream
            return@withContext hiStream
        }
        android.util.Log.d("ErumiResolver", "✗ Primary HiAnime failed for: $cleanTitle")

        // 1b. Safe title prefixes
        for (prefix in safePrefixCandidates(cleanTitle)) {
            if (prefix.equals(cleanTitle, ignoreCase = true)) continue
            val pStream = hiAnimeSource.resolveStream(prefix, episodeNumber, audio)
            if (pStream != null) {
                android.util.Log.d("ErumiResolver", "\u2713 Prefix match found: $prefix")
                streamMemoryCache[cacheKey] = pStream
                return@withContext pStream
            }
        }

        // 1c. Trusted AniList alts (romaji / synonyms) - same as web_server get_stream 1c
        val searchItems = aniListSource.search(cleanTitle)
        android.util.Log.d("ErumiResolver", "AniList search returned ${searchItems.size} results for: $cleanTitle")
        val match = pickValidAniListMatch(cleanTitle, searchItems)
        if (match != null) {
            android.util.Log.d("ErumiResolver", "\u2713 AniList match found: ${match.title} (en: ${match.englishName}, romaji: ${match.romajiName})")
            val altKeys = trustedHiAnimeKeys(match)
            android.util.Log.d("ErumiResolver", "Trying ${altKeys.size} alternate HiAnime keys: $altKeys")
            for (cand in altKeys) {
                if (cand.equals(cleanTitle, ignoreCase = true)) continue
                if (!seasonPartCompatible(cleanTitle, cand)) continue
                android.util.Log.d("ErumiResolver", "  - Trying alt key: $cand")
                val altStream = hiAnimeSource.resolveStream(cand, episodeNumber, audio)
                if (altStream != null) {
                    android.util.Log.d("ErumiResolver", "\u2713 Alt key success: $cand")
                    streamMemoryCache[cacheKey] = altStream
                    return@withContext altStream
                }
            }
        } else {
            android.util.Log.d("ErumiResolver", "✗ No valid AniList match found for: $cleanTitle")
        }

        // SAFETY FALLBACK: Magic Repo Man romaji title via AniList ID
        // AniList search returns 0 results for this anime, so we fetch by known ID
        val overrideId = TITLE_ANILIST_OVERRIDES.entries.firstOrNull { cleanTitle.contains(it.key, ignoreCase = true) }?.value
        if (overrideId != null) {
            android.util.Log.d("ErumiResolver", "Trying title override fallback via AniList ID $overrideId")
            val anilistMetadata = aniListSource.fetchById(overrideId)
            if (anilistMetadata != null) {
                android.util.Log.d("ErumiResolver", "  Fetched metadata from AniList: ${anilistMetadata.title} (romaji: ${anilistMetadata.romajiName})")
                val candidates = buildCandidateTitlesFromMetadata(anilistMetadata, cleanTitle)
                android.util.Log.d("ErumiResolver", "  Trying ${candidates.size} title candidates from metadata")
                for (cand in candidates) {
                    android.util.Log.d("ErumiResolver", "    - Trying: $cand")
                    val romajiStream = hiAnimeSource.resolveStream(cand, episodeNumber, audio)
                    if (romajiStream != null) {
                        android.util.Log.d("ErumiResolver", "\u2713 Magic Repo Man ID-based fallback stream worked with title: $cand")
                        streamMemoryCache[cacheKey] = romajiStream
                        return@withContext romajiStream
                    }
                }
            } else {
                android.util.Log.d("ErumiResolver", "✗ Failed to fetch Magic Repo Man from AniList by ID")
            }
        }

        // 2. AniDB Fallback (also try trusted alts)
        val anidbStream = aniDBSource.resolveStream(cleanTitle, episodeNumber, audio)
        if (anidbStream != null) {
            streamMemoryCache[cacheKey] = anidbStream
            return@withContext anidbStream
        }
        if (match != null) {
            for (cand in trustedHiAnimeKeys(match)) {
                if (cand.equals(cleanTitle, ignoreCase = true)) continue
                val alt = aniDBSource.resolveStream(cand, episodeNumber, audio)
                if (alt != null) {
                    streamMemoryCache[cacheKey] = alt
                    return@withContext alt
                }
            }
        }

        // 3. AniNeko Fallback
        val aninekoStream = aniNekoSource.resolveStream(cleanTitle, episodeNumber, audio)
        if (aninekoStream != null) {
            streamMemoryCache[cacheKey] = aninekoStream
            return@withContext aninekoStream
        }
        if (match != null) {
            for (cand in trustedHiAnimeKeys(match)) {
                if (cand.equals(cleanTitle, ignoreCase = true)) continue
                val alt = aniNekoSource.resolveStream(cand, episodeNumber, audio)
                if (alt != null) {
                    streamMemoryCache[cacheKey] = alt
                    return@withContext alt
                }
            }
        }

        null
    }

    /**
     * Builds candidate titles from AniList metadata, similar to web_server.py approach.
     * Tries romaji, english, userPreferred, and safe prefix variants.
     */
    private fun buildCandidateTitlesFromMetadata(metadata: Anime, originalTitle: String): List<String> {
        val candidates = mutableListOf<String>()

        // Add title fields from metadata
        metadata.romajiName?.trim()?.takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        metadata.englishName?.trim()?.takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        metadata.title.trim().takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        metadata.nativeName?.trim()?.takeIf { it.isNotEmpty() }?.let { candidates.add(it) }

        // Add synonyms
        metadata.synonyms.forEach { syn ->
            syn.trim().takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        }

        // Add cleaned romaji (remove brackets)
        metadata.romajiName?.let { romaji ->
            val cleaned = romaji.replace("[", " ").replace("]", " ").replace("ã€", " ").replace("ã€‘", " ")
                .replace(Regex("""\s+"""), " ").trim()
            if (cleaned.isNotEmpty() && cleaned != romaji) {
                candidates.add(cleaned)
            }
        }

        // Add safe prefix variants (like web_server.py)
        val qInfo = FuzzyMatcher.parseSeasonAndPart(originalTitle)
        val qSubs = FuzzyMatcher.extractSubtitleKeywords(originalTitle)
        val hasSpecificSeason =
            (qInfo.season != null && qInfo.season > 1) ||
                (qInfo.part != null && qInfo.part > 1) ||
                qSubs.isNotEmpty()

        for (cand in candidates.toList()) {
            if (" - " in cand) {
                val pref = cand.split(" - ")[0].trim()
                if (pref.isNotBlank() && pref !in candidates) {
                    if (!hasSpecificSeason || qSubs.any { it in pref.lowercase() }) {
                        candidates.add(pref)
                    }
                }
            }
            if (":" in cand && !hasSpecificSeason) {
                val pref = cand.split(":")[0].trim()
                if (pref.isNotBlank() && pref !in candidates) {
                    candidates.add(pref)
                }
            }
        }

        return candidates.distinctBy { it.lowercase() }
    }

    /**
     * HiAnime search keys taken from a validated AniList media object.
     * Romaji/synonyms are intentionally not cross-checked with English word overlap.
     */
    private fun trustedHiAnimeKeys(match: Anime): List<String> {
        return buildList {
            match.englishName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            match.romajiName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            match.title.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            match.nativeName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            match.synonyms.forEach { syn ->
                syn.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            }
            // Also try a version with brackets cleaned (for titles like [Revo Barai])
            match.romajiName?.let { romaji ->
                val cleaned = romaji.replace("[", " ").replace("]", " ").replace("ã€", " ").replace("ã€‘", " ")
                    .replace(Regex("""\s+"""), " ").trim()
                if (cleaned.isNotEmpty() && cleaned != romaji) {
                    add(cleaned)
                }
            }
        }.distinctBy { it.lowercase() }
    }

    private fun safePrefixCandidates(cleanTitle: String): List<String> {
        val qInfo = FuzzyMatcher.parseSeasonAndPart(cleanTitle)
        val qSubs = FuzzyMatcher.extractSubtitleKeywords(cleanTitle)
        val hasSpecificSeason =
            (qInfo.season != null && qInfo.season > 1) ||
                (qInfo.part != null && qInfo.part > 1) ||
                qSubs.isNotEmpty()

        val out = mutableListOf<String>()
        if (" - " in cleanTitle) {
            val pref = cleanTitle.substringBefore(" - ").trim()
            if (pref.isNotBlank() && (!hasSpecificSeason || qSubs.any { it in pref.lowercase() })) {
                out.add(pref)
            }
        }
        if (":" in cleanTitle && !hasSpecificSeason) {
            val pref = cleanTitle.substringBefore(":").trim()
            if (pref.isNotBlank()) out.add(pref)
        }
        return out
    }

    private fun seasonPartCompatible(query: String, candidate: String): Boolean {
        val qInfo = FuzzyMatcher.parseSeasonAndPart(query)
        val cInfo = FuzzyMatcher.parseSeasonAndPart(candidate)
        if (qInfo.season != null && cInfo.season != null && qInfo.season != cInfo.season) return false
        if (qInfo.part != null && cInfo.part != null && qInfo.part != cInfo.part) return false
        return true
    }

    /**
     * Picks the first AniList hit that is still a valid title match for [query].
     * AniList SEARCH_MATCH alone is not trustworthy for long isekai titles that
     * share generic words like "magic" / "strongest".
     *
     * For cases where English and Romaji share no words (e.g., Magic Repo Man ↔ Kashita Maryoku),
     * we trust AniList's SEARCH_MATCH ordering as a fallback.
     */
    private fun pickValidAniListMatch(query: String, items: List<Anime>): Anime? {
        val cleanQuery = FuzzyMatcher.cleanForSearch(query)
        android.util.Log.d("ErumiResolver", "pickValidAniListMatch: query='$query', clean='$cleanQuery', items=${items.size}")

        // First pass: strict word-based matching
        for (item in items) {
            val names = listOfNotNull(item.englishName, item.romajiName, item.title) + item.synonyms
            android.util.Log.d("ErumiResolver", "  Checking item: ${item.title} (en: ${item.englishName}, romaji: ${item.romajiName})")
            for (name in names) {
                val isValid = FuzzyMatcher.isValidTitleMatch(query, name)
                android.util.Log.d("ErumiResolver", "    - '$name' -> isValidTitleMatch=$isValid")
                if (isValid) {
                    android.util.Log.d("ErumiResolver", "  \u2713 Strict match found: ${item.title}")
                    return item
                }
            }
        }

        // Fallback: trust AniList's search ordering (SEARCH_MATCH sort)
        // This handles English ↔ Romaji cases with no word overlap
        if (items.isNotEmpty()) {
            android.util.Log.d("ErumiResolver", "Using AniList SEARCH_MATCH fallback for: $query -> ${items.first().title}")
            return items.first()
        }

        android.util.Log.d("ErumiResolver", "✗ No AniList match found")
        return null
    }
}
