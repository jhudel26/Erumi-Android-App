package com.erumi.nativeapp.engine.sources

import com.erumi.nativeapp.core.common.Constants
import com.erumi.nativeapp.core.network.ApiClient
import com.erumi.nativeapp.core.utils.FuzzyMatcher
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.Episode
import com.erumi.nativeapp.domain.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.regex.Pattern

class AniNekoSource(private val client: OkHttpClient = ApiClient.okHttpClient) : AnimeSource {

    override val name: String = "AniNeko"

    override suspend fun search(query: String): List<Anime> = emptyList()
    override suspend fun getLatest(limit: Int): List<Anime> = emptyList()
    override suspend fun getPopular(limit: Int): List<Anime> = emptyList()
    override suspend fun browse(genre: String?, page: Int, limit: Int): List<Anime> = emptyList()
    override suspend fun getEpisodes(animeTitle: String, showId: String?): List<Episode> = emptyList()

    override suspend fun resolveStream(
        animeTitle: String,
        episodeNumber: Int,
        audio: String
    ): StreamSource? = withContext(Dispatchers.IO) {
        try {
            val cleanT = FuzzyMatcher.cleanTitleForHiAnime(animeTitle)
            val searchUrl = "${Constants.ANINEKO_BASE_URL}/browser?keyword=${URLEncoder.encode(cleanT, "UTF-8")}"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .build()

            val searchHtml = client.newCall(searchReq).execute().use {
                it.body?.string()
            } ?: return@withContext null

            data class ResultItem(val slug: String, val title: String)
            val results = mutableListOf<ResultItem>()

            val itemPattern = Pattern.compile("""<a\b[^>]*class=["'][^"']*nv-anime-thumb[^"']*["'][^>]*>[\s\S]*?</a>""", Pattern.CASE_INSENSITIVE)
            val itemMatcher = itemPattern.matcher(searchHtml)
            while (itemMatcher.find()) {
                val block = itemMatcher.group(0) ?: continue
                val hrefM = Pattern.compile("""href=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(block)
                if (!hrefM.find()) continue
                val href = hrefM.group(1) ?: continue
                val slugM = Pattern.compile("""/watch/([^/?#]+)""").matcher(href)
                if (!slugM.find()) continue
                val slug = slugM.group(1) ?: continue

                val titleM = Pattern.compile("""<(?:h3|[^>]+class=["'][^"']*nv-anime-title[^"']*["'][^>]*)>([\s\S]*?)</(?:h3|[^>]+)>""", Pattern.CASE_INSENSITIVE).matcher(block)
                val rawTitle = if (titleM.find()) titleM.group(1)?.replace(Regex("""<[^>]*>"""), "")?.trim() else slug.replace("-", " ")
                results.add(ResultItem(slug, rawTitle ?: slug.replace("-", " ")))
            }

            if (results.isEmpty()) return@withContext null

            val expected = cleanT.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
            val expectedNoThe = expected.removePrefix("the-")
            var bestSlug = results.first().slug
            var bestScore = -999999
            val qInfo = FuzzyMatcher.parseSeasonAndPart(cleanT)
            val qSubs = FuzzyMatcher.extractSubtitleKeywords(cleanT)

            for (r in results) {
                var score = 0
                val slug = r.slug.lowercase()
                val slugNoThe = slug.removePrefix("the-")
                val t = r.title.lowercase()
                val cInfo = FuzzyMatcher.parseSeasonAndPart("$t ${slug.replace("-", " ")}")

                if (slug == expected || slugNoThe == expectedNoThe) score += 2500
                else if (t == cleanT.lowercase()) score += 2000
                else if (slug.startsWith(expected) || expected.startsWith(slug)) score += 1200
                else if (cleanT.lowercase() in t || t in cleanT.lowercase()) score += 800

                if (qInfo.season != null && cInfo.season != null) {
                    if (qInfo.season == cInfo.season) score += 1500 else score -= 4000
                } else if (qInfo.season != null && qInfo.season > 1 && cInfo.season == 1) {
                    score -= 4000
                }

                if (qInfo.part != null && cInfo.part != null) {
                    if (qInfo.part == cInfo.part) score += 1500 else score -= 4000
                }

                if (qSubs.isNotEmpty()) {
                    val fullWords = FuzzyMatcher.cleanWords("$t ${slug.replace("-", " ")}").toSet()
                    if (qSubs.intersect(fullWords).isNotEmpty()) score += 800
                }

                if ("movie" in slug || "movie" in t) score -= 700
                if ("special" in slug || "special" in t) score -= 600

                if (score > bestScore) {
                    bestScore = score
                    bestSlug = r.slug
                }
            }

            val watchUrl = "${Constants.ANINEKO_BASE_URL}/watch/$bestSlug/ep-$episodeNumber"
            val watchReq = Request.Builder()
                .url(watchUrl)
                .header("Referer", "${Constants.ANINEKO_BASE_URL}/watch/$bestSlug")
                .build()

            val watchHtml = client.newCall(watchReq).execute().use {
                it.body?.string()
            } ?: return@withContext null
            val embedUrls = mutableListOf<String>()

            val panelPattern = Pattern.compile(
                """<div\b[^>]*class=["'][^"']*nv-server-grid[^"']*["'][^>]*data-id=["']([^"']+)["'][^>]*>([\s\S]*?)(?=<div\b[^>]*class=["'][^"']*nv-server-grid|${'$'})""",
                Pattern.CASE_INSENSITIVE
            )
            val panelMatcher = panelPattern.matcher(watchHtml)
            while (panelMatcher.find()) {
                val rawAudio = panelMatcher.group(1)?.lowercase() ?: ""
                val panelAudio = if ("dub" in rawAudio) "dub" else "sub"
                if (panelAudio.equals(audio, ignoreCase = true) || embedUrls.isEmpty()) {
                    val btnPattern = Pattern.compile("""data-video=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
                    val btnMatcher = btnPattern.matcher(panelMatcher.group(2) ?: "")
                    while (btnMatcher.find()) {
                        val videoUrl = btnMatcher.group(1) ?: continue
                        embedUrls.add(android.text.Html.fromHtml(videoUrl, android.text.Html.FROM_HTML_MODE_LEGACY).toString())
                    }
                }
            }

            for (embed in embedUrls) {
                try {
                    val embedReq = Request.Builder()
                        .url(embed)
                        .header("Referer", "${Constants.ANINEKO_BASE_URL}/")
                        .build()

                    val embedHtml = client.newCall(embedReq).execute().use {
                        it.body?.string()
                    } ?: continue

                    val patterns = listOf(
                        """const\s+src\s*=\s*["'](https?://[^"']+\.m3u8[^"']*)["']""",
                        """file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""",
                        """["'](https?://[^"']+/master\.m3u8[^"']*)["']""",
                        """["'](https?://[^"']+\.m3u8[^"']*)["']"""
                    )
                    for (p in patterns) {
                        val m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(embedHtml)
                        if (m.find()) {
                            val hlsUrl = m.group(1) ?: continue
                            val origin = try {
                                val parsedOrigin = URI(embed.trim())
                                "${parsedOrigin.scheme}://${parsedOrigin.host}/"
                            } catch (e: Exception) {
                                "${Constants.ANINEKO_BASE_URL}/"
                            }
                            return@withContext StreamSource(
                                provider = "anineko",
                                server = "AniNeko Server",
                                url = hlsUrl,
                                directUrl = hlsUrl,
                                quality = "Auto",
                                audio = audio,
                                isHls = true,
                                referer = origin
                            )
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
