package com.erumi.nativeapp.engine.resolver

import com.erumi.nativeapp.core.utils.FuzzyMatcher
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

    private val episodesMemoryCache = mutableMapOf<String, List<Episode>>()
    private val streamMemoryCache = mutableMapOf<String, StreamSource>()

    suspend fun getEpisodes(animeTitle: String, totalCatalogEpisodes: Int? = null): List<Episode> = withContext(Dispatchers.IO) {
        val cleanTitle = animeTitle.trim()
        val cacheKey = cleanTitle.lowercase()
        episodesMemoryCache[cacheKey]?.let { return@withContext it }

        val candidateTitles = mutableListOf<String>()
        candidateTitles.add(cleanTitle)

        val qInfo = FuzzyMatcher.parseSeasonAndPart(cleanTitle)
        val qSubs = FuzzyMatcher.extractSubtitleKeywords(cleanTitle)
        val hasSpecificSeason = (qInfo.season != null && qInfo.season > 1) || (qInfo.part != null && qInfo.part > 1) || qSubs.isNotEmpty()

        if (" - " in cleanTitle) {
            val pref = cleanTitle.substringBefore(" - ").trim()
            if (pref.isNotBlank() && (!hasSpecificSeason || qSubs.any { it in pref.lowercase() })) {
                candidateTitles.add(pref)
            }
        }
        if (":" in cleanTitle && !hasSpecificSeason) {
            val pref = cleanTitle.substringBefore(":").trim()
            if (pref.isNotBlank()) {
                candidateTitles.add(pref)
            }
        }

        for (cand in candidateTitles) {
            val eps = hiAnimeSource.getEpisodes(cand)
            if (eps.isNotEmpty()) {
                episodesMemoryCache[cacheKey] = eps
                return@withContext eps
            }
        }

        // Search alternate titles
        val searchItems = aniListSource.search(cleanTitle)
        val match = searchItems.firstOrNull()
        if (match != null) {
            val alts = listOfNotNull(match.englishName, match.romajiName, match.title).distinct()
            for (alt in alts) {
                if (!alt.equals(cleanTitle, ignoreCase = true)) {
                    val eps = hiAnimeSource.getEpisodes(alt)
                    if (eps.isNotEmpty()) {
                        episodesMemoryCache[cacheKey] = eps
                        return@withContext eps
                    }
                }
            }
        }

        // If not found in scrapers, generate real episode list using known catalog count
        val count = totalCatalogEpisodes ?: match?.episodes ?: 12
        if (count > 0) {
            val generated = (1..count).map { epNum ->
                Episode(episodeNumber = epNum, title = "Episode $epNum")
            }
            episodesMemoryCache[cacheKey] = generated
            return@withContext generated
        }

        listOf(Episode(episodeNumber = 1, title = "Episode 1"))
    }

    suspend fun resolveStream(
        animeTitle: String,
        episodeNumber: Int,
        audio: String = "sub"
    ): StreamSource? = withContext(Dispatchers.IO) {
        val cleanTitle = animeTitle.trim()
        val cacheKey = "$cleanTitle-$episodeNumber-$audio".lowercase()
        streamMemoryCache[cacheKey]?.let { return@withContext it }

        // 1. Primary: HiAnime
        val hiStream = hiAnimeSource.resolveStream(cleanTitle, episodeNumber, audio)
        if (hiStream != null) {
            streamMemoryCache[cacheKey] = hiStream
            return@withContext hiStream
        }

        // 1b. Try title prefixes if safe
        val qInfo = FuzzyMatcher.parseSeasonAndPart(cleanTitle)
        val qSubs = FuzzyMatcher.extractSubtitleKeywords(cleanTitle)
        val hasSpecificSeason = (qInfo.season != null && qInfo.season > 1) || (qInfo.part != null && qInfo.part > 1) || qSubs.isNotEmpty()

        if (" - " in cleanTitle || ":" in cleanTitle) {
            var prefix: String? = null
            if (" - " in cleanTitle) {
                val cand = cleanTitle.substringBefore(" - ").trim()
                if (!hasSpecificSeason || qSubs.any { it in cand.lowercase() }) {
                    prefix = cand
                }
            } else if (":" in cleanTitle && !hasSpecificSeason) {
                prefix = cleanTitle.substringBefore(":").trim()
            }

            if (!prefix.isNullOrBlank() && !prefix.equals(cleanTitle, ignoreCase = true)) {
                val pStream = hiAnimeSource.resolveStream(prefix, episodeNumber, audio)
                if (pStream != null) {
                    streamMemoryCache[cacheKey] = pStream
                    return@withContext pStream
                }
            }
        }

        // 1c. Alternate names from AniList
        val searchItems = aniListSource.search(cleanTitle)
        for (item in searchItems.take(3)) {
            val candidates = listOfNotNull(item.nameOrTitle(), item.englishName, item.romajiName).distinct()
            for (cand in candidates) {
                if (!cand.equals(cleanTitle, ignoreCase = true)) {
                    val altInfo = FuzzyMatcher.parseSeasonAndPart(cand)
                    if (qInfo.season != null && altInfo.season != null && qInfo.season != altInfo.season) continue
                    if (qInfo.part != null && altInfo.part != null && qInfo.part != altInfo.part) continue

                    val altStream = hiAnimeSource.resolveStream(cand, episodeNumber, audio)
                    if (altStream != null) {
                        streamMemoryCache[cacheKey] = altStream
                        return@withContext altStream
                    }
                }
            }
        }

        // 2. Secondary: AniDB Fallback
        val anidbStream = aniDBSource.resolveStream(cleanTitle, episodeNumber, audio)
        if (anidbStream != null) {
            streamMemoryCache[cacheKey] = anidbStream
            return@withContext anidbStream
        }

        // 3. Tertiary: AniNeko Fallback
        val aninekoStream = aniNekoSource.resolveStream(cleanTitle, episodeNumber, audio)
        if (aninekoStream != null) {
            streamMemoryCache[cacheKey] = aninekoStream
            return@withContext aninekoStream
        }

        null
    }

    private fun com.erumi.nativeapp.domain.model.Anime.nameOrTitle(): String {
        return englishName ?: title
    }
}
