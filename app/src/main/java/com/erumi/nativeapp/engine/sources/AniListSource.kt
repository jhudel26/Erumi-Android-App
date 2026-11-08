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
        if (clean.isBlank()) return@withContext emptyList()
        fetchCatalog(searchQuery = clean, limit = 40)
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
                  title { romaji english userPreferred }
                  status
                  format
                  episodes
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
                val tObj = m.getAsJsonObject("title")
                val english = tObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
                val romaji = tObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
                val userPref = tObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString
                val title = english ?: userPref ?: romaji ?: "Unknown"

                val cover = m.getAsJsonObject("coverImage")
                val poster = cover?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                    ?: cover?.get("large")?.takeIf { !it.isJsonNull }?.asString
                    ?: cover?.get("medium")?.takeIf { !it.isJsonNull }?.asString

                val banner = m.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
                val score = m.get("averageScore")?.takeIf { !it.isJsonNull }?.asDouble?.div(10.0)
                val eps = m.get("episodes")?.takeIf { !it.isJsonNull }?.asInt ?: 12
                val itemYear = m.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt
                val desc = m.get("description")?.takeIf { !it.isJsonNull }?.asString

                val genres = mutableListOf<String>()
                m.getAsJsonArray("genres")?.forEach { genres.add(it.asString) }

                list.add(
                    Anime(
                        id = "anilist-${m.get("id").asInt}",
                        title = title,
                        englishName = english,
                        romajiName = romaji,
                        poster = poster,
                        banner = banner,
                        description = desc,
                        genres = genres,
                        releaseYear = itemYear,
                        episodes = eps,
                        score = score,
                        status = m.get("status")?.takeIf { !it.isJsonNull }?.asString?.replace("_", " "),
                        format = m.get("format")?.takeIf { !it.isJsonNull }?.asString ?: "TV"
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchDetailedMetadata(title: String, year: Int? = null): Anime? = withContext(Dispatchers.IO) {
        val query = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 15) {
                media (search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                  id
                  title { romaji english userPreferred }
                  status
                  format
                  episodes
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
            var bestM = mediaList[0].asJsonObject
            var bestScore = -9999

            for (elem in mediaList) {
                val m = elem.asJsonObject
                var score = 0
                val tEng = m.getAsJsonObject("title")?.get("english")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val tRom = m.getAsJsonObject("title")?.get("romaji")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
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

            val tObj = bestM.getAsJsonObject("title")
            val english = tObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
            val romaji = tObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
            val userPref = tObj?.get("userPreferred")?.takeIf { !it.isJsonNull }?.asString
            val bestTitle = english ?: userPref ?: romaji ?: title

            val cover = bestM.getAsJsonObject("coverImage")
            val poster = cover?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                ?: cover?.get("large")?.takeIf { !it.isJsonNull }?.asString
            val banner = bestM.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
            val avgScore = bestM.get("averageScore")?.takeIf { !it.isJsonNull }?.asDouble?.div(10.0)
            val desc = bestM.get("description")?.takeIf { !it.isJsonNull }?.asString

            val relations = mutableListOf<AnimeRelation>()
            bestM.getAsJsonObject("relations")?.getAsJsonArray("edges")?.forEach { edgeElem ->
                val edge = edgeElem.asJsonObject
                val relType = edge.get("relationType")?.asString ?: "RELATION"
                val node = edge.getAsJsonObject("node")
                if (node != null && relType in listOf("SEQUEL", "PREQUEL", "SIDE_STORY")) {
                    val nId = node.get("id").asInt
                    val nTitleObj = node.getAsJsonObject("title")
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
                poster = poster,
                banner = banner,
                description = desc,
                genres = genres,
                releaseYear = bestM.get("seasonYear")?.takeIf { !it.isJsonNull }?.asInt,
                episodes = bestM.get("episodes")?.takeIf { !it.isJsonNull }?.asInt ?: 12,
                score = avgScore,
                status = bestM.get("status")?.takeIf { !it.isJsonNull }?.asString,
                format = bestM.get("format")?.takeIf { !it.isJsonNull }?.asString,
                relations = relations
            )
        } catch (e: Exception) {
            null
        }
    }
}
