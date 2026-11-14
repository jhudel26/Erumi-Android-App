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
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Public-page adapter for https://manhuaplus.org/ (Liliana theme).
 * Uses the same publicly reachable HTML and XHR endpoints the website itself uses.
 * Does not bypass authentication, CAPTCHA, DRM, or paywalls.
 */
class ManhuaPlusSource(
    private val client: OkHttpClient = MangaHttpClient.client,
    private val gson: Gson = Gson()
) : MangaSource {

    override val info = MangaSourceInfo(
        id = ID,
        name = "ManhuaPlus",
        baseUrl = BASE_URL,
        supportsDownloads = true
    )

    private val chapterIdCache = ConcurrentHashMap<String, String>()

    override fun supportedGenres(): List<String> = GENRES

    override suspend fun getPopular(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList("$BASE_URL/ranking/week/$page")

    override suspend fun getLatest(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList("$BASE_URL/all-manga/$page/?sort=last_update&status=0")

    override suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult> {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("keyword", query.trim())
            .addPathSegment(page.toString())
            .addPathSegment("")
            .build()
            .toString()
        return fetchMangaList(url)
    }

    override suspend fun getByGenre(genre: String, page: Int): MangaResult<MangaPagedResult> {
        val slug = genre.trim().lowercase(Locale.US).replace(' ', '-')
        return fetchMangaList("$BASE_URL/genres/$slug/$page")
    }

    override suspend fun browse(
        page: Int,
        sort: MangaSortOption,
        genre: String?,
        status: String?
    ): MangaResult<MangaPagedResult> {
        if (!genre.isNullOrBlank()) return getByGenre(genre, page)
        return when (sort) {
            MangaSortOption.POPULARITY, MangaSortOption.RATING -> getPopular(page)
            MangaSortOption.ALPHABETICAL -> fetchMangaList("$BASE_URL/all-manga/$page/?sort=name&status=0")
            MangaSortOption.RECENTLY_ADDED -> fetchMangaList("$BASE_URL/all-manga/$page/?sort=new&status=0")
            MangaSortOption.LATEST_UPDATES -> getLatest(page)
        }
    }

    override suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)
            val title = doc.selectFirst(".a2 header h1")?.text()?.trim()
                ?: doc.selectFirst("h1")?.text()?.trim()
                ?: error("Title unavailable")
            val cover = doc.selectFirst(".a1 > figure img")?.imgAttr()
                ?: doc.selectFirst("meta[property=og:image]")?.attr("abs:content")
            val description = doc.selectFirst("div#syn-target")?.text()?.trim()
            val author = doc.selectFirst("div.y6x11p i.fas.fa-user + span.dt")?.text()
                ?.takeUnless { it.equals("updating", true) }
            val statusText = doc.selectFirst("div.y6x11p i.fas.fa-rss + span.dt")?.text()
            val genres = doc.select(".a2 div > a[rel=tag].label").map { it.text().trim() }.filter { it.isNotEmpty() }
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
            onFailure = { MangaResult.Error(it.message ?: "Failed to load manga details", it) }
        )
    }

    override suspend fun getChapters(mangaUrl: String): MangaResult<List<MangaChapter>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)
            parseChapters(doc, absolute)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load chapters", it) }
        )
    }

    override suspend fun getChapterPages(chapterUrl: String): MangaResult<List<MangaPage>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(chapterUrl)
            val doc = fetchDocument(absolute)
            val script = doc.selectFirst("script:containsData(const CHAPTER_ID)")?.data()
                ?: error("Chapter id not found on page")
            val chapterId = script.substringAfter("const CHAPTER_ID = ").substringBefore(";").trim()
            if (chapterId.isBlank()) error("Chapter id missing")
            chapterIdCache[absolute] = chapterId

            val request = Request.Builder()
                .url("$BASE_URL/ajax/image/list/chap/$chapterId")
                .get()
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", absolute)
                .header("Host", BASE_HOST)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Chapter images unavailable (HTTP ${response.code}). The source may be temporarily blocking or failing this endpoint.")
                }
                val body = response.body?.string().orEmpty()
                val json = gson.fromJson(body, JsonObject::class.java)
                val status = json.get("status")?.asBoolean == true
                if (!status) {
                    error(json.get("msg")?.asString ?: "Source returned an unsuccessful image list")
                }
                val html = json.get("html")?.asString.orEmpty()
                parsePages(html, absolute)
            }
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load chapter pages", it) }
        )
    }

    private suspend fun fetchMangaList(url: String): MangaResult<MangaPagedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(url)
            val items = doc.select(LIST_SELECTOR).mapNotNull { elementToManga(it) }
            val hasNext = doc.selectFirst(NEXT_PAGE_SELECTOR) != null
            MangaPagedResult(items, hasNext)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load manga list", it) }
        )
    }

    private fun elementToManga(element: Element): Manga? {
        val link = element.selectFirst(".text-center a") ?: return null
        val title = link.text().trim()
        if (title.isBlank()) return null
        val href = link.attr("abs:href").ifBlank { link.attr("href") }
        val absolute = absolutize(href)
        val cover = element.selectFirst("img")?.imgAttr()
        val latest = element.selectFirst("a[href*=/chapter-]")?.text()?.trim()
        val ratingText = element.selectFirst(".numscore, .score, .rating")?.text()
            ?.replace(Regex("[^0-9.]"), "")
        return Manga(
            id = mangaIdFromUrl(absolute),
            sourceId = ID,
            url = absolute,
            title = title,
            coverUrl = cover,
            latestChapter = latest,
            rating = ratingText?.toDoubleOrNull(),
            contentType = MangaContentType.UNKNOWN
        )
    }

    private fun parseChapters(doc: Document, mangaUrl: String): List<MangaChapter> {
        val mangaId = mangaIdFromUrl(mangaUrl)
        return doc.select(CHAPTER_SELECTOR).mapNotNull { li ->
            val anchor = li.selectFirst("a") ?: return@mapNotNull null
            val name = anchor.text().trim()
            if (name.isBlank()) return@mapNotNull null
            val href = absolutize(anchor.attr("abs:href").ifBlank { anchor.attr("href") })
            val uploadedAt = li.selectFirst("time[datetime]")?.attr("datetime")
                ?.toLongOrNull()?.times(1000L)
            MangaChapter(
                id = chapterIdFromUrl(href),
                mangaId = mangaId,
                sourceId = ID,
                url = href,
                name = name,
                number = parseChapterNumber(name),
                uploadedAt = uploadedAt
            )
        }.distinctBy { it.url }
    }

    private fun parsePages(html: String, chapterUrl: String): List<MangaPage> {
        val fragment = Jsoup.parseBodyFragment(html, chapterUrl)
        val separators = fragment.select("div.separator")
        val indexed = separators.any { it.hasAttr("data-index") }
        val urls = if (indexed) {
            separators.mapNotNull { sep ->
                val href = sep.selectFirst("a")?.attr("abs:href") ?: return@mapNotNull null
                if (!isPageImageUrl(href)) return@mapNotNull null
                val index = sep.attr("data-index").toIntOrNull() ?: return@mapNotNull null
                index to href
            }.sortedBy { it.first }.map { it.second }
        } else {
            separators.mapNotNull { sep ->
                val href = sep.selectFirst("a")?.attr("abs:href") ?: return@mapNotNull null
                href.takeIf { isPageImageUrl(it) }
            }
        }
        return urls.mapIndexed { index, url -> MangaPage(index, url) }
    }

    private fun fetchDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", BASE_URL)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            val body = response.body?.string().orEmpty()
            return Jsoup.parse(body, url)
        }
    }

    private fun Element.imgAttr(): String {
        val raw = when {
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("data-src") -> attr("abs:data-src")
            else -> attr("abs:src")
        }
        return if (raw.startsWith("//")) "https:$raw" else raw
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
        val path = url.toHttpUrlOrNull()?.encodedPath ?: url
        val slug = path.trim('/').substringAfter("manga/").substringBefore('/')
        return "$ID:$slug"
    }

    private fun chapterIdFromUrl(url: String): String {
        val path = url.toHttpUrlOrNull()?.encodedPath ?: url
        return "$ID:${path.trim('/')}"
    }

    private fun String.toHttpUrlOrNull() = runCatching { toHttpUrl() }.getOrNull()

    private fun parseChapterNumber(name: String): Float? {
        val match = Regex("""(?i)chapter\s*([0-9]+(?:\.[0-9]+)?)""").find(name)
        return match?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }

    private fun parseStatus(text: String?): MangaPublicationStatus {
        return when (text?.lowercase(Locale.US)?.trim()) {
            "ongoing" -> MangaPublicationStatus.ONGOING
            "completed" -> MangaPublicationStatus.COMPLETED
            "on-hold", "on hold" -> MangaPublicationStatus.ON_HOLD
            "canceled", "cancelled" -> MangaPublicationStatus.CANCELLED
            else -> MangaPublicationStatus.UNKNOWN
        }
    }

    private fun inferContentType(genres: List<String>, title: String): MangaContentType {
        val blob = (genres + title).joinToString(" ").lowercase(Locale.US)
        return when {
            "manhwa" in blob -> MangaContentType.MANHWA
            "manhua" in blob -> MangaContentType.MANHUA
            "manga" in blob -> MangaContentType.MANGA
            "long strip" in blob || "webtoon" in blob -> MangaContentType.MANHWA
            else -> MangaContentType.MANHUA
        }
    }

    private fun isPageImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        val path = lower.substringBefore('?').substringBefore('#')
        return !path.endsWith(".svg") && !lower.contains("loading_comments") && !lower.contains("loading.gif")
    }

    companion object {
        const val ID = "manhuaplus"
        const val BASE_URL = "https://manhuaplus.org"
        private const val BASE_HOST = "manhuaplus.org"
        private const val LIST_SELECTOR = "div#main div.grid > div"
        private const val NEXT_PAGE_SELECTOR = ".blog-pager > span.pagecurrent + span, .blog-pager span:has(a)"
        private const val CHAPTER_SELECTOR = "ul > li.chapter, li.chapter"

        val GENRES = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Martial Arts",
            "Romance", "School Life", "Sci-Fi", "Slice of Life", "Supernatural",
            "Thriller", "Historical", "Mystery", "Horror", "Sports", "Isekai",
            "Harem", "Magic", "Full Color", "Long Strip"
        )
    }
}
