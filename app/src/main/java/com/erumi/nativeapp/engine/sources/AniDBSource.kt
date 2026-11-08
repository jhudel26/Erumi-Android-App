package com.erumi.nativeapp.engine.sources

import com.erumi.nativeapp.core.common.Constants
import com.erumi.nativeapp.core.network.ApiClient
import com.erumi.nativeapp.core.utils.FuzzyMatcher
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.Episode
import com.erumi.nativeapp.domain.model.StreamSource
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.regex.Pattern

class AniDBSource(private val client: OkHttpClient = ApiClient.okHttpClient) : AnimeSource {

    override val name: String = "AniDB"
    private val gson = Gson()

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
            val q = URLEncoder.encode(animeTitle.trim(), "UTF-8")
            val searchUrl = "${Constants.ANIDB_BASE_URL}/search/suggestions?q=$q"
            val req = Request.Builder()
                .url(searchUrl)
                .header("Referer", "${Constants.ANIDB_BASE_URL}/")
                .build()

            val page = client.newCall(req).execute().use {
                it.body?.string()
            } ?: return@withContext null
            val pattern = Pattern.compile("""anime/([a-z0-9-]+)-(\d+)""", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(page)

            data class MatchItem(val slug: String, val id: String)
            val matches = mutableListOf<MatchItem>()
            while (matcher.find()) {
                val slug = matcher.group(1) ?: continue
                val id = matcher.group(2) ?: continue
                matches.add(MatchItem(slug, id))
            }
            if (matches.isEmpty()) return@withContext null

            val qInfo = FuzzyMatcher.parseSeasonAndPart(animeTitle)
            val qSub = FuzzyMatcher.extractSubtitleKeywords(animeTitle)
            var bestId = matches.first().id
            var bestScore = -999999.0
            val cleanTarget = FuzzyMatcher.cleanForSearch(animeTitle)

            for (m in matches) {
                val tCand = m.slug.replace("-", " ")
                val cInfo = FuzzyMatcher.parseSeasonAndPart(tCand)
                var score = FuzzyMatcher.titleSimilarity(cleanTarget, tCand) * 100.0

                if (qInfo.season != null && cInfo.season != null) {
                    if (qInfo.season == cInfo.season) score += 500.0 else score -= 2000.0
                } else if (qInfo.season != null && qInfo.season > 1 && cInfo.season == 1) {
                    score -= 2000.0
                }

                if (qInfo.part != null && cInfo.part != null) {
                    if (qInfo.part == cInfo.part) score += 500.0 else score -= 2000.0
                }

                if (qSub.isNotEmpty()) {
                    val words = FuzzyMatcher.cleanWords(tCand).toSet()
                    if (qSub.intersect(words).isNotEmpty()) score += 300.0
                }

                if (score > bestScore) {
                    bestScore = score
                    bestId = m.id
                }
            }

            val epUrl = "${Constants.ANIDB_BASE_URL}/api/frontend/anime/$bestId/episodes"
            val reqEp = Request.Builder()
                .url(epUrl)
                .header("Referer", "${Constants.ANIDB_BASE_URL}/")
                .build()

            val epJson = client.newCall(reqEp).execute().use {
                it.body?.string()
            } ?: return@withContext null
            val epElement = try {
                gson.fromJson(epJson, com.google.gson.JsonElement::class.java)
            } catch (e: Exception) {
                null
            } ?: return@withContext null

            val epArray = when {
                epElement.isJsonArray -> epElement.asJsonArray
                epElement.isJsonObject -> {
                    epElement.asJsonObject.getAsJsonArray("episodes")
                        ?: epElement.asJsonObject.getAsJsonArray("data")
                }
                else -> null
            } ?: return@withContext null

            var targetEpId: String? = null
            for (elem in epArray) {
                val obj = elem.asJsonObject
                val num = obj.get("number")?.takeIf { !it.isJsonNull }?.asString
                if (num == episodeNumber.toString()) {
                    targetEpId = obj.get("id")?.asString
                    break
                }
            }

            if (targetEpId == null && episodeNumber <= epArray.size() && episodeNumber > 0) {
                targetEpId = epArray[episodeNumber - 1].asJsonObject.get("id")?.asString
            }
            if (targetEpId == null) return@withContext null

            val langUrl = "${Constants.ANIDB_BASE_URL}/api/frontend/episode/$targetEpId/languages"
            val reqLang = Request.Builder()
                .url(langUrl)
                .header("Referer", "${Constants.ANIDB_BASE_URL}/")
                .build()

            val langJson = client.newCall(reqLang).execute().use {
                it.body?.string()
            } ?: return@withContext null
            val langElem = try {
                gson.fromJson(langJson, com.google.gson.JsonElement::class.java)
            } catch (e: Exception) {
                null
            } ?: return@withContext null

            val langArray = when {
                langElem.isJsonArray -> langElem.asJsonArray
                langElem.isJsonObject -> langElem.asJsonObject.getAsJsonArray("languages")
                else -> null
            }
            if (langArray == null || langArray.isEmpty) return@withContext null

            val pref = if (audio.equals("dub", ignoreCase = true)) "eng" else "jpn"
            var embedUrl: String? = null
            for (elem in langArray) {
                val obj = elem.asJsonObject
                if (obj.get("code")?.asString?.equals(pref, ignoreCase = true) == true) {
                    embedUrl = obj.get("embed_url")?.asString
                    break
                }
            }
            if (embedUrl == null) {
                embedUrl = langArray[0].asJsonObject.get("embed_url")?.asString
            }
            if (embedUrl.isNullOrBlank()) return@withContext null

            val reqEmbed = Request.Builder()
                .url(embedUrl)
                .header("Referer", "${Constants.ANIDB_BASE_URL}/")
                .build()

            val embedPage = client.newCall(reqEmbed).execute().use {
                it.body?.string()
            } ?: return@withContext null
            val m3u8Pattern = Pattern.compile("""file:\s*['"]([^'"]+)['"]""")
            val m3u8Matcher = m3u8Pattern.matcher(embedPage)
            if (m3u8Matcher.find()) {
                val streamUrl = m3u8Matcher.group(1) ?: return@withContext null
                return@withContext StreamSource(
                    provider = "anidb",
                    server = "AniDB Server",
                    url = streamUrl,
                    directUrl = streamUrl,
                    quality = "1080p",
                    audio = audio,
                    isHls = true,
                    referer = "${Constants.ANIDB_BASE_URL}/"
                )
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
