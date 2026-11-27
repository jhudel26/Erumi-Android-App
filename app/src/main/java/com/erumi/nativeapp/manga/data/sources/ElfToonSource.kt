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
import java.net.URLDecoder
import java.util.Locale

/**
 * Public HTML/Next.js SSR adapter for https://elftoon.net/.
 */
class ElfToonSource(
    private val client: OkHttpClient = MangaHttpClient.client
) : MangaSource {

    override val info = MangaSourceInfo(
        id = ID,
        name = "Elf Toon",
        baseUrl = BASE_URL,
        supportsDownloads = true
    )

    override fun supportedGenres(): List<String> = GENRES

    override suspend fun getPopular(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList(buildBrowseUrl(page = page, sort = "popular"))

    override suspend fun getLatest(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList(buildBrowseUrl(page = page, sort = "latest"))

    override suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getPopular(page)
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addQueryParameter("q", trimmed)
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .build()
            .toString()
        return fetchMangaList(url)
    }

    override suspend fun getByGenre(genre: String, page: Int): MangaResult<MangaPagedResult> {
        val slug = genre.trim().lowercase(Locale.US).replace(' ', '-')
        val url = buildBrowseUrl(page = page, sort = "popular", genre = slug)
        return fetchMangaList(url)
    }

    override suspend fun browse(
        page: Int,
        sort: MangaSortOption,
        genre: String?,
        status: String?
    ): MangaResult<MangaPagedResult> {
        val sortParam = when (sort) {
            MangaSortOption.POPULARITY -> "popular"
            MangaSortOption.LATEST_UPDATES -> "latest"
            MangaSortOption.RECENTLY_ADDED -> "newest"
            MangaSortOption.ALPHABETICAL -> "title"
            MangaSortOption.RATING -> "rating"
        }
        val genreSlug = genre?.trim()?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.US)?.replace(' ', '-')
        val statusParam = status?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.US)
        return fetchMangaList(buildBrowseUrl(page, sortParam, genreSlug, statusParam))
    }

    override suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)

            val title = doc.selectFirst("h1:not(.noscript-title)")?.text()?.trim()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                    ?.substringBefore(" | Elf Toon")?.trim()
                ?: error("Title unavailable for $absolute")

            val slug = absolute.substringAfter("/series/comic/").substringBefore('/').substringBefore('?')
            val coverCandidates = doc.select("img")
                .asSequence()
                .map { it.imgAttr() }
                .filter { it.contains("/uploads/series/") && !it.contains("logo") }
                .toList()
            val cover = coverCandidates.firstOrNull { slug.isNotBlank() && it.contains("/$slug/") }
                ?: coverCandidates.firstOrNull()
                ?: doc.selectFirst("meta[property=og:image]")?.attr("abs:content")
                    ?.let { decodeNextImageUrl(it) }

            val description = doc.selectFirst("div[role=tabpanel] p, p.text-muted-foreground, div.prose")?.text()?.trim()
                ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()

            val headerMeta = doc.body().text()
            val status = parseStatus(headerMeta)
            val contentType = parseContentType(headerMeta)

            val genres = doc.select("a[href*=/series?genre=], a[href*=/genre/]")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val chapters = parseChapters(doc, absolute)

            val manga = Manga(
                id = mangaIdFromUrl(absolute),
                sourceId = ID,
                url = absolute,
                title = title,
                coverUrl = cover,
                latestChapter = chapters.firstOrNull()?.name,
                status = status,
                contentType = contentType,
                genres = genres
            )

            MangaDetails(
                manga = manga,
                description = description,
                chapterCount = chapters.size,
                updatedAt = chapters.firstOrNull()?.uploadedAt
            )
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Elf Toon details", it) }
        )
    }

    override suspend fun getChapters(mangaUrl: String): MangaResult<List<MangaChapter>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)
            parseChapters(doc, absolute)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Elf Toon chapters", it) }
        )
    }

    override suspend fun getChapterPages(chapterUrl: String): MangaResult<List<MangaPage>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(chapterUrl)
            val (doc, body) = fetchDocumentWithRaw(absolute)

            // 1. Extract all /uploads/series/.../p<num>-... page image paths from HTML + Next.js RSC payload
            val pageRegex = Regex(
                """(/uploads/series/[^"'\\<>\s]+/p[0-9]*-[^"'\\<>\s]+\.(?:webp|jpg|jpeg|png|avif))""",
                RegexOption.IGNORE_CASE
            )
            val rscPages = pageRegex.findAll(body)
                .map { absolutize(it.groupValues[1]) }
                .distinct()
                .toList()

            if (rscPages.isNotEmpty()) {
                return@runCatching rscPages.mapIndexed { index, url ->
                    MangaPage(index = index, imageUrl = url)
                }
            }

            // 2. Fallback to rendered <img> elements
            val images = doc.select("img[src*=/uploads/series/]")
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
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Elf Toon pages", it) }
        )
    }

    private suspend fun fetchMangaList(url: String): MangaResult<MangaPagedResult> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = fetchDocument(url)
            val items = doc.select("[data-card-set-item], div[data-series-id], a[href*=/series/comic/]")
                .mapNotNull { elementToManga(it) }
                .distinctBy { it.id }

            val hasNext = doc.selectFirst("a[href*='page=']:contains(Next), button:contains(Next):not([disabled])") != null ||
                items.size >= 20

            MangaPagedResult(items, hasNext)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { MangaResult.Error(it.message ?: "Failed to load Elf Toon manga list", it) }
        )
    }

    private fun elementToManga(element: Element): Manga? {
        val anchor = if (element.tagName() == "a") element else element.selectFirst("a[href*=/series/comic/]") ?: return null
        val rawHref = anchor.attr("href").ifBlank { anchor.attr("abs:href") }
        if (!rawHref.contains("/series/comic/") || rawHref.contains("/chapter/")) return null

        val cleanUrl = absolutize(rawHref.substringBefore('?'))
        val img = element.selectFirst("img") ?: anchor.selectFirst("img")
        val title = element.selectFirst("h3, h2, [data-series-title]")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: img?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() }
            ?: anchor.text().trim().takeIf { it.isNotEmpty() }
            ?: return null

        val cover = img?.imgAttr()
        val latestChapter = element.selectFirst("a[href*=/chapter/]")?.text()?.trim()?.takeIf { it.isNotEmpty() }

        return Manga(
            id = mangaIdFromUrl(cleanUrl),
            sourceId = ID,
            url = cleanUrl,
            title = title,
            coverUrl = cover,
            latestChapter = latestChapter,
            contentType = MangaContentType.MANHUA
        )
    }

    private fun parseChapters(doc: Document, mangaUrl: String): List<MangaChapter> {
        val mangaId = mangaIdFromUrl(mangaUrl)
        return doc.select("a[href*=/series/comic/][href*=/chapter/]").mapNotNull { anchor ->
            val rawHref = anchor.attr("href").ifBlank { anchor.attr("abs:href") }
            val cleanHref = absolutize(rawHref.substringBefore('?'))
            val rawName = anchor.text().trim()
            if (rawName.equals("Start Reading", ignoreCase = true) || rawName.isBlank()) {
                return@mapNotNull null
            }

            val chapterNum = parseChapterNumber(cleanHref) ?: parseChapterNumber(rawName)
            val displayName = if (chapterNum != null && !rawName.contains("chapter", ignoreCase = true)) {
                val formattedNum = if (chapterNum % 1f == 0f) chapterNum.toInt().toString() else chapterNum.toString()
                "Chapter $formattedNum"
            } else {
                rawName
            }

            MangaChapter(
                id = chapterIdFromUrl(cleanHref),
                mangaId = mangaId,
                sourceId = ID,
                url = cleanHref,
                name = displayName,
                number = chapterNum
            )
        }.distinctBy { it.url }
            .sortedByDescending { it.number ?: Float.MIN_VALUE }
    }

    private fun buildBrowseUrl(
        page: Int,
        sort: String = "popular",
        genre: String? = null,
        status: String? = null
    ): String {
        val builder = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
        if (!genre.isNullOrBlank()) builder.addQueryParameter("genre", genre)
        if (!status.isNullOrBlank()) builder.addQueryParameter("status", status)
        return builder.build().toString()
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
            hasAttr("data-src") -> attr("data-src")
            hasAttr("data-lazy-src") -> attr("data-lazy-src")
            hasAttr("src") -> attr("src")
            hasAttr("srcset") -> attr("srcset").split(",").lastOrNull()?.trim()?.substringBefore(" ").orEmpty()
            else -> ""
        }
        return decodeNextImageUrl(absolutize(raw))
    }

    private fun decodeNextImageUrl(url: String): String {
        if (!url.contains("/_next/image")) return url
        val encoded = url.substringAfter("url=", "").substringBefore('&')
        if (encoded.isBlank()) return url
        val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
        return absolutize(decoded)
    }

    private fun absolutize(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("http") -> trimmed.trimEnd('/')
            trimmed.startsWith("//") -> "https:${trimmed.trimEnd('/')}"
            trimmed.startsWith("/") -> "$BASE_URL${trimmed.trimEnd('/')}"
            else -> "$BASE_URL/${trimmed.trimEnd('/')}"
        }
    }

    private fun mangaIdFromUrl(url: String): String {
        val slug = url.substringAfter("/series/comic/").substringBefore('/').substringBefore('?')
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

    private fun parseStatus(text: String): MangaPublicationStatus {
        val lower = text.lowercase(Locale.US)
        return when {
            "ongoing" in lower -> MangaPublicationStatus.ONGOING
            "completed" in lower -> MangaPublicationStatus.COMPLETED
            "hiatus" in lower || "on hold" in lower -> MangaPublicationStatus.ON_HOLD
            "dropped" in lower || "cancelled" in lower -> MangaPublicationStatus.CANCELLED
            else -> MangaPublicationStatus.UNKNOWN
        }
    }

    private fun parseContentType(text: String): MangaContentType {
        val lower = text.lowercase(Locale.US)
        return when {
            "manhwa" in lower -> MangaContentType.MANHWA
            "manhua" in lower -> MangaContentType.MANHUA
            "manga" in lower -> MangaContentType.MANGA
            else -> MangaContentType.MANHUA
        }
    }

    private fun isPageImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (lower.contains("logo") || lower.contains("cover-")) return false
        return lower.contains("/uploads/series/") &&
            (lower.endsWith(".webp") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".avif"))
    }

    companion object {
        const val ID = "elftoon"
        const val BASE_URL = "https://elftoon.net"

        val GENRES = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Harem",
            "Historical", "Martial Arts", "Mystery", "Romance", "Sci-Fi",
            "Slice of Life", "Supernatural", "System", "Isekai"
        )
    }
}
