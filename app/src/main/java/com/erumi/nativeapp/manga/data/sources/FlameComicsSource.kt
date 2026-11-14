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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.Locale

/**
 * Modern SSR adapter for Flame Comics (https://flamecomics.xyz/).
 * Popular dedicated source for high-quality Action, Cultivation, and Martial Arts manhwa/manhua.
 */
class FlameComicsSource(
    private val client: OkHttpClient = MangaHttpClient.client
) : MangaSource {

    override val info = MangaSourceInfo(
        id = ID,
        name = "Flame Comics",
        baseUrl = BASE_URL,
        supportsDownloads = true
    )

    override fun supportedGenres(): List<String> = GENRES

    override suspend fun getPopular(page: Int): MangaResult<MangaPagedResult> =
        fetchSeriesList("$BASE_URL/browse")

    override suspend fun getLatest(page: Int): MangaResult<MangaPagedResult> =
        fetchSeriesList("$BASE_URL/browse")

    override suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getPopular(page)
        return fetchSeriesList("$BASE_URL/browse?search=$trimmed")
    }

    override suspend fun getByGenre(genre: String, page: Int): MangaResult<MangaPagedResult> =
        fetchSeriesList("$BASE_URL/browse")

    override suspend fun browse(
        page: Int,
        sort: MangaSortOption,
        genre: String?,
        status: String?
    ): MangaResult<MangaPagedResult> = fetchSeriesList("$BASE_URL/browse")

    override suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val html = fetchHtml(absolute)
            val doc = Jsoup.parse(html, absolute)

            val nextData = extractNextData(html)
            val seriesObj = nextData?.getAsJsonObject("props")
                ?.getAsJsonObject("pageProps")
                ?.getAsJsonObject("series")

            val title = seriesObj?.get("title")?.asString
                ?: doc.selectFirst("h1")?.text()?.trim()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.removeSuffix(" - Flame Comics")?.trim()
                ?: error("Title unavailable for $absolute")

            val seriesId = seriesObj?.get("series_id")?.asString ?: mangaUrl.substringAfter("/series/").substringBefore('/')
            val cover = seriesObj?.get("cover")?.asString?.let { "$CDN_BASE/uploads/images/series/$seriesId/$it" }
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")

            val description = seriesObj?.get("description")?.asString
                ?: doc.selectFirst("meta[name=description]")?.attr("content")

            val author = seriesObj?.getAsJsonArray("author")?.firstOrNull()?.asString
            val statusStr = seriesObj?.get("status")?.asString
            val tags = seriesObj?.getAsJsonArray("tags")?.map { it.asString }.orEmpty()

            val chapters = parseChapters(seriesObj, seriesId)

            val manga = Manga(
                id = "$ID:$seriesId",
                sourceId = ID,
                url = "$BASE_URL/series/$seriesId",
                title = title,
                coverUrl = cover,
                latestChapter = chapters.firstOrNull()?.name,
                status = parseStatus(statusStr),
                contentType = MangaContentType.MANHUA,
                genres = tags
            )

            MangaDetails(
                manga = manga,
                description = description,
                author = author,
                artist = author,
                chapterCount = chapters.size,
                updatedAt = chapters.firstOrNull()?.uploadedAt
            )
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Flame Comics details", it) }
        )
    }

    override suspend fun getChapters(mangaUrl: String): MangaResult<List<MangaChapter>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val html = fetchHtml(absolute)
            val nextData = extractNextData(html)
            val seriesObj = nextData?.getAsJsonObject("props")
                ?.getAsJsonObject("pageProps")
                ?.getAsJsonObject("series")

            val seriesId = seriesObj?.get("series_id")?.asString ?: mangaUrl.substringAfter("/series/").substringBefore('/')
            parseChapters(seriesObj, seriesId)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Flame Comics chapters", it) }
        )
    }

    override suspend fun getChapterPages(chapterUrl: String): MangaResult<List<MangaPage>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(chapterUrl)
            val html = fetchHtml(absolute)
            val nextData = extractNextData(html)
            val chapterObj = nextData?.getAsJsonObject("props")
                ?.getAsJsonObject("pageProps")
                ?.getAsJsonObject("chapter")

            val seriesId = chapterObj?.get("series_id")?.asString
                ?: absolute.substringAfter("/series/").substringBefore('/')
            val token = chapterObj?.get("token")?.asString
                ?: absolute.substringAfterLast('/')

            val pages = mutableListOf<MangaPage>()
            val imagesObj = chapterObj?.getAsJsonObject("images")
            if (imagesObj != null) {
                imagesObj.entrySet().forEachIndexed { index, entry ->
                    val imgObj = entry.value.asJsonObject
                    val fileName = imgObj.get("name")?.asString
                    if (!fileName.isNullOrBlank()) {
                        val imgUrl = "$CDN_BASE/uploads/images/series/$seriesId/$token/$fileName"
                        pages.add(MangaPage(index = index, imageUrl = imgUrl))
                    }
                }
            }

            if (pages.isEmpty()) {
                // Fallback to preload image links in head
                val preloadRegex = Regex("""https://cdn\.flamecomics\.xyz/uploads/images/series/[^"'\s]+\.(?:jpg|webp|png)""")
                val matched = preloadRegex.findAll(html).map { it.value }.distinct().toList()
                matched.forEachIndexed { idx, url ->
                    pages.add(MangaPage(index = idx, imageUrl = url))
                }
            }

            if (pages.isEmpty()) error("No reader pages found for $absolute")
            pages
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Flame Comics pages", it) }
        )
    }

    private suspend fun fetchSeriesList(url: String): MangaResult<MangaPagedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val html = fetchHtml(url)
            val nextData = extractNextData(html)
            val seriesArray = nextData?.getAsJsonObject("props")
                ?.getAsJsonObject("pageProps")
                ?.getAsJsonArray("series")

            val items = mutableListOf<Manga>()
            if (seriesArray != null) {
                for (el in seriesArray) {
                    val obj = el.asJsonObject
                    val id = obj.get("series_id")?.asString ?: continue
                    val title = obj.get("title")?.asString ?: continue
                    val cover = obj.get("cover")?.asString?.let { "$CDN_BASE/uploads/images/series/$id/$it" }
                    val typeStr = obj.get("type")?.asString.orEmpty()

                    val contentType = when {
                        "manhwa" in typeStr.lowercase(Locale.US) -> MangaContentType.MANHWA
                        "manga" in typeStr.lowercase(Locale.US) -> MangaContentType.MANGA
                        else -> MangaContentType.MANHUA
                    }

                    items.add(
                        Manga(
                            id = "$ID:$id",
                            sourceId = ID,
                            url = "$BASE_URL/series/$id",
                            title = title,
                            coverUrl = cover,
                            contentType = contentType
                        )
                    )
                }
            }

            MangaPagedResult(items = items, hasNextPage = false)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Flame Comics series", it) }
        )
    }

    private fun parseChapters(seriesObj: JsonObject?, seriesId: String): List<MangaChapter> {
        val chapterArray = seriesObj?.getAsJsonArray("chapters") ?: return emptyList()
        val list = mutableListOf<MangaChapter>()
        for (el in chapterArray) {
            val obj = el.asJsonObject
            val chNumStr = obj.get("chapter")?.asString.orEmpty()
            val chTitle = obj.get("title")?.asString.orEmpty()
            val token = obj.get("token")?.asString.orEmpty()
            val chNum = chNumStr.toFloatOrNull()

            val displayName = buildString {
                append("Chapter ").append(chNumStr.removeSuffix(".00"))
                if (chTitle.isNotBlank()) append(": ").append(chTitle)
            }

            val chUrl = "$BASE_URL/series/$seriesId/$token"
            list.add(
                MangaChapter(
                    id = "$ID:$seriesId/$token",
                    mangaId = "$ID:$seriesId",
                    sourceId = ID,
                    url = chUrl,
                    name = displayName,
                    number = chNum
                )
            )
        }
        return list
    }

    private fun extractNextData(html: String): JsonObject? {
        val match = Regex("""<script id="__NEXT_DATA__" type="application/json">([^<]+)</script>""").find(html)
            ?: return null
        return runCatching {
            JsonParser.parseString(match.groupValues[1]).asJsonObject
        }.getOrNull()
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", MangaHttpClient.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", BASE_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return response.body?.string().orEmpty()
        }
    }

    private fun parseStatus(text: String?): MangaPublicationStatus = when (text?.lowercase(Locale.US)?.trim()) {
        "ongoing" -> MangaPublicationStatus.ONGOING
        "completed" -> MangaPublicationStatus.COMPLETED
        "dropped" -> MangaPublicationStatus.CANCELLED
        "hiatus" -> MangaPublicationStatus.ON_HOLD
        else -> MangaPublicationStatus.UNKNOWN
    }

    private fun absolutize(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("http") -> trimmed
            trimmed.startsWith("/") -> "$BASE_URL$trimmed"
            else -> "$BASE_URL/$trimmed"
        }
    }

    companion object {
        const val ID = "flamecomics"
        const val BASE_URL = "https://flamecomics.xyz"
        private const val CDN_BASE = "https://cdn.flamecomics.xyz"

        val GENRES = listOf(
            "Action", "Adventure", "Cultivation", "Fantasy", "Martial Arts",
            "Magic", "Comedy", "Drama", "Shounen", "Supernatural"
        )
    }
}
