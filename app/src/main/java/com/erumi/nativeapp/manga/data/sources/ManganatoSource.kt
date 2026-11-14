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
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Public HTML and REST API adapter for Manganato / Manganelo network (https://www.manganato.gg/).
 * Traditional server-rendered manga catalog with official JSON chapter API.
 */
class ManganatoSource(
    private val client: OkHttpClient = MangaHttpClient.client
) : MangaSource {

    override val info = MangaSourceInfo(
        id = ID,
        name = "Manganato",
        baseUrl = BASE_URL,
        supportsDownloads = true
    )

    override fun supportedGenres(): List<String> = GENRES

    override suspend fun getPopular(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList("$BASE_URL/manga-list/hot-manga?page=$page")

    override suspend fun getLatest(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList("$BASE_URL/manga-list/latest-manga?page=$page")

    override suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getPopular(page)
        val formatted = trimmed.replace(Regex("""[^a-zA-Z0-9]+"""), "_")
        val url = "$BASE_URL/search/story/$formatted?page=$page"
        return fetchMangaList(url)
    }

    override suspend fun getByGenre(genre: String, page: Int): MangaResult<MangaPagedResult> {
        val slug = genre.trim().lowercase(Locale.US).replace(' ', '-')
        val url = "$BASE_URL/genre/$slug?page=$page"
        return fetchMangaList(url)
    }

    override suspend fun browse(
        page: Int,
        sort: MangaSortOption,
        genre: String?,
        status: String?
    ): MangaResult<MangaPagedResult> {
        if (!genre.isNullOrBlank()) return getByGenre(genre, page)
        val path = when (sort) {
            MangaSortOption.POPULARITY, MangaSortOption.RATING -> "hot-manga"
            MangaSortOption.LATEST_UPDATES -> "latest-manga"
            MangaSortOption.RECENTLY_ADDED -> "new-manga"
            MangaSortOption.ALPHABETICAL -> "latest-manga"
        }
        return fetchMangaList("$BASE_URL/manga-list/$path?page=$page")
    }

    override suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)

            val title = doc.selectFirst("ul.manga-info-text h1, .story-info-right h1, h1")?.text()?.trim()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                ?: error("Title unavailable for $absolute")

            val ogImage = doc.selectFirst("meta[property=og:image], meta[name=twitter:image]")?.attr("content")?.trim()
                ?.takeIf { it.isNotEmpty() && !it.contains("default_nato") && !it.contains("logo") }

            val domImage = doc.selectFirst(".manga-info-pic img, .story-info-left img, .panel-story-info img")?.imgAttr()
                ?.takeIf { it.isNotEmpty() && !it.contains("default_nato") && !it.contains("logo") }

            val cover = ogImage ?: domImage

            val description = doc.selectFirst("#contentBox, .panel-story-info-description, #panel-story-info-description")?.text()
                ?.removePrefix("Description :")?.trim()

            val author = doc.select("li:contains(Author) a, tr:has(td:contains(Author)) td.table-value a, tr:contains(Author) td.table-value")
                .firstOrNull()?.text()?.trim()

            val statusText = doc.select("li:contains(Status), tr:has(td:contains(Status)) td.table-value")
                .firstOrNull()?.text()?.replace(Regex("(?i)status\\s*:"), "")?.trim()

            val genres = doc.select("li.genres a, tr:has(td:contains(Genres)) td.table-value a")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }

            val chapters = parseChapters(doc, absolute)

            val manga = Manga(
                id = mangaIdFromUrl(absolute),
                sourceId = ID,
                url = absolute,
                title = title,
                coverUrl = cover,
                latestChapter = chapters.firstOrNull()?.name,
                status = parseStatus(statusText),
                contentType = inferContentType(genres, title),
                genres = genres
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
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Manganato details", it) }
        )
    }

    override suspend fun getChapters(mangaUrl: String): MangaResult<List<MangaChapter>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)
            parseChapters(doc, absolute)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Manganato chapters", it) }
        )
    }

    override suspend fun getChapterPages(chapterUrl: String): MangaResult<List<MangaPage>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(chapterUrl)
            val doc = fetchDocument(absolute)
            val images = doc.select(".container-chapter-reader img")
                .map { it.imgAttr() }
                .filter { isPageImageUrl(it) }

            if (images.isEmpty()) {
                error("No reader images found for $absolute")
            }

            images.mapIndexed { index, url ->
                MangaPage(index = index, imageUrl = url)
            }
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Manganato pages", it) }
        )
    }

    private suspend fun fetchMangaList(url: String): MangaResult<MangaPagedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(url)
            val items = doc.select(".list-comic-item-wrap, .list-story-item-wrap, .content-genres-item, .itemupdate, .owl-carousel .item, .search-story-item")
                .mapNotNull { elementToManga(it) }
                .distinctBy { it.id }

            val hasNext = doc.selectFirst(".page-blue.page-last, a:contains(NEXT), a.page-next, a:contains(>>)") != null ||
                items.size >= 12

            MangaPagedResult(items, hasNext)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Manganato manga list", it) }
        )
    }

    private fun elementToManga(element: Element): Manga? {
        val link = element.selectFirst("h3 a, a.list-story-item, a.cover, .genres-item-name, a.item-title, a[href*=/manga/]")
            ?: return null
        val href = absolutize(link.attr("abs:href").ifBlank { link.attr("href") })
        if (!href.contains("/manga/") || href.contains("/chapter-")) return null

        val title = element.selectFirst("h3 a")?.text()?.trim()
            ?: link.attr("title").trim().takeIf { it.isNotEmpty() }
            ?: link.text().trim().takeIf { it.isNotEmpty() }
            ?: return null

        val cover = element.selectFirst("img")?.imgAttr()
        val latest = element.selectFirst(".list-story-item-wrap-chapter, a[href*=/chapter-]")?.text()?.trim()

        return Manga(
            id = mangaIdFromUrl(href),
            sourceId = ID,
            url = href,
            title = title,
            coverUrl = cover,
            latestChapter = latest,
            contentType = MangaContentType.MANGA
        )
    }

    private fun parseChaptersFromJson(slug: String): List<MangaChapter>? {
        return runCatching {
            val apiUrl = "$BASE_URL/api/manga/$slug/chapters"
            val request = Request.Builder()
                .url(apiUrl)
                .get()
                .header("User-Agent", MangaHttpClient.USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$BASE_URL/manga/$slug")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                val root = JsonParser.parseString(body).asJsonObject
                if (!root.has("success") || !root.get("success").asBoolean) return@runCatching null
                val data = root.getAsJsonObject("data") ?: return@runCatching null
                val chapterArray = data.getAsJsonArray("chapters") ?: return@runCatching null

                val list = mutableListOf<MangaChapter>()
                for (el in chapterArray) {
                    val obj = el.asJsonObject
                    val chName = obj.get("chapter_name")?.asString.orEmpty()
                    val chSlug = obj.get("chapter_slug")?.asString.orEmpty()
                    val chNum = if (obj.has("chapter_num") && !obj.get("chapter_num").isJsonNull) {
                        obj.get("chapter_num").asFloat
                    } else null
                    val updatedAt = runCatching {
                        java.time.Instant.parse(obj.get("updated_at")?.asString).toEpochMilli()
                    }.getOrNull()
                    val chUrl = "$BASE_URL/manga/$slug/$chSlug"

                    list.add(
                        MangaChapter(
                            id = "$ID:$slug/$chSlug",
                            mangaId = "$ID:$slug",
                            sourceId = ID,
                            url = chUrl,
                            name = chName,
                            number = chNum,
                            uploadedAt = updatedAt
                        )
                    )
                }
                list
            }
        }.getOrNull()
    }

    private fun parseChapters(doc: Document, mangaUrl: String): List<MangaChapter> {
        val slug = mangaUrl.substringAfter("/manga/").substringBefore('/').substringBefore('?')
        val fromApi = parseChaptersFromJson(slug)
        if (!fromApi.isNullOrEmpty()) return fromApi

        val mangaId = "$ID:$slug"
        return doc.select(".row-content-chapter li a, .chapter-name, a[href*=/manga/$slug/chapter-]").mapNotNull { anchor ->
            val href = absolutize(anchor.attr("abs:href").ifBlank { anchor.attr("href") })
            val name = anchor.text().trim()
            if (name.isBlank()) return@mapNotNull null

            MangaChapter(
                id = chapterIdFromUrl(href),
                mangaId = mangaId,
                sourceId = ID,
                url = href,
                name = name,
                number = parseChapterNumber(name)
            )
        }.distinctBy { it.url }
    }

    private fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Referer", BASE_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            val body = response.body?.string().orEmpty()
            return Jsoup.parse(body, url)
        }
    }

    private fun Element.imgAttr(): String {
        val dataSrc = (attr("abs:data-src").ifBlank { attr("data-src") }).trim()
        val lazySrc = (attr("abs:data-lazy-src").ifBlank { attr("data-lazy-src") }).trim()
        val src = (attr("abs:src").ifBlank { attr("src") }).trim()
        val raw = when {
            dataSrc.isNotEmpty() && !dataSrc.contains("placeholder") && !dataSrc.contains("loading") -> dataSrc
            lazySrc.isNotEmpty() && !lazySrc.contains("placeholder") && !lazySrc.contains("loading") -> lazySrc
            else -> src
        }
        return when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "$BASE_URL$raw"
            else -> raw
        }
    }

    private fun absolutize(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("http") -> trimmed.trimEnd('/')
            trimmed.startsWith("//") -> "https:${trimmed.trimEnd('/')}"
            trimmed.startsWith("/") -> "$BASE_URL${trimmed.trimEnd('/')}"
            else -> "$BASE_URL/${trimmed.trimEnd('/')}"
        }
    }

    private fun mangaIdFromUrl(url: String): String {
        val slug = url.substringAfter("/manga/").substringBefore('/').substringBefore('?')
        return "$ID:$slug"
    }

    private fun chapterIdFromUrl(url: String): String {
        val path = url.substringAfter(BASE_URL).trim('/')
        return "$ID:$path"
    }

    private fun parseChapterNumber(name: String): Float? {
        val match = Regex("""(?i)chapter\s*([0-9]+(?:\.[0-9]+)?)""").find(name)
        return match?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }

    private fun parseStatus(text: String?): MangaPublicationStatus = when (text?.lowercase(Locale.US)?.trim()) {
        "ongoing" -> MangaPublicationStatus.ONGOING
        "completed" -> MangaPublicationStatus.COMPLETED
        else -> MangaPublicationStatus.UNKNOWN
    }

    private fun inferContentType(genres: List<String>, title: String): MangaContentType {
        val blob = (genres + title).joinToString(" ").lowercase(Locale.US)
        return when {
            "manhwa" in blob -> MangaContentType.MANHWA
            "manhua" in blob -> MangaContentType.MANHUA
            else -> MangaContentType.MANGA
        }
    }

    private fun isPageImageUrl(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase(Locale.US)
        if (clean.contains("loading") || clean.contains("logo") || clean.contains("avatar") || clean.contains("banner")) return false
        return clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") ||
            clean.endsWith(".webp") || clean.contains("2xstorage") || clean.contains("chapter")
    }

    companion object {
        const val ID = "manganato"
        const val BASE_URL = "https://www.manganato.gg"

        val GENRES = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Historical",
            "Horror", "Isekai", "Martial Arts", "Mystery", "Romance", "Sci-Fi",
            "Shounen", "Slice of Life", "Sports", "Supernatural", "Tragedy"
        )
    }
}
