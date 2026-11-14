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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Public HTML adapter for https://asurascans.com/.
 * Specializes in manhwa and webtoons.
 */
class AsuraScansSource(
    private val client: OkHttpClient = MangaHttpClient.client
) : MangaSource {

    override val info = MangaSourceInfo(
        id = ID,
        name = "Asura Scans",
        baseUrl = BASE_URL,
        supportsDownloads = true
    )

    override fun supportedGenres(): List<String> = GENRES

    override suspend fun getPopular(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList("$BASE_URL/browse/comics?order=popular&page=$page")

    override suspend fun getLatest(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList("$BASE_URL/browse/comics?order=update&page=$page")

    override suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getPopular(page)
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("browse")
            .addPathSegment("comics")
            .addQueryParameter("search", trimmed)
            .addQueryParameter("page", page.toString())
            .build()
            .toString()
        return fetchMangaList(url)
    }

    override suspend fun getByGenre(genre: String, page: Int): MangaResult<MangaPagedResult> {
        val slug = genre.trim().lowercase(Locale.US).replace(' ', '-')
        val url = "$BASE_URL/browse/comics?genre=$slug&page=$page"
        return fetchMangaList(url)
    }

    override suspend fun browse(
        page: Int,
        sort: MangaSortOption,
        genre: String?,
        status: String?
    ): MangaResult<MangaPagedResult> {
        if (!genre.isNullOrBlank()) return getByGenre(genre, page)
        val order = when (sort) {
            MangaSortOption.POPULARITY, MangaSortOption.RATING -> "popular"
            MangaSortOption.LATEST_UPDATES -> "update"
            MangaSortOption.RECENTLY_ADDED -> "latest"
            MangaSortOption.ALPHABETICAL -> "title"
        }
        return fetchMangaList("$BASE_URL/browse/comics?order=$order&page=$page")
    }

    override suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)

            val title = doc.selectFirst("h1")?.text()?.trim()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                ?: error("Title unavailable for $absolute")

            val cover = doc.selectFirst("img[src*=/covers/]")?.imgAttr()
                ?: doc.selectFirst("meta[property=og:image]")?.attr("abs:content")

            val description = doc.selectFirst("div.description, span.font-medium.text-sm, p.text-sm")?.text()?.trim()
                ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()

            val author = doc.select("span:contains(Author) + span, div:contains(Author) span").text().trim().takeIf { it.isNotEmpty() }
            val statusText = doc.select("span:contains(Status) + span, div:contains(Status) span").text().trim().takeIf { it.isNotEmpty() }
            val genres = doc.select("a[href*=/genre/], button:has(span)").map { it.text().trim() }.filter { it.isNotEmpty() }

            val chapters = parseChapters(doc, absolute)

            val manga = Manga(
                id = mangaIdFromUrl(absolute),
                sourceId = ID,
                url = absolute,
                title = title,
                coverUrl = cover,
                latestChapter = chapters.firstOrNull()?.name,
                status = parseStatus(statusText),
                contentType = MangaContentType.MANHWA,
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
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Asura Scans details", it) }
        )
    }

    override suspend fun getChapters(mangaUrl: String): MangaResult<List<MangaChapter>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)
            parseChapters(doc, absolute)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Asura Scans chapters", it) }
        )
    }

    override suspend fun getChapterPages(chapterUrl: String): MangaResult<List<MangaPage>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(chapterUrl)
            val (doc, body) = fetchDocumentWithRaw(absolute)

            // 1. Try parsing Astro ChapterReader props in HTML
            val pageRegex = Regex(""""url":\[0,"(https://[^"]+)"]""")
            val matchedPages = pageRegex.findAll(body).mapIndexed { index, m ->
                MangaPage(index = index, imageUrl = m.groupValues[1])
            }.toList()

            if (matchedPages.isNotEmpty()) {
                return@runCatching matchedPages
            }

            // 2. Fallback to img elements
            val images = doc.select("img[src*=/chapters/], img[src*=/asura-images/chapters/], .w-full img")
                .map { it.imgAttr() }
                .filter { isPageImageUrl(it) }
                .distinct()

            if (images.isEmpty()) {
                error("No chapter pages found for $absolute")
            }

            images.mapIndexed { index, url ->
                MangaPage(index = index, imageUrl = url)
            }
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Asura Scans pages", it) }
        )
    }

    private suspend fun fetchMangaList(url: String): MangaResult<MangaPagedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(url)
            val items = doc.select(".series-card, div.group:has(a[href*=/comics/]), a[href*=/comics/]")
                .mapNotNull { elementToManga(it) }
                .distinctBy { it.id }

            val hasNext = doc.selectFirst("a[href*='page=']:contains(Next), button:contains(Next)") != null ||
                items.size >= 12

            MangaPagedResult(items, hasNext)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Asura Scans manga list", it) }
        )
    }

    private fun elementToManga(element: Element): Manga? {
        val anchor = if (element.tagName() == "a") element else element.selectFirst("a[href*=/comics/]") ?: return null
        val href = anchor.attr("abs:href").ifBlank { anchor.attr("href") }
        if (!href.contains("/comics/") || href.contains("/chapter/")) return null
        val absolute = absolutize(href)

        val title = element.selectFirst("h3, h2, span.font-bold")?.text()?.trim()
            ?: anchor.selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null

        val cover = element.selectFirst("img")?.imgAttr()

        return Manga(
            id = mangaIdFromUrl(absolute),
            sourceId = ID,
            url = absolute,
            title = title,
            coverUrl = cover,
            contentType = MangaContentType.MANHWA
        )
    }

    private fun parseChapters(doc: Document, mangaUrl: String): List<MangaChapter> {
        val mangaId = mangaIdFromUrl(mangaUrl)
        return doc.select("a[href*=/comics/][href*=/chapter/]").mapNotNull { anchor ->
            val href = absolutize(anchor.attr("abs:href").ifBlank { anchor.attr("href") })
            val rawName = anchor.text().trim()
            if (rawName.isBlank()) return@mapNotNull null

            val chapterNum = parseChapterNumber(href) ?: parseChapterNumber(rawName)
            val displayName = if (chapterNum != null && !rawName.startsWith("Chapter", ignoreCase = true)) {
                "Chapter $chapterNum - $rawName"
            } else {
                rawName
            }

            MangaChapter(
                id = chapterIdFromUrl(href),
                mangaId = mangaId,
                sourceId = ID,
                url = href,
                name = displayName,
                number = chapterNum
            )
        }.distinctBy { it.url }
    }

    private fun fetchDocumentWithRaw(url: String): Pair<Document, String> {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", MangaHttpClient.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", BASE_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            val body = response.body?.string().orEmpty()
            return Jsoup.parse(body, url) to body
        }
    }

    private fun fetchDocument(url: String): Document = fetchDocumentWithRaw(url).first

    private fun Element.imgAttr(): String {
        val raw = when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
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
        val slug = url.substringAfter("/comics/").substringBefore('/').substringBefore('?')
        return "$ID:$slug"
    }

    private fun chapterIdFromUrl(url: String): String {
        val path = url.substringAfter(BASE_URL).trim('/')
        return "$ID:$path"
    }

    private fun parseChapterNumber(text: String): Float? {
        val match = Regex("""(?i)(?:chapter|ch)[-/\s]*([0-9]+(?:\.[0-9]+)?)""").find(text)
        return match?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }

    private fun parseStatus(text: String?): MangaPublicationStatus {
        return when (text?.lowercase(Locale.US)?.trim()) {
            "ongoing" -> MangaPublicationStatus.ONGOING
            "completed" -> MangaPublicationStatus.COMPLETED
            "hiatus", "on hold" -> MangaPublicationStatus.ON_HOLD
            "dropped", "cancelled" -> MangaPublicationStatus.CANCELLED
            else -> MangaPublicationStatus.UNKNOWN
        }
    }

    private fun isPageImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (lower.contains("logo") || lower.contains("banner") || lower.contains("avatar") || lower.contains("cover")) return false
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.contains("/chapters/")
    }

    companion object {
        const val ID = "asurascans"
        const val BASE_URL = "https://asurascans.com"

        val GENRES = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Historical",
            "Martial Arts", "Mystery", "Romance", "Sci-Fi", "Slice of Life",
            "Supernatural", "Thriller", "Isekai", "Returner", "System", "Dungeon"
        )
    }
}
