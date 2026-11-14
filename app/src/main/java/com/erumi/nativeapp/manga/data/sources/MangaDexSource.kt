package com.erumi.nativeapp.manga.data.sources

import com.erumi.nativeapp.manga.domain.models.Manga
import com.erumi.nativeapp.manga.domain.models.MangaChapter
import com.erumi.nativeapp.manga.domain.models.MangaContentType
import com.erumi.nativeapp.manga.domain.models.MangaDetails
import com.erumi.nativeapp.manga.domain.models.MangaPage
import com.erumi.nativeapp.manga.domain.models.MangaPagedResult
import com.erumi.nativeapp.manga.domain.models.MangaPublicationStatus
import com.erumi.nativeapp.manga.domain.models.MangaResult
import com.erumi.nativeapp.manga.domain.models.MangaSortOption
import com.erumi.nativeapp.manga.domain.models.MangaSourceInfo
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Public REST API adapter for MangaDex (v5).
 * Uses official public API at https://api.mangadex.org/.
 */
class MangaDexSource(
    private val client: OkHttpClient = MangaHttpClient.client,
    private val gson: Gson = Gson()
) : MangaSource {

    override val info = MangaSourceInfo(
        id = ID,
        name = "MangaDex",
        baseUrl = BASE_URL,
        supportsDownloads = true
    )

    override fun supportedGenres(): List<String> = GENRES

    override suspend fun getPopular(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList(page = page, sortKey = "order[followedCount]", sortDir = "desc")

    override suspend fun getLatest(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList(page = page, sortKey = "order[latestUploadedChapter]", sortDir = "desc")

    override suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getPopular(page)
        val offset = (page.coerceAtLeast(1) - 1) * PAGE_LIMIT
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("title", trimmed)
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .build()
            .toString()
        return executeMangaQuery(url)
    }

    override suspend fun getByGenre(genre: String, page: Int): MangaResult<MangaPagedResult> {
        val tagId = GENRE_TO_TAG_ID[genre.lowercase(Locale.US)]
        val offset = (page.coerceAtLeast(1) - 1) * PAGE_LIMIT
        val urlBuilder = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("order[followedCount]", "desc")
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")

        if (tagId != null) {
            urlBuilder.addQueryParameter("includedTags[]", tagId)
        }

        return executeMangaQuery(urlBuilder.build().toString())
    }

    override suspend fun browse(
        page: Int,
        sort: MangaSortOption,
        genre: String?,
        status: String?
    ): MangaResult<MangaPagedResult> {
        if (!genre.isNullOrBlank()) return getByGenre(genre, page)
        val (sortKey, sortDir) = when (sort) {
            MangaSortOption.POPULARITY -> "order[followedCount]" to "desc"
            MangaSortOption.LATEST_UPDATES -> "order[latestUploadedChapter]" to "desc"
            MangaSortOption.RATING -> "order[rating]" to "desc"
            MangaSortOption.RECENTLY_ADDED -> "order[createdAt]" to "desc"
            MangaSortOption.ALPHABETICAL -> "order[title]" to "asc"
        }
        return fetchMangaList(page, sortKey, sortDir)
    }

    override suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val mangaId = extractMangaId(mangaUrl)
            val url = API_BASE.toHttpUrl().newBuilder()
                .addPathSegment("manga")
                .addPathSegment(mangaId)
                .addQueryParameter("includes[]", "cover_art")
                .addQueryParameter("includes[]", "author")
                .addQueryParameter("includes[]", "artist")
                .build()
                .toString()
            val json = fetchJson(url)
            val data = json.getAsJsonObject("data") ?: error("Manga data missing")
            val attrs = data.getAsJsonObject("attributes") ?: JsonObject()
            val relationships = data.getAsJsonArray("relationships")

            val title = extractTitle(attrs.getAsJsonObject("title"))
            val altTitles = attrs.getAsJsonArray("altTitles")?.mapNotNull { el ->
                if (el.isJsonObject) extractTitle(el.asJsonObject) else null
            }.orEmpty()

            val description = extractLocalizedText(attrs.getAsJsonObject("description"))
            val coverFileName = relationships?.firstOrNull {
                it.asJsonObject.get("type")?.asString == "cover_art"
            }?.asJsonObject?.getAsJsonObject("attributes")?.get("fileName")?.asString

            val coverUrl = if (!coverFileName.isNullOrBlank()) {
                "$COVERS_BASE/$mangaId/$coverFileName.512.jpg"
            } else null

            val author = relationships?.firstOrNull {
                it.asJsonObject.get("type")?.asString == "author"
            }?.asJsonObject?.getAsJsonObject("attributes")?.get("name")?.asString

            val artist = relationships?.firstOrNull {
                it.asJsonObject.get("type")?.asString == "artist"
            }?.asJsonObject?.getAsJsonObject("attributes")?.get("name")?.asString ?: author

            val status = parseStatus(attrs.get("status")?.asString)
            val origLang = attrs.get("originalLanguage")?.asString.orEmpty()
            val contentType = inferContentType(origLang)

            val tags = attrs.getAsJsonArray("tags")?.mapNotNull {
                it.asJsonObject.getAsJsonObject("attributes")
                    ?.getAsJsonObject("name")
                    ?.get("en")?.asString
            }.orEmpty()

            val manga = Manga(
                id = "$ID:$mangaId",
                sourceId = ID,
                url = "$BASE_URL/title/$mangaId",
                title = title,
                coverUrl = coverUrl,
                status = status,
                contentType = contentType,
                genres = tags
            )

            MangaDetails(
                manga = manga,
                alternativeTitles = altTitles,
                description = description,
                author = author,
                artist = artist,
                chapterCount = 0
            )
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load MangaDex details", it) }
        )
    }

    override suspend fun getChapters(mangaUrl: String): MangaResult<List<MangaChapter>> = withContext(Dispatchers.IO) {
        runCatching {
            val mangaId = extractMangaId(mangaUrl)
            val chapters = mutableListOf<MangaChapter>()
            var offset = 0
            val limit = 100

            while (true) {
                val url = API_BASE.toHttpUrl().newBuilder()
                    .addPathSegment("manga")
                    .addPathSegment(mangaId)
                    .addPathSegment("feed")
                    .addQueryParameter("translatedLanguage[]", "en")
                    .addQueryParameter("order[chapter]", "desc")
                    .addQueryParameter("order[volume]", "desc")
                    .addQueryParameter("limit", limit.toString())
                    .addQueryParameter("offset", offset.toString())
                    .addQueryParameter("includeExternalUrl", "0")
                    .build()
                    .toString()
                val json = fetchJson(url)
                val data = json.getAsJsonArray("data") ?: break
                if (data.size() == 0) break

                for (el in data) {
                    val obj = el.asJsonObject
                    val chId = obj.get("id")?.asString ?: continue
                    val attrs = obj.getAsJsonObject("attributes") ?: JsonObject()
                    val chapterNum = attrs.get("chapter")?.takeIf { !it.isJsonNull }?.asString
                    val chapterTitle = attrs.get("title")?.takeIf { !it.isJsonNull }?.asString
                    val publishAt = attrs.get("publishAt")?.takeIf { !it.isJsonNull }?.asString

                    val displayName = buildString {
                        if (!chapterNum.isNullOrBlank()) {
                            append("Chapter ").append(chapterNum)
                            if (!chapterTitle.isNullOrBlank()) {
                                append(": ").append(chapterTitle)
                            }
                        } else if (!chapterTitle.isNullOrBlank()) {
                            append(chapterTitle)
                        } else {
                            append("Chapter")
                        }
                    }

                    chapters.add(
                        MangaChapter(
                            id = "$ID:$chId",
                            mangaId = "$ID:$mangaId",
                            sourceId = ID,
                            url = "$BASE_URL/chapter/$chId",
                            name = displayName,
                            number = chapterNum?.toFloatOrNull(),
                            uploadedAt = parseIsoDate(publishAt)
                        )
                    )
                }

                val total = json.get("total")?.asInt ?: chapters.size
                offset += data.size()
                if (offset >= total || data.size() < limit) break
            }

            chapters.distinctBy { it.id }
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load MangaDex chapters", it) }
        )
    }

    override suspend fun getChapterPages(chapterUrl: String): MangaResult<List<MangaPage>> = withContext(Dispatchers.IO) {
        runCatching {
            val chapterId = extractChapterId(chapterUrl)
            val url = "$API_BASE/at-home/server/$chapterId"
            val json = fetchJson(url)
            val baseUrl = json.get("baseUrl")?.asString ?: error("BaseUrl missing in at-home response")
            val chapterObj = json.getAsJsonObject("chapter") ?: error("Chapter object missing in at-home response")
            val hash = chapterObj.get("hash")?.asString ?: error("Chapter hash missing")
            val data = chapterObj.getAsJsonArray("data") ?: error("Page data missing")

            val pages = mutableListOf<MangaPage>()
            for (i in 0 until data.size()) {
                val fileName = data.get(i).asString
                val pageUrl = "$baseUrl/data/$hash/$fileName"
                pages.add(MangaPage(index = i, imageUrl = pageUrl))
            }
            pages
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load MangaDex pages", it) }
        )
    }

    private suspend fun fetchMangaList(page: Int, sortKey: String, sortDir: String): MangaResult<MangaPagedResult> {
        val offset = (page.coerceAtLeast(1) - 1) * PAGE_LIMIT
        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter(sortKey, sortDir)
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .build()
            .toString()
        return executeMangaQuery(url)
    }

    private suspend fun executeMangaQuery(url: String): MangaResult<MangaPagedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val json = fetchJson(url)
            val data = json.getAsJsonArray("data") ?: return@runCatching MangaPagedResult(emptyList(), false)
            val total = json.get("total")?.asInt ?: 0
            val offset = json.get("offset")?.asInt ?: 0
            val limit = json.get("limit")?.asInt ?: PAGE_LIMIT

            val mangas = mutableListOf<Manga>()
            for (el in data) {
                val obj = el.asJsonObject
                val mangaId = obj.get("id")?.asString ?: continue
                val attrs = obj.getAsJsonObject("attributes") ?: JsonObject()
                val relationships = obj.getAsJsonArray("relationships")

                val title = extractTitle(attrs.getAsJsonObject("title"))
                if (title.isBlank()) continue

                val coverFileName = relationships?.firstOrNull {
                    it.asJsonObject.get("type")?.asString == "cover_art"
                }?.asJsonObject?.getAsJsonObject("attributes")?.get("fileName")?.asString

                val coverUrl = if (!coverFileName.isNullOrBlank()) {
                    "$COVERS_BASE/$mangaId/$coverFileName.512.jpg"
                } else null

                val origLang = attrs.get("originalLanguage")?.asString.orEmpty()
                val status = parseStatus(attrs.get("status")?.asString)

                mangas.add(
                    Manga(
                        id = "$ID:$mangaId",
                        sourceId = ID,
                        url = "$BASE_URL/title/$mangaId",
                        title = title,
                        coverUrl = coverUrl,
                        status = status,
                        contentType = inferContentType(origLang)
                    )
                )
            }

            val hasNext = (offset + limit) < total
            MangaPagedResult(mangas, hasNext)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to fetch MangaDex manga list", it) }
        )
    }

    private fun fetchJson(url: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("MangaDex API error HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            return gson.fromJson(body, JsonObject::class.java)
        }
    }

    private fun extractTitle(titleObj: JsonObject?): String {
        if (titleObj == null) return ""
        val en = titleObj.get("en")?.asString
        if (!en.isNullOrBlank()) return en
        val firstEntry = titleObj.entrySet().firstOrNull()
        return firstEntry?.value?.asString.orEmpty()
    }

    private fun extractLocalizedText(obj: JsonObject?): String? {
        if (obj == null) return null
        val en = obj.get("en")?.asString
        if (!en.isNullOrBlank()) return en
        return obj.entrySet().firstOrNull()?.value?.asString
    }

    private fun extractMangaId(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("$ID:") -> trimmed.removePrefix("$ID:")
            trimmed.contains("/title/") -> trimmed.substringAfter("/title/").substringBefore('/').substringBefore('?')
            trimmed.contains("/manga/") -> trimmed.substringAfter("/manga/").substringBefore('/').substringBefore('?')
            else -> trimmed
        }
    }

    private fun extractChapterId(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("$ID:") -> trimmed.removePrefix("$ID:")
            trimmed.contains("/chapter/") -> trimmed.substringAfter("/chapter/").substringBefore('/').substringBefore('?')
            else -> trimmed
        }
    }

    private fun parseStatus(text: String?): MangaPublicationStatus = when (text?.lowercase(Locale.US)) {
        "ongoing" -> MangaPublicationStatus.ONGOING
        "completed" -> MangaPublicationStatus.COMPLETED
        "hiatus" -> MangaPublicationStatus.ON_HOLD
        "cancelled" -> MangaPublicationStatus.CANCELLED
        else -> MangaPublicationStatus.UNKNOWN
    }

    private fun inferContentType(lang: String): MangaContentType = when (lang.lowercase(Locale.US)) {
        "ja" -> MangaContentType.MANGA
        "ko" -> MangaContentType.MANHWA
        "zh", "zh-ro", "zh-hk" -> MangaContentType.MANHUA
        else -> MangaContentType.MANGA
    }

    private fun parseIsoDate(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(iso.substringBefore('+'))?.time
        }.getOrNull()
    }

    companion object {
        const val ID = "mangadex"
        const val BASE_URL = "https://mangadex.org"
        private const val API_BASE = "https://api.mangadex.org"
        private const val COVERS_BASE = "https://uploads.mangadex.org/covers"
        private const val USER_AGENT = "ErumiNative/1.0.0 (Android; mobile)"
        private const val PAGE_LIMIT = 20

        val GENRES = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror",
            "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life",
            "Sports", "Supernatural", "Thriller", "Isekai", "Historical",
            "Martial Arts", "School Life"
        )

        private val GENRE_TO_TAG_ID = mapOf(
            "action" to "391b0423-d847-456f-aff0-8b0cfc03066b",
            "adventure" to "87cc87cd-a395-47af-b27a-93258283bbc6",
            "comedy" to "4d32cc48-9f00-4cca-9b5a-a839f0764984",
            "drama" to "b9af3a63-f058-46de-a9a0-e0c13906197a",
            "fantasy" to "cdc585f5-271b-45b6-a320-f5ce5e0343c8",
            "horror" to "cdad7e68-07dd-4270-a3e3-7ce037777ba0",
            "mystery" to "ee968142-be90-4d0a-837a-9952fb06d57b",
            "psychological" to "3b60b75c-a2d7-4860-ab56-05f391bb889c",
            "romance" to "423e2caf-b653-4084-8376-2c3f838359d2",
            "sci-fi" to "256c8bd9-4904-450f-bf8f-50d4a132e4cd",
            "slice of life" to "e5301a23-ebd9-49dd-a0cb-2add944c7fe9",
            "sports" to "69964a6f-2f90-4d42-a5f7-02c4877e6f42",
            "supernatural" to "eabc5b52-d13c-49c7-8f1c-7b1ae97f398f",
            "thriller" to "07044577-7622-4a1e-ac4a-35496143d7d0",
            "isekai" to "ace04997-f6bd-4329-8b0c-ad893471340c",
            "historical" to "33771934-028e-4cf3-8744-697e30e1604d",
            "martial arts" to "799c8342-a38f-4d44-9c6c-e3b86c189d25"
        )
    }
}
