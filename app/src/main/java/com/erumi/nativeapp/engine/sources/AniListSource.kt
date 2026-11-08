package com.erumi.nativeapp.engine.sources

import com.erumi.nativeapp.core.common.Constants
import com.erumi.nativeapp.core.network.ApiClient
import com.erumi.nativeapp.core.utils.FuzzyMatcher
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.AnimeRelation
import com.erumi.nativeapp.domain.model.Episode
import com.erumi.nativeapp.domain.model.StreamSource
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AniListSource(private val client: OkHttpClient = ApiClient.okHttpClient) : AnimeSource {

    override val name: String = "AniList"
    private val gson = Gson()

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val clean = FuzzyMatcher.cleanForSearch(query)
        android.util.Log.d("ErumiAniList", "search: query='$query' -> clean='$clean'")
        if (clean.isBlank()) return@withContext emptyList()
        val results = fetchCatalog(searchQuery = clean, limit = 40)
        android.util.Log.d("ErumiAniList", "search returned ${results.size} results")
        results
    }

    override suspend fun getLatest(limit: Int): List<Anime> = withContext(Dispatchers.IO) {
        val res = fetchCatalog(sortBy = "UPDATED_AT_DESC", limit = limit)
        if (res.isNotEmpty()) res else fetchCatalog(sortBy = "TRENDING_DESC", limit = limit)
    }

    override suspend fun getPopular(limit: Int): List<Anime> = withContext(Dispatchers.IO) {
        fetchCatalog(sortBy = "POPULARITY_DESC", limit = limit)
    }

    override suspend fun browse(genre: String?, page: Int, limit: Int): List<Anime> = withContext(Dispatchers.IO) {
        val g = if (!genre.isNullOrBlank() && !genre.equals("All", ignoreCase = true)) genre else null
        fetchCatalog(genre = g, page = page, limit = limit, sortBy = "TRENDING_DESC")
    }

    override suspend fun getEpisodes(animeTitle: String, showId: String?): List<Episode> {
        return emptyList()
    }

    override suspend fun resolveStream(animeTitle: String, episodeNumber: Int, audio: String): StreamSource? {
        return null
    }

    fun fetchCatalog(
        sortBy: String = "TRENDING_DESC",
        limit: Int = 18,
        page: Int = 1,
        searchQuery: String? = null,
        genre: String? = null,
        year: Int? = null
    ): List<Anime> {
        val query = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort], ${'$'}search: String, ${'$'}genre: String, ${'$'}seasonYear: Int) {
              Page (page: ${'$'}page, perPage: ${'$'}perPage) {
                media (sort: ${'$'}sort, type: ANIME, isAdult: false, search: ${'$'}search, genre: ${'$'}genre, seasonYear: ${'$'}seasonYear) {
                  id
                  title { romaji english userPreferred native }
                  synonyms
                  status
                  format
                  episodes
                  nextAiringEpisode { episode }
                  seasonYear
                  coverImage { extraLarge large medium }
                  bannerImage
                  averageScore
                  description(asHtml: false)
                  genres
                }
              }
            }
        """.trimIndent()

        val variables = JsonObject().apply {
            addProperty("page", page)
            addProperty("perPage", limit)
            val sortArr = com.google.gson.JsonArray()
            sortArr.add(if (!searchQuery.isNullOrBlank()) "SEARCH_MATCH" else sortBy)
            add("sort", sortArr)
            if (!searchQuery.isNullOrBlank()) addProperty("search", searchQuery)
            if (!genre.isNullOrBlank()) addProperty("genre", genre)
            if (year != null) addProperty("seasonYear", year)
        }

        val reqBody = JsonObject().apply {
            addProperty("query", query)
            add("variables", variables)
        }

        val request = Request.Builder()
            .url(Constants.ANILIST_GQL_URL)
            .header("Content-Type", "application/json")
            .post(reqBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val text = client.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return emptyList()

            val root = gson.fromJson(text, JsonObject::class.java)
            val mediaList = root.getAsJsonObject("data")
                ?.getAsJsonObject("Page")
                ?.getAsJsonArray("media") ?: return emptyList()

            val list = mutableListOf<Anime>()
            for (elem in mediaList) {
                val m = elem.asJsonObject
                val tObj = m.get("title")?.takeIf { !it.isJsonNull }?.asJsonObject
                val english = tObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
                val romaji = tObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
                val native = tObj?.get("native")?.takeIf { !it.isJsonNull }?.asString
                val userPref = tObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString
                val title = english ?: userPref ?: romaji ?: "Unknown"

                val cover = m.get("coverImage")?.takeIf { !it.isJsonNull }?.asJsonObject
                val poster = cover?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                    ?: cover?.get("large")?.takeIf { !it.isJsonNull }?.asString
                    ?: cover?.get("medium")?.takeIf { !it.isJsonNull }?.asString

                val banner = m.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
                val score = m.get("averageScore")?.takeIf { !it.isJsonNull }?.asDouble?.div(10.0)
                val statusRaw = m.get("status")?.takeIf { !it.isJsonNull }?.asString
                val eps = resolveEpisodeCount(m, statusRaw)
                val itemYear = m.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt
                val desc = m.get("description")?.takeIf { !it.isJsonNull }?.asString
                val synonyms = parseSynonyms(m)

                val genres = mutableListOf<String>()
                m.getAsJsonArray("genres")?.forEach { genres.add(it.asString) }

                list.add(
                    Anime(
                        id = "anilist-${m.get("id").asInt}",
                        title = title,
                        englishName = english,
                        romajiName = romaji,
                        nativeName = native,
                        synonyms = synonyms,
                        poster = poster,
                        banner = banner,
                        description = desc,
                        genres = genres,
                        releaseYear = itemYear,
                        episodes = eps,
                        score = score,
                        status = statusRaw?.replace("_", " "),
                        format = m.get("format")?.takeIf { !it.isJsonNull }?.asString ?: "TV"
                    )
                )
            }
            list
        } catch (e: Exception) {
            android.util.Log.e("ErumiAniList", "fetchCatalog error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchDetailedMetadata(title: String, year: Int? = null): Anime? = withContext(Dispatchers.IO) {
        val query = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 15) {
                media (search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                  id
                  title { romaji english userPreferred native }
                  synonyms
                  status
                  format
                  episodes
                  nextAiringEpisode { episode }
                  seasonYear
                  coverImage { extraLarge large medium }
                  bannerImage
                  averageScore
                  description(asHtml: false)
                  genres
                  relations {
                    edges {
                      relationType
                      node {
                        id
                        title { romaji english }
                        episodes
                        format
                        seasonYear
                        status
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val variables = JsonObject().apply {
            addProperty("search", title)
        }

        val reqBody = JsonObject().apply {
            addProperty("query", query)
            add("variables", variables)
        }

        val request = Request.Builder()
            .url(Constants.ANILIST_GQL_URL)
            .header("Content-Type", "application/json")
            .post(reqBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val text = client.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return@withContext null

            val root = gson.fromJson(text, JsonObject::class.java)
            val mediaList = root.getAsJsonObject("data")
                ?.getAsJsonObject("Page")
                ?.getAsJsonArray("media") ?: return@withContext null

            if (mediaList.isEmpty) return@withContext null

            val targetInfo = FuzzyMatcher.parseSeasonAndPart(title)
            var bestM: JsonObject? = null
            var bestScore = Int.MIN_VALUE

            for (elem in mediaList) {
                val m = elem.asJsonObject
                val tObj = m.get("title")?.takeIf { !it.isJsonNull }?.asJsonObject
                val tEng = tObj?.get("english")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val tRom = tObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val tPref = tObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val nameCandidates = listOf(tEng, tRom, tPref).filter { it.isNotBlank() }
                // Reject AniList hits that only share weak fantasy words with the query.
                if (nameCandidates.none { FuzzyMatcher.isValidTitleMatch(title, it) }) continue

                var score = 0
                val bestNameSim = nameCandidates.maxOf { name ->
                    FuzzyMatcher.titleSimilarity(title, name) * 100.0 +
                        FuzzyMatcher.wordOverlapRatio(title, name) * 80.0
                }
                score += bestNameSim.toInt()

                val cInfoEng = FuzzyMatcher.parseSeasonAndPart(tEng)
                val cInfoRom = FuzzyMatcher.parseSeasonAndPart(tRom)
                val cSeason = cInfoEng.season ?: cInfoRom.season ?: 1
                val cPart = cInfoEng.part ?: cInfoRom.part

                val tSeason = targetInfo.season ?: 1
                if (tSeason == cSeason) score += 50
                else if (tSeason > 1 && cSeason == 1) score -= 40
                else if (tSeason == 1 && cSeason > 1) score -= 40
                else score -= 60

                if (targetInfo.part != null && cPart != null) {
                    if (targetInfo.part == cPart) score += 40 else score -= 60
                }

                val mYear = m.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt
                if (year != null && mYear != null) {
                    if (year == mYear) score += 30
                    else if (kotlin.math.abs(year - mYear) <= 1) score += 15
                }

                if (score > bestScore) {
                    bestScore = score
                    bestM = m
                }
            }

            // Prefer no metadata over swapping the user onto a different series.
            if (bestM == null) return@withContext null

            val tObj = bestM.get("title")?.takeIf { !it.isJsonNull }?.asJsonObject
            val english = tObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
            val romaji = tObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
            val native = tObj?.get("native")?.takeIf { !it.isJsonNull }?.asString
            val userPref = tObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString
            val bestTitle = english ?: userPref ?: romaji ?: title

            val cover = bestM.get("coverImage")?.takeIf { !it.isJsonNull }?.asJsonObject
            val poster = cover?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                ?: cover?.get("large")?.takeIf { !it.isJsonNull }?.asString
            val banner = bestM.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
            val avgScore = bestM.get("averageScore")?.takeIf { !it.isJsonNull }?.asDouble?.div(10.0)
            val desc = bestM.get("description")?.takeIf { !it.isJsonNull }?.asString
            val statusRaw = bestM.get("status")?.takeIf { !it.isJsonNull }?.asString
            val synonyms = parseSynonyms(bestM)

            val relations = mutableListOf<AnimeRelation>()
            bestM.get("relations")?.takeIf { !it.isJsonNull }?.asJsonObject?.getAsJsonArray("edges")?.forEach { edgeElem ->
                val edge = edgeElem.asJsonObject
                val relType = edge.get("relationType")?.asString ?: "RELATION"
                val node = edge.get("node")?.takeIf { !it.isJsonNull }?.asJsonObject
                if (node != null && relType in listOf("SEQUEL", "PREQUEL", "SIDE_STORY")) {
                    val nId = node.get("id").asInt
                    val nTitleObj = node.get("title")?.takeIf { !it.isJsonNull }?.asJsonObject
                    val nTitle = nTitleObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
                        ?: nTitleObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString ?: "Unknown"
                    val nFmt = node.get("format")?.takeIf { !it.isJsonNull }?.asString
                    val nYr = node.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt
                    val nStatus = node.get("status")?.takeIf { !it.isJsonNull }?.asString
                    relations.add(AnimeRelation(relType, nId, nTitle, nFmt, nYr, nStatus))
                }
            }

            val genres = mutableListOf<String>()
            bestM.getAsJsonArray("genres")?.forEach { genres.add(it.asString) }

            Anime(
                id = "anilist-${bestM.get("id").asInt}",
                title = bestTitle,
                englishName = english,
                romajiName = romaji,
                nativeName = native,
                synonyms = synonyms,
                poster = poster,
                banner = banner,
                description = desc,
                genres = genres,
                releaseYear = bestM.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt,
                episodes = resolveEpisodeCount(bestM, statusRaw),
                score = avgScore,
                status = statusRaw,
                format = bestM.get("format")?.takeIf { !it.isJsonNull }?.asString,
                relations = relations
            )
        } catch (e: Exception) {
            android.util.Log.e("ErumiAniList", "fetchDetailedMetadata error for '$title': ${e.message}", e)
            null
        }
    }

    suspend fun fetchById(anilistId: Int): Anime? = withContext(Dispatchers.IO) {
        val query = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english userPreferred native }
                synonyms
                status
                format
                episodes
                nextAiringEpisode { episode }
                seasonYear
                coverImage { extraLarge large medium }
                bannerImage
                averageScore
                description(asHtml: false)
                genres
              }
            }
        """.trimIndent()

        val variables = JsonObject().apply {
            addProperty("id", anilistId)
        }

        val reqBody = JsonObject().apply {
            addProperty("query", query)
            add("variables", variables)
        }

        val request = Request.Builder()
            .url(Constants.ANILIST_GQL_URL)
            .header("Content-Type", "application/json")
            .post(reqBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val text = client.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return@withContext null

            val root = gson.fromJson(text, JsonObject::class.java)
            val media = root.getAsJsonObject("data")
                ?.getAsJsonObject("Media") ?: return@withContext null

            val tObj = media.get("title")?.takeIf { !it.isJsonNull }?.asJsonObject
            val english = tObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
            val romaji = tObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
            val native = tObj?.get("native")?.takeIf { !it.isJsonNull }?.asString
            val userPref = tObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString
            val title = english ?: userPref ?: romaji ?: "Unknown"

            val cover = media.get("coverImage")?.takeIf { !it.isJsonNull }?.asJsonObject
            val poster = cover?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                ?: cover?.get("large")?.takeIf { !it.isJsonNull }?.asString
                ?: cover?.get("medium")?.takeIf { !it.isJsonNull }?.asString

            val banner = media.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
            val score = media.get("averageScore")?.takeIf { !it.isJsonNull }?.asDouble?.div(10.0)
            val statusRaw = media.get("status")?.takeIf { !it.isJsonNull }?.asString
            val eps = resolveEpisodeCount(media, statusRaw)
            val itemYear = media.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt
            val desc = media.get("description")?.takeIf { !it.isJsonNull }?.asString
            val synonyms = parseSynonyms(media)

            val genres = mutableListOf<String>()
            media.getAsJsonArray("genres")?.forEach { genres.add(it.asString) }

            Anime(
                id = "anilist-${media.get("id").asInt}",
                title = title,
                englishName = english,
                romajiName = romaji,
                nativeName = native,
                synonyms = synonyms,
                poster = poster,
                banner = banner,
                description = desc,
                genres = genres,
                releaseYear = itemYear,
                episodes = eps,
                score = score,
                status = statusRaw?.replace("_", " "),
                format = media.get("format")?.takeIf { !it.isJsonNull }?.asString ?: "TV"
            )
        } catch (e: Exception) {
            android.util.Log.e("ErumiAniList", "Error fetching by ID $anilistId: ${e.message}")
            null
        }
    }

    /**
     * Prefer real aired count for currently airing shows:
     * nextAiringEpisode.episode - 1. Never invent "12" when AniList episodes is null.
     */
    private fun resolveEpisodeCount(media: JsonObject, statusRaw: String?): Int {
        val catalogEps = media.get("episodes")?.takeIf { !it.isJsonNull }?.asInt
        val nextEp = media.get("nextAiringEpisode")
            ?.takeIf { !it.isJsonNull }
            ?.asJsonObject
            ?.get("episode")
            ?.takeIf { !it.isJsonNull }
            ?.asInt
        val airedFromNext = nextEp?.takeIf { it > 1 }?.minus(1)
        val status = statusRaw?.uppercase().orEmpty()
        val isAiring = status in setOf("RELEASING", "NOT_YET_RELEASED", "UPCOMING")

        return when {
            airedFromNext != null && airedFromNext > 0 -> airedFromNext
            catalogEps != null && catalogEps > 0 -> catalogEps
            isAiring -> 0
            else -> catalogEps ?: 0
        }
    }

    private fun parseSynonyms(media: JsonObject): List<String> {
        val arr = media.getAsJsonArray("synonyms") ?: return emptyList()
        return arr.mapNotNull { el ->
            el?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
