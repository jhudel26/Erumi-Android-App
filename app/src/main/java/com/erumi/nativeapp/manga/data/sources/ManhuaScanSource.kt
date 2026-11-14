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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Public HTML adapter for ManhuaScan (https://manhuascan.us/).
 * Specialized in Chinese manhua comics, cultivation, martial arts, and colored webtoons.
 */
class ManhuaScanSource(
    private val client: OkHttpClient = MangaHttpClient.client
) : MangaSource {

    private val fallback by lazy { ManhuaPlusSource(client) }

    override val info = MangaSourceInfo(
        id = ID,
        name = "ManhuaScan",
        baseUrl = BASE_URL,
        supportsDownloads = true
    )

    override fun supportedGenres(): List<String> = GENRES

    override suspend fun getPopular(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList("$BASE_URL/popular?page=$page")

    override suspend fun getLatest(page: Int): MangaResult<MangaPagedResult> =
        fetchMangaList("$BASE_URL/latest?page=$page")

    override suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getPopular(page)
        val url = "$BASE_URL/search?q=$trimmed&page=$page"
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
            MangaSortOption.POPULARITY, MangaSortOption.RATING -> "popular"
            MangaSortOption.LATEST_UPDATES -> "latest"
            MangaSortOption.RECENTLY_ADDED -> "new"
            MangaSortOption.ALPHABETICAL -> "alphabet"
        }
        return fetchMangaList("$BASE_URL/$path?page=$page")
    }

    override suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)

            val title = doc.selectFirst("h1, .detail-title, .book-title")?.text()?.trim()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                ?: error("Title unavailable for $absolute")

            val cover = doc.selectFirst(".book-cover img, .detail-thumb img, .summary_image img, .thumb img")?.imgAttr()
                ?: doc.selectFirst("meta[property=og:image]")?.attr("abs:content")

            val description = doc.selectFirst(".description, .summary__content, #syn-target, .synopsis, p.content")
                ?.text()?.trim()

            val author = doc.select(".author-content a, span:contains(Author) + span, .author a")
                .firstOrNull()?.text()?.trim()

            val statusText = doc.select(".status-content, span:contains(Status) + span")
                .firstOrNull()?.text()?.trim()

            val genres = doc.select(".genres-content a, a[href*=/genre/], .genres a")
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
                contentType = MangaContentType.MANHUA,
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
            onFailure = { fallback.getDetails(mangaUrl) }
        )
    }

    override suspend fun getChapters(mangaUrl: String): MangaResult<List<MangaChapter>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(mangaUrl)
            val doc = fetchDocument(absolute)
            parseChapters(doc, absolute)
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { fallback.getChapters(mangaUrl) }
        )
    }

    override suspend fun getChapterPages(chapterUrl: String): MangaResult<List<MangaPage>> = withContext(Dispatchers.IO) {
        runCatching {
            val absolute = absolutize(chapterUrl)
            val doc = fetchDocument(absolute)
            val images = doc.select(".reading-content img, #chapter-images img, .container-chapter-reader img, .chapter-image img, div.separator img")
                .map { it.imgAttr() }
                .filter { isPageImageUrl(it) }

            if (images.isEmpty()) {
                error("No chapter images found for $absolute")
            }

            images.mapIndexed { index, url ->
                MangaPage(index = index, imageUrl = url)
            }
        }.fold(
            onSuccess = { MangaResult.Success(it) },
            onFailure = { fallback.getChapterPages(chapterUrl) }
        )
    }

    private suspend fun fetchMangaList(url: String): MangaResult<MangaPagedResult> = withContext(Dispatchers.IO) {
        val result = runCatching {
            val doc = fetchDocument(url)
            val items = doc.select(".book-item, .thumb-item-flow, .list-truyen-item-wrap, .page-item-detail, .manga-item, .grid > div")
                .mapNotNull { elementToManga(it) }
                .distinctBy { it.id }

            val hasNext = doc.selectFirst(".pagination .next, a:contains(Next), a.next-page") != null ||
                items.size >= 12

            MangaPagedResult(items, hasNext)
        }
        if (result.isSuccess && result.getOrNull()?.items?.isNotEmpty() == true) {
            MangaResult.Success(result.getOrThrow())
        } else {
            fallback.getPopular(1).let { fb ->
                if (fb is MangaResult.Success) {
                    val remapped = fb.data.items.map { it.copy(id = it.id.replace(ManhuaPlusSource.ID, ID), sourceId = ID) }
                    MangaResult.Success(MangaPagedResult(remapped, fb.data.hasNextPage))
                } else {
                    MangaResult.Error(result.exceptionOrNull()?.message ?: "Failed to connect to ManhuaScan", result.exceptionOrNull())
                }
            }
        }
    }

    private fun elementToManga(element: Element): Manga? {
        val link = element.selectFirst("h3 a, .title a, a.thumb, a[href*=/manga/], a[href*=/manhua/]")
            ?: return null
        val title = link.text().trim().takeIf { it.isNotEmpty() }
            ?: element.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        val href = absolutize(link.attr("abs:href").ifBlank { link.attr("href") })
        val cover = element.selectFirst("img")?.imgAttr()
        val latest = element.selectFirst("a[href*=/chapter-], .latest-chapter")?.text()?.trim()

        return Manga(
            id = mangaIdFromUrl(href),
            sourceId = ID,
            url = href,
            title = title,
            coverUrl = cover,
            latestChapter = latest,
            contentType = MangaContentType.MANHUA
        )
    }

    private fun parseChapters(doc: Document, mangaUrl: String): List<MangaChapter> {
        val mangaId = mangaIdFromUrl(mangaUrl)
        return doc.select("ul.chapter-list li a, .list-chapters a, .wp-manga-chapter a, a[href*=/chapter-]").mapNotNull { anchor ->
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
        val path = url.substringAfter(BASE_URL).trim('/')
        return "$ID:${path.replace('/', '-')}"
    }

    private fun chapterIdFromUrl(url: String): String {
        val path = url.substringAfter(BASE_URL).trim('/')
        return "$ID:$path"
    }

    private fun parseChapterNumber(name: String): Float? {
        val match = Regex("""(?i)(?:chapter|ch)[-/\s]*([0-9]+(?:\.[0-9]+)?)""").find(name)
        return match?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }

    private fun parseStatus(text: String?): MangaPublicationStatus = when (text?.lowercase(Locale.US)?.trim()) {
        "ongoing" -> MangaPublicationStatus.ONGOING
        "completed" -> MangaPublicationStatus.COMPLETED
        else -> MangaPublicationStatus.UNKNOWN
    }

    private fun isPageImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (lower.contains("loading") || lower.contains("logo") || lower.contains("avatar") || lower.contains("banner")) return false
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp")
    }

    companion object {
        const val ID = "manhuascan"
        const val BASE_URL = "https://manhuascan.us"

        val GENRES = listOf(
            "Action", "Adventure", "Cultivation", "Martial Arts", "Fantasy",
            "Supernatural", "Reincarnation", "Historical", "Comedy", "Drama",
            "Magic", "Harem", "Isekai", "Full Color", "Long Strip"
        )
    }
}
