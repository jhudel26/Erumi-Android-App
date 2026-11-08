package com.erumi.nativeapp

import com.erumi.nativeapp.core.utils.FuzzyMatcher
import com.erumi.nativeapp.core.utils.OtakuCipher
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class FuzzyMatcherTest {

    @Test
    fun testCleanForSearch() {
        val query = "Solo Leveling: Arise (2024)!"
        val cleaned = FuzzyMatcher.cleanForSearch(query)
        assertEquals("solo leveling arise 2024", cleaned)
    }

    @Test
    fun testParseSeasonAndPart() {
        val title1 = "Jujutsu Kaisen Season 2"
        val info1 = FuzzyMatcher.parseSeasonAndPart(title1)
        assertEquals(2, info1.season)
        assertNull(info1.part)

        val title2 = "Bleach: Thousand-Year Blood War Part 3"
        val info2 = FuzzyMatcher.parseSeasonAndPart(title2)
        assertEquals(2, info2.season) // Subtitle maps to season 2
        assertEquals(3, info2.part)

        val title3 = "Dr. STONE New World Part 2"
        val info3 = FuzzyMatcher.parseSeasonAndPart(title3)
        assertEquals(3, info3.season) // "new world" -> Season 3
        assertEquals(2, info3.part)
    }

    @Test
    fun testTitleSimilarity() {
        val s1 = "Frieren: Beyond Journey's End"
        val s2 = "Frieren Beyond Journey End"
        val similarity = FuzzyMatcher.titleSimilarity(s1, s2)
        assertTrue("Similarity should be > 0.8 but was $similarity", similarity > 0.8)

        val diff = FuzzyMatcher.titleSimilarity("Demon Slayer", "Attack on Titan")
        assertTrue("Similarity should be < 0.3 but was $diff", diff < 0.3)
    }

    @Test
    fun testIsValidTitleMatch() {
        val query = "Solo Leveling Season 2"
        val wrongSeason = "Solo Leveling Season 1"
        assertFalse(FuzzyMatcher.isValidTitleMatch(query, wrongSeason))

        val rightMatch = "Solo Leveling: Arise from the Shadow Season 2"
        assertTrue(FuzzyMatcher.isValidTitleMatch(query, rightMatch, "solo-leveling-season-2"))
    }

    @Test
    fun testRejectsUnrelatedIsekaiTitlesSharingWeakWords() {
        // Regression: selecting Magic Repo Man must NOT match Easygoing Territory Defense
        // just because both titles contain "magic" / "strongest".
        val query =
            "Magic Repo man: Dumped by my party, I'll cash in with a cute support fairy to become the strongest!"
        val wrong =
            "Easygoing Territory Defense by the Optimistic Lord: Production Magic Turns a Nameless Village into the Strongest Fortified City"
        val wrongSlug =
            "easygoing-territory-defense-by-the-optimistic-lord-production-magic-turns-a-nameless-village-into-the-strongest-fortified-city"

        assertFalse(
            "Unrelated isekai titles must not match via weak words",
            FuzzyMatcher.isValidTitleMatch(query, wrong, wrongSlug)
        )

        // A real alternate / abbreviation of the same series should still pass.
        assertTrue(
            FuzzyMatcher.isValidTitleMatch(
                query,
                "Magic Repo Man: Dumped by My Party, I'll Cash In with a Cute Support Fairy to Become the Strongest!",
                "magic-repo-man-dumped-by-my-party-ill-cash-in-with-a-cute-support-fairy-to-become-the-strongest"
            )
        )

        // English ↔ romaji do not share words — EpisodeResolver must still try romaji as a
        // HiAnime key after AniList English validates (do not require this to return true).
        assertFalse(
            FuzzyMatcher.isValidTitleMatch(
                query,
                "Kashita Maryoku wa [Revo Barai] de Kyousei Choushuu",
                "kashita-maryoku-wa-revo-barai-de-kyousei-choushuu-10496"
            )
        )
    }

    @Test
    fun testCleanTitleForHiAnimeKeepsBracketWords() {
        val cleaned = FuzzyMatcher.cleanTitleForHiAnime(
            "Kashita Maryoku wa [Revo Barai] de Kyousei Choushuu"
        )
        assertTrue(cleaned.contains("Revo Barai", ignoreCase = true))
        assertFalse(cleaned.contains("["))
        assertFalse(cleaned.contains("]"))
    }

    @Test
    fun testScoreSearchResult() {
        val query = "Frieren"
        val exactScore = FuzzyMatcher.scoreSearchResult(
            query = query,
            title = "Frieren: Beyond Journey's End",
            englishName = "Frieren: Beyond Journey's End",
            romajiName = "Sousou no Frieren",
            episodes = 28,
            type = "TV",
            status = "FINISHED"
        )

        val movieScore = FuzzyMatcher.scoreSearchResult(
            query = query,
            title = "Frieren Special Movie Recap",
            englishName = null,
            romajiName = null,
            episodes = 1,
            type = "MOVIE",
            status = "FINISHED"
        )

        assertTrue("Main TV series score ($exactScore) should exceed movie recap score ($movieScore)", exactScore > movieScore)
    }

    @Test
    fun testOtakuCipherDeobfuscate() {
        // Test OtakuCipher XOR logic
        val raw = "{\"src\":\"https://test.cdn/video.m3u8\"}"
        val key = "otaku-embed-v1".toByteArray(Charsets.UTF_8)
        val encrypted = ByteArray(raw.toByteArray(Charsets.UTF_8).size)
        val rawBytes = raw.toByteArray(Charsets.UTF_8)
        for (i in rawBytes.indices) {
            encrypted[i] = (rawBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        val base64Str = Base64.getEncoder().encodeToString(encrypted)

        // Deobfuscate
        val decrypted = OtakuCipher.deobfuscate(base64Str)
        assertEquals(raw, decrypted)
    }
}
