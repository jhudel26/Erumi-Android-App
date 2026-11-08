package com.erumi.nativeapp.engine.sources

import com.erumi.nativeapp.core.common.Constants
import com.erumi.nativeapp.core.network.ApiClient
import com.erumi.nativeapp.core.utils.FuzzyMatcher
import com.erumi.nativeapp.domain.model.Anime
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

class AllAnimeSource(private val client: OkHttpClient = ApiClient.okHttpClient) : AnimeSource {

    override val name: String = "AllAnime"
    private val gson = Gson()

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val clean = FuzzyMatcher.cleanForSearch(query)
        if (clean.isBlank()) return@withContext emptyList()

        var results = fetchShows(queryStr = clean, translationType = "sub")
        if (results.isEmpty()) {
            results = fetchShows(queryStr = clean, translationType = "dub")
        }

        // Rank results using scoring logic
        results.sortedByDescending {
            FuzzyMatcher.scoreSearchResult(
                query = clean,
                title = it.title,
                englishName = it.englishName,
                romajiName = it.romajiName,
                episodes = it.episodes,
                type = it.format,
                status = it.status
            )
        }
    }

    override suspend fun getLatest(limit: Int): List<Anime> = withContext(Dispatchers.IO) {
        fetchShows(sortBy = "Latest_Update", limit = limit)
    }

    override suspend fun getPopular(limit: Int): List<Anime> = withContext(Dispatchers.IO) {
        fetchShows(sortBy = "Trending", limit = limit)
    }

    override suspend fun browse(genre: String?, page: Int, limit: Int): List<Anime> = withContext(Dispatchers.IO) {
        val sortBy = if (!genre.isNullOrBlank()) "Latest_Update" else "Trending"
        val genres = if (!genre.isNullOrBlank() && !genre.equals("All", ignoreCase = true)) listOf(genre) else null
        fetchShows(sortBy = sortBy, limit = limit, page = page, genres = genres)
    }

    override suspend fun getEpisodes(animeTitle: String, showId: String?): List<Episode> {
        return emptyList() // Handled via EpisodeResolver waterfall
    }

    override suspend fun resolveStream(animeTitle: String, episodeNumber: Int, audio: String): StreamSource? {
        return null // Handled via HiAnime, AniDB, AniNeko
    }

    private fun fetchShows(
        queryStr: String? = null,
        sortBy: String? = null,
        limit: Int = 40,
        page: Int = 1,
        genres: List<String>? = null,
        translationType: String = "sub"
    ): List<Anime> {
        val searchParams = JsonObject().apply {
            addProperty("allowAdult", false)
            addProperty("allowUnknown", false)
            if (!queryStr.isNullOrBlank()) {
                addProperty("query", queryStr)
            } else if (!sortBy.isNullOrBlank()) {
                addProperty("sortBy", sortBy)
                addProperty("sortDirection", "DSC")
            }
            if (!genres.isNullOrEmpty()) {
                val gArr = com.google.gson.JsonArray()
                genres.forEach { gArr.add(it) }
                add("genres", gArr)
            }
        }

        val gql = """
            query (${'$'}search: SearchInput, ${'$'}limit: Int, ${'$'}page: Int, ${'$'}translationType: VaildTranslationTypeEnumType, ${'$'}countryOrigin: VaildCountryOriginEnumType) {
              shows(search: ${'$'}search, limit: ${'$'}limit, page: ${'$'}page, translationType: ${'$'}translationType, countryOrigin: ${'$'}countryOrigin) {
                edges {
                  _id
                  name
                  englishName
                  type
                  season
                  availableEpisodes
                  episodeCount
                  status
                  genres
                  thumbnail
                  banner
                }
              }
            }
        """.trimIndent()

        val variables = JsonObject().apply {
            add("search", searchParams)
            addProperty("limit", limit)
            addProperty("page", page)
            addProperty("translationType", translationType)
            addProperty("countryOrigin", "ALL")
        }

        val reqBody = JsonObject().apply {
            addProperty("query", gql)
            add("variables", variables)
        }

        val request = Request.Builder()
            .url(Constants.ALLANIME_API_URL)
            .header("Content-Type", "application/json")
            .header("Origin", Constants.ALLANIME_REFERER)
            .header("Referer", Constants.ALLANIME_REFERER)
            .post(reqBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val text = client.newCall(request).execute().use { response ->
                response.body?.string()
            } ?: return emptyList()

            val root = gson.fromJson(text, JsonObject::class.java)
            val edges = root.getAsJsonObject("data")
                ?.getAsJsonObject("shows")
                ?.getAsJsonArray("edges") ?: return emptyList()

            val list = mutableListOf<Anime>()
            for (elem in edges) {
                val edge = elem.asJsonObject
                val title = edge.get("englishName")?.takeIf { !it.isJsonNull }?.asString
                    ?: edge.get("name")?.takeIf { !it.isJsonNull }?.asString ?: continue

                val avail = edge.getAsJsonObject("availableEpisodes")
                val subCount = avail?.get("sub")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val dubCount = avail?.get("dub")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val rawCount = avail?.get("raw")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val epCount = edge.get("episodeCount")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val totalEps = maxOf(subCount, dubCount, rawCount, epCount, 1)

                var year: Int? = null
                val seasonObj = edge.get("season")
                if (seasonObj != null && seasonObj.isJsonObject) {
                    year = seasonObj.asJsonObject.get("year")?.takeIf { !it.isJsonNull }?.asInt
                }

                val rawThumb = edge.get("thumbnail")?.takeIf { !it.isJsonNull }?.asString
                val poster = if (rawThumb?.startsWith("http") == true) rawThumb else null
                val banner = edge.get("banner")?.takeIf { !it.isJsonNull }?.asString

                val genreList = mutableListOf<String>()
                edge.getAsJsonArray("genres")?.forEach { genreList.add(it.asString) }

                list.add(
                    Anime(
                        id = "allanime-${edge.get("_id")?.asString}",
                        title = title,
                        englishName = edge.get("englishName")?.takeIf { !it.isJsonNull }?.asString,
                        romajiName = edge.get("name")?.takeIf { !it.isJsonNull }?.asString,
                        poster = poster,
                        banner = banner,
                        genres = genreList,
                        releaseYear = year,
                        episodes = totalEps,
                        status = edge.get("status")?.takeIf { !it.isJsonNull }?.asString,
                        format = edge.get("type")?.takeIf { !it.isJsonNull }?.asString ?: "TV"
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
