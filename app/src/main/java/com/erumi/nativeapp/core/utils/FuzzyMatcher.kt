package com.erumi.nativeapp.core.utils

import java.util.regex.Pattern

object FuzzyMatcher {

    data class SeasonPartInfo(
        val season: Int?,
        val part: Int?
    )

    fun cleanForSearch(query: String): String {
        return query.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun cleanTitleForHiAnime(title: String): String {
        var t = title
        t = t.replace(Regex("""(?i)\((TV|Dub|Sub|Dubbed|Subbed|Uncensored|Batch|Raw|1080p|720p|480p)\)"""), "")
        t = t.replace(Regex("""(?i)\[(1080p|720p|480p|Batch|Dual Audio|Uncensored|Raw|Dub|Sub|ENG)\]"""), "")
        t = t.replace('[', ' ').replace(']', ' ')
        t = t.replace(Regex("""\s+"""), " ")
        return t.trim()
    }

    fun cleanWords(s: String): List<String> {
        val stopWords = setOf(
            "the", "a", "an", "by", "of", "in", "to", "with", "and", "for", "is", "at",
            "on", "from", "de", "wa", "no", "wo", "i", "my", "into", "ill", "ll"
        )
        val raw = Regex("""[a-zA-Z0-9]+""").findAll(s.lowercase()).map { it.value }.toList()
        return raw.filter { it !in stopWords && (it.length > 1 || it.all { c -> c.isDigit() }) }
    }

    fun parseSeasonAndPart(title: String): SeasonPartInfo {
        val s = title.lowercase()
        var season: Int? = null
        var part: Int? = null

        // 1. Season regex: "season 4", "s4", "4th season", "season iv"
        val mSeason = Regex("""\b(?:season|s)\s*([0-9]{1,2})\b""").find(s)
        if (mSeason != null) {
            season = mSeason.groupValues[1].toIntOrNull()
        } else {
            val mNth = Regex("""\b([0-9]{1,2})(?:st|nd|rd|th)\s+season\b""").find(s)
            if (mNth != null) {
                season = mNth.groupValues[1].toIntOrNull()
            } else {
                val mRoman = Regex("""\bseason\s+(i|ii|iii|iv|v|vi|vii|viii|ix|x)\b""").find(s)
                if (mRoman != null) {
                    val romanMap = mapOf("i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10)
                    season = romanMap[mRoman.groupValues[1]]
                }
            }
        }

        // Subtitles mapping to seasons
        if (season == null) {
            when {
                "stone wars" in s -> season = 2
                "new world" in s -> season = 3
                "science future" in s -> season = 4
                "shibuya incident" in s -> season = 2
                "yuukaku-hen" in s || "entertainment district" in s -> season = 2
                "katanakaji no sato" in s || "swordsmith village" in s -> season = 3
                "hashira geiko" in s || "hashira training" in s -> season = 4
                "thousand-year blood war" in s || "sennen kessen-hen" in s -> season = 2
                "final season" in s -> season = 4
            }
        }

        // 2. Part / Cour: "part 2", "cour 2", "part ii"
        val mPart = Regex("""\b(?:part|cour)\s*([0-9]{1,2})\b""").find(s)
        if (mPart != null) {
            part = mPart.groupValues[1].toIntOrNull()
        } else {
            val mPartNth = Regex("""\b([0-9]{1,2})(?:st|nd|rd|th)\s+(?:part|cour)\b""").find(s)
            if (mPartNth != null) {
                part = mPartNth.groupValues[1].toIntOrNull()
            } else {
                val mPartRoman = Regex("""\b(?:part|cour)\s+(i|ii|iii|iv|v|vi|vii|viii|ix|x)\b""").find(s)
                if (mPartRoman != null) {
                    val romanMap = mapOf("i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10)
                    part = romanMap[mPartRoman.groupValues[1]]
                }
            }
        }

        return SeasonPartInfo(season = season, part = part)
    }

    fun extractSubtitleKeywords(title: String): List<String> {
        val s = title.trim()
        val subtitlePart = when {
            ":" in s -> s.substringAfter(":")
            " - " in s -> s.substringAfter(" - ")
            else -> ""
        }
        if (subtitlePart.isBlank()) return emptyList()
        val words = cleanWords(subtitlePart)
        val ignore = setOf("season", "part", "cour", "tv", "dub", "sub", "hd")
        return words.filter { it !in ignore && !it.all { c -> c.isDigit() } }
    }

    fun titleSimilarity(a: String, b: String): Double {
        val sa = a.lowercase().trim()
        val sb = b.lowercase().trim()
        if (sa == sb) return 1.0
        if (sa.isEmpty() || sb.isEmpty()) return 0.0

        fun bigrams(s: String): Set<String> {
            val set = mutableSetOf<String>()
            for (i in 0 until s.length - 1) {
                set.add(s.substring(i, i + 2))
            }
            return set
        }

        val bigramsA = bigrams(sa)
        val bigramsB = bigrams(sb)
        if (bigramsA.isEmpty() && bigramsB.isEmpty()) return 1.0
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0.0

        val intersection = bigramsA.intersect(bigramsB)
        return 2.0 * intersection.size / (bigramsA.size + bigramsB.size)
    }

    fun isValidTitleMatch(query: String, candidateName: String, candidateSlug: String = ""): Boolean {
        val qLower = query.lowercase().trim()
        val cLower = candidateName.lowercase().trim()
        val cSlug = candidateSlug.lowercase().trim()

        if (qLower.isEmpty() || cLower.isEmpty()) return false

        val qInfo = parseSeasonAndPart(qLower)
        val cInfo = parseSeasonAndPart("$cLower ${cSlug.replace('-', ' ')}")

        if (qInfo.season != null && cInfo.season != null && qInfo.season != cInfo.season) {
            return false
        }
        if (qInfo.season != null && qInfo.season > 1 && cInfo.season == 1) {
            return false
        }
        if (qInfo.part != null && cInfo.part != null && qInfo.part != cInfo.part) {
            return false
        }

        val qSub = extractSubtitleKeywords(qLower)
        if (qSub.isNotEmpty()) {
            val cFullWords = cleanWords("$cLower ${cSlug.replace('-', ' ')}").toSet()
            if (qSub.intersect(cFullWords).isEmpty()) {
                return false
            }
        }

        if (qLower == cLower || qLower == cSlug) return true

        val qWords = cleanWords(qLower)
        val cWords = cleanWords("$cLower ${cSlug.replace('-', ' ')}")
        if (qWords.isEmpty() || cWords.isEmpty()) return false

        val setQ = qWords.toSet()
        val setC = cWords.toSet()
        val overlap = setQ.intersect(setC)

        val qPrefix = qLower.substringBefore(':').substringBefore(" - ").trim()
        val prefixWords = cleanWords(qPrefix)
        if (prefixWords.isNotEmpty()) {
            val prefixOverlap = prefixWords.toSet().intersect(setC)
            val prefixRatio = prefixOverlap.size.toDouble() / prefixWords.size
            if (prefixRatio < 0.5) return false
        }

        val qRatio = overlap.size.toDouble() / setQ.size
        if (qRatio < 0.35 && overlap.size < 2) return false

        return true
    }

    fun scoreSearchResult(query: String, title: String, englishName: String?, romajiName: String?, episodes: Int, type: String?, status: String?): Int {
        val q = cleanForSearch(query)
        val titles = listOfNotNull(title, englishName, romajiName).map { cleanForSearch(it) }
        var score = 0
        var exactMatch = false
        val qInfo = parseSeasonAndPart(query)

        for (t in titles) {
            if (t == q) {
                exactMatch = true
                score = maxOf(score, 100_000)
            } else if (t.startsWith("$q ")) {
                score = maxOf(score, 20_000)
            } else if (q in t) {
                score = maxOf(score, 5_000)
            }

            val tInfo = parseSeasonAndPart(t)
            if (qInfo.season != null && tInfo.season != null) {
                if (qInfo.season == tInfo.season) score += 30_000 else score -= 50_000
            }
            if (qInfo.part != null && tInfo.part != null) {
                if (qInfo.part == tInfo.part) score += 30_000 else score -= 50_000
            }
        }

        val isSpecial = Regex("""(?i)\b(movie|special|recap|ova|ona)\b""").containsMatchIn(title)
        val asksSpecial = Regex("""(?i)\b(movie|special|recap|ova|ona)\b""").containsMatchIn(query)
        if (isSpecial && !asksSpecial) {
            score -= 200_000
        }

        val showType = (type ?: "").uppercase()
        val showStatus = (status ?: "").uppercase()
        val isTv = showType in listOf("TV", "TV_SHORT")
        val isAiring = showStatus in listOf("RELEASING", "NOT_YET_RELEASED", "UPCOMING")

        if (!asksSpecial && !exactMatch && episodes <= 1 && !(isTv || isAiring)) {
            score -= 200_000
        }

        score += minOf(episodes, 1_000)
        return score
    }
}
