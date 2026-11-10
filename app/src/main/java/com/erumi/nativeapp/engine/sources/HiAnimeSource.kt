package com.erumi.nativeapp.engine.sources

import android.text.Html
import android.util.Base64
import com.erumi.nativeapp.core.common.Constants
import com.erumi.nativeapp.core.network.ApiClient
import com.erumi.nativeapp.core.utils.FuzzyMatcher
import com.erumi.nativeapp.core.utils.OtakuCipher
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.Episode
import com.erumi.nativeapp.domain.model.StreamSource
import com.erumi.nativeapp.domain.model.SubtitleTrack
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.regex.Pattern

class HiAnimeSource(private val client: OkHttpClient = ApiClient.okHttpClient) : AnimeSource {

    override val name: String = "HiAnime"
    private val gson = Gson()

    data class HiAnimeSearchResult(
        val id: String,
        val slug: String,
        val name: String
    )

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val searchResults = rawSearch(query)
        searchResults.map {
            Anime(
                id = "hianime-${it.id}",
                title = it.name
            )
        }
    }

    override suspend fun getLatest(limit: Int): List<Anime> = emptyList()
    override suspend fun getPopular(limit: Int): List<Anime> = emptyList()
    override suspend fun browse(genre: String?, page: Int, limit: Int): List<Anime> = emptyList()

    suspend fun rawSearch(query: String): List<HiAnimeSearchResult> = withContext(Dispatchers.IO) {
        val clean = FuzzyMatcher.cleanTitleForHiAnime(query)
        if (clean.isBlank()) return@withContext emptyList()

        try {
            val url = "${Constants.HIANIME_BASE_URL}/search?keyword=${URLEncoder.encode(clean, "UTF-8")}"
            android.util.Log.d("ErumiHiAnime", "rawSearch URL: $url")
            val request = Request.Builder()
                .url(url)
                .build()

            val html = client.newCall(request).execute().use { resp ->
                resp.body?.string()
            } ?: return@withContext emptyList()

            android.util.Log.d("ErumiHiAnime", "HTML length: ${html.length}")

            val pattern = Pattern.compile("""<h3 class="film-name">\s*<a href="[^"]*/([^"/]*)"\s*title="([^"]*)"""")
            val matcher = pattern.matcher(html)
            val results = mutableListOf<HiAnimeSearchResult>()
            while (matcher.find()) {
                val slug = matcher.group(1) ?: continue
                val name = matcher.group(2) ?: ""
                val id = slug.substringAfterLast("-")
                val result = HiAnimeSearchResult(
                    id = id,
                    slug = slug,
                    name = Html.fromHtml(name, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                )
                results.add(result)
                android.util.Log.d("ErumiHiAnime", "Found: ${result.name} (slug: ${result.slug}, id: ${result.id})")
            }
            android.util.Log.d("ErumiHiAnime", "Total results: ${results.size}")
            results
        } catch (e: Exception) {
            android.util.Log.e("ErumiHiAnime", "rawSearch error for query '$query': ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getEpisodes(animeTitle: String, showId: String?): List<Episode> = withContext(Dispatchers.IO) {
        val cleanT = FuzzyMatcher.cleanTitleForHiAnime(animeTitle)
        android.util.Log.d("ErumiHiAnime", "getEpisodes for: $animeTitle -> cleaned: $cleanT")
        val searchResults = rawSearch(cleanT)
        android.util.Log.d("ErumiHiAnime", "rawSearch returned ${searchResults.size} results")
        if (searchResults.isEmpty()) return@withContext emptyList()

        // Never fall back to unvalidated search hits — that plays the wrong anime
        // (e.g. shared weak words like "magic"/"strongest" across unrelated titles).
        val validResults = searchResults.filter {
            FuzzyMatcher.isValidTitleMatch(cleanT, it.name, it.slug)
        }
        android.util.Log.d("ErumiHiAnime", "After isValidTitleMatch filter: ${validResults.size} valid results")
        if (validResults.isEmpty()) return@withContext emptyList()

        val best = pickBestMatch(cleanT, validResults) ?: return@withContext emptyList()
        android.util.Log.d("ErumiHiAnime", "Best match: ${best.name} (id: ${best.id}, slug: ${best.slug})")

        try {
            // First try theme endpoint
            var epUrl = "${Constants.HIANIME_BASE_URL}/api/theme/episode/list/${best.id}"
            var req = Request.Builder()
                .url(epUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "${Constants.HIANIME_BASE_URL}/")
                .build()

            var jsonStr = client.newCall(req).execute().use { resp ->
                resp.body?.string().orEmpty()
            }
            var root = gson.fromJson(jsonStr, JsonObject::class.java)
            var epHtml = root?.get("html")?.asString.orEmpty()

            if (epHtml.isBlank()) {
                // Try ajax v2
                epUrl = "${Constants.HIANIME_BASE_URL}/ajax/v2/episode/list/${best.id}"
                req = Request.Builder()
                    .url(epUrl)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "${Constants.HIANIME_BASE_URL}/watch/${best.slug}")
                    .build()
                jsonStr = client.newCall(req).execute().use { resp ->
                    resp.body?.string().orEmpty()
                }
                root = gson.fromJson(jsonStr, JsonObject::class.java)
                epHtml = root?.get("html")?.asString.orEmpty()
            }

            var epPattern = Pattern.compile("""href="[^"]*\?ep=([0-9]+)"[^>]*data-number="([^"]+)"""")
            var matcher = epPattern.matcher(epHtml)
            val episodes = mutableListOf<Episode>()

            while (matcher.find()) {
                val eid = matcher.group(1) ?: continue
                val numStr = matcher.group(2) ?: ""
                val num = numStr.toDoubleOrNull()?.toInt() ?: continue
                episodes.add(Episode(episodeNumber = num, epId = eid, title = "Episode $num"))
            }

            if (episodes.isEmpty()) {
                epPattern = Pattern.compile("""data-number="([^"]+)"[\s\S]*?data-id="([^"]*)"""")
                matcher = epPattern.matcher(epHtml)
                while (matcher.find()) {
                    val numStr = matcher.group(1) ?: ""
                    val eid = matcher.group(2) ?: ""
                    val num = numStr.toDoubleOrNull()?.toInt() ?: continue
                    episodes.add(Episode(episodeNumber = num, epId = eid, title = "Episode $num"))
                }
            }

            episodes.sortedBy { it.episodeNumber }
        } catch (e: Exception) {
            android.util.Log.e("ErumiHiAnime", "getEpisodes error for anime '$animeTitle': ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun resolveStream(
        animeTitle: String,
        episodeNumber: Int,
        audio: String
    ): StreamSource? = withContext(Dispatchers.IO) {
        val episodes = getEpisodes(animeTitle)
        val ep = episodes.firstOrNull { it.episodeNumber == episodeNumber }
            ?: episodes.getOrNull(episodeNumber - 1)
            ?: return@withContext null

        val epId = ep.epId
        if (epId.isBlank()) return@withContext null

        try {
            val srvUrl = "${Constants.HIANIME_BASE_URL}/api/theme/episode/servers?episodeId=$epId"
            val reqSrv = Request.Builder()
                .url(srvUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "${Constants.HIANIME_BASE_URL}/")
                .build()

            val srvJson = client.newCall(reqSrv).execute().use { srvResp ->
                gson.fromJson(srvResp.body?.string().orEmpty(), JsonObject::class.java)
            }
            val srvHtml = srvJson?.get("html")?.asString ?: ""

            val srvPattern = Pattern.compile(
                """class="[^"]*server-item[^"]*"[^>]*data-type="([^"]*)"[\s\S]*?data-server-name="([^"]*)"[\s\S]*?data-hash="([^"]*)"""",
                Pattern.CASE_INSENSITIVE
            )
            var srvMatcher = srvPattern.matcher(srvHtml)

            data class Srv(val type: String, val name: String, val hash: String)
            val serverList = mutableListOf<Srv>()
            while (srvMatcher.find()) {
                val stype = srvMatcher.group(1) ?: "sub"
                val sname = srvMatcher.group(2) ?: ""
                val shash = srvMatcher.group(3) ?: ""
                serverList.add(Srv(stype, sname, shash))
            }

            if (serverList.isEmpty()) {
                val altPattern = Pattern.compile(
                    """data-type="([^"]*)"[\s\S]*?data-server-name="([^"]*)"[\s\S]*?data-hash="([^"]*)"""",
                    Pattern.CASE_INSENSITIVE
                )
                srvMatcher = altPattern.matcher(srvHtml)
                while (srvMatcher.find()) {
                    val stype = srvMatcher.group(1) ?: "sub"
                    val sname = srvMatcher.group(2) ?: ""
                    val shash = srvMatcher.group(3) ?: ""
                    serverList.add(Srv(stype, sname, shash))
                }
            }

            val prefAudio = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
            val matchingServers = serverList.filter { it.type.equals(prefAudio, ignoreCase = true) }
                .ifEmpty { serverList }
                .sortedBy { if (it.name.contains("zokoanime", ignoreCase = true)) 0 else 1 }

            for (srv in matchingServers) {
                try {
                    val embedUrl = try {
                        String(Base64.decode(srv.hash, Base64.DEFAULT), Charsets.UTF_8)
                    } catch (t: Throwable) {
                        String(java.util.Base64.getDecoder().decode(srv.hash), Charsets.UTF_8)
                    }
                    if (!embedUrl.startsWith("http")) continue

                    val embReq = Request.Builder()
                        .url(embedUrl)
                        .header("Referer", "${Constants.HIANIME_BASE_URL}/")
                        .build()

                    val embHtml = client.newCall(embReq).execute().use { embResp ->
                        embResp.body?.string()
                    } ?: continue

                    val pBlob = Pattern.compile("""window\.__P\s*=\s*"([^"]*)"""")
                    val mBlob = pBlob.matcher(embHtml)
                    if (mBlob.find()) {
                        val blob = mBlob.group(1) ?: continue
                        val decrypted = OtakuCipher.deobfuscate(blob)
                        val decObj = gson.fromJson(decrypted, JsonObject::class.java)
                        val src = decObj?.get("src")?.asString
                        if (!src.isNullOrBlank()) {
                            val subtitles = mutableListOf<SubtitleTrack>()
                            val tracksArray = decObj.getAsJsonArray("tracks") ?: decObj.getAsJsonArray("subtitles")
                            tracksArray?.forEach { subElem ->
                                val subObj = subElem.asJsonObject
                                val kind = subObj.get("kind")?.asString ?: "captions"
                                if (kind.equals("thumbnails", ignoreCase = true)) return@forEach
                                val subSrc = subObj.get("file")?.asString
                                    ?: subObj.get("src")?.asString
                                    ?: subObj.get("url")?.asString ?: ""
                                val label = subObj.get("label")?.asString
                                    ?: subObj.get("lang")?.asString
                                    ?: "English"
                                val lang = subObj.get("lang")?.asString
                                    ?: subObj.get("srclang")?.asString
                                    ?: label.take(2).lowercase()
                                if (subSrc.isNotBlank()) {
                                    // Convert relative URLs to absolute
                                    val absoluteUrl = if (subSrc.startsWith("http")) {
                                        subSrc
                                    } else {
                                        try {
                                            val u = URI(embedUrl)
                                            val base = "${u.scheme}://${u.host}"
                                            if (subSrc.startsWith("/")) {
                                                "$base$subSrc"
                                            } else {
                                                val path = u.path?.substringBeforeLast("/") ?: ""
                                                "$base$path/$subSrc"
                                            }
                                        } catch (e: Exception) {
                                            subSrc
                                        }
                                    }
                                    subtitles.add(SubtitleTrack(label = label, url = absoluteUrl, language = lang))
                                }
                            }

                            if (subtitles.isEmpty()) {
                                val trackPattern = Pattern.compile("""<track\b[^>]*src=["']([^"']+)["'][^>]*>""", Pattern.CASE_INSENSITIVE)
                                val tm = trackPattern.matcher(embHtml)
                                while (tm.find()) {
                                    val tag = tm.group(0) ?: ""
                                    val srcTrack = tm.group(1) ?: continue
                                    if ("thumbnails" in tag.lowercase()) continue
                                    val labelM = Pattern.compile("""label=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(tag)
                                    val label = if (labelM.find()) labelM.group(1) ?: "English" else "English"
                                    val langM = Pattern.compile("""srclang=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(tag)
                                    val lang = if (langM.find()) langM.group(1) ?: "en" else "en"
                                    // Convert relative URLs to absolute
                                    val absoluteUrl = if (srcTrack.startsWith("http")) {
                                        srcTrack
                                    } else {
                                        try {
                                            val u = URI(embedUrl)
                                            val base = "${u.scheme}://${u.host}"
                                            if (srcTrack.startsWith("/")) {
                                                "$base$srcTrack"
                                            } else {
                                                val path = u.path?.substringBeforeLast("/") ?: ""
                                                "$base$path/$srcTrack"
                                            }
                                        } catch (e: Exception) {
                                            srcTrack
                                        }
                                    }
                                    subtitles.add(SubtitleTrack(label = label, url = absoluteUrl, language = lang))
                                }
                            }
                            android.util.Log.d("ErumiSub", "HiAnime found ${subtitles.size} subtitles for ${ep.title}")
                            subtitles.forEach { android.util.Log.d("ErumiSub", "  - ${it.label}: ${it.url}") }

                            val referer = try {
                                val u = URI(embedUrl)
                                "${u.scheme}://${u.host}/"
                            } catch (e: Exception) {
                                "${Constants.HIANIME_BASE_URL}/"
                            }

                            return@withContext StreamSource(
                                provider = "hianime",
                                server = srv.name,
                                url = src,
                                directUrl = src,
                                quality = "Auto",
                                audio = srv.type,
                                isHls = true,
                                referer = referer,
                                subtitles = subtitles
                            )
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("ErumiHiAnime", "resolveStream error for '$animeTitle' ep $episodeNumber: ${e.message}", e)
            null
        }
    }

    private fun pickBestMatch(query: String, candidates: List<HiAnimeSearchResult>): HiAnimeSearchResult? {
        if (candidates.isEmpty()) return null
        val lowerQ = query.lowercase().trim()
        val qInfo = FuzzyMatcher.parseSeasonAndPart(lowerQ)
        val qSub = FuzzyMatcher.extractSubtitleKeywords(lowerQ)

        var best: HiAnimeSearchResult? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (item in candidates) {
            val nameL = item.name.lowercase()
            val slugL = item.slug.lowercase()
            val cInfo = FuzzyMatcher.parseSeasonAndPart("$nameL ${slugL.replace('-', ' ')}")

            var sim = FuzzyMatcher.titleSimilarity(lowerQ, nameL) * 100.0
            sim += FuzzyMatcher.wordOverlapRatio(lowerQ, "$nameL ${slugL.replace('-', ' ')}") * 80.0

            if (nameL == lowerQ) sim += 200.0
            else if (nameL.startsWith(lowerQ.take(maxOf(1, lowerQ.length / 2)))) sim += 30.0
            else if (lowerQ in nameL) sim += 15.0

            if (qInfo.season != null && cInfo.season != null) {
                if (qInfo.season == cInfo.season) sim += 500.0 else sim -= 2500.0
            } else if (qInfo.season != null && qInfo.season > 1 && cInfo.season == 1) {
                sim -= 2500.0
            }

            if (qInfo.part != null && cInfo.part != null) {
                if (qInfo.part == cInfo.part) sim += 500.0 else sim -= 2500.0
            }

            if (qSub.isNotEmpty()) {
                val fullWords = FuzzyMatcher.cleanWords("$nameL ${slugL.replace('-', ' ')}").toSet()
                val subHits = qSub.intersect(fullWords)
                sim += subHits.size * 80.0
            }

            if ("movie" in slugL && "movie" !in lowerQ) sim -= 60.0
            if ("special" in slugL && "special" !in lowerQ) sim -= 50.0
            if ("ova" in slugL && "ova" !in lowerQ) sim -= 40.0

            if (sim > bestScore) {
                bestScore = sim
                best = item
            }
        }
        // Hard floor: refuse a weak "best" so we don't stream a different series.
        if (best == null || bestScore < 55.0) return null
        return best
    }
}
