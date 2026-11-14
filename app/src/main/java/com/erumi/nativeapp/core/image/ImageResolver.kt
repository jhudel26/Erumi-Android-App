package com.erumi.nativeapp.core.image

import android.content.Context
import android.util.Log
import com.erumi.nativeapp.core.database.AppDatabase
import com.erumi.nativeapp.core.database.entity.ImageCacheEntity
import com.erumi.nativeapp.core.image.providers.JikanArtworkProvider
import com.erumi.nativeapp.core.image.providers.KitsuArtworkProvider
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.engine.sources.AniListSource
import com.erumi.nativeapp.manga.domain.models.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized image resolver for Erumi anime posters and banners.
 * Implements multi-source fallback: AllAnime → AniList → Placeholder
 * Also supports manga cover images.
 */
class ImageResolver(private val context: Context) {

    private val TAG = "ERUMI_IMAGE"
    private val database = AppDatabase.getInstance(context)
    private val aniListSource = AniListSource()
    private val jikanProvider = JikanArtworkProvider()
    private val kitsuProvider = KitsuArtworkProvider()

    // In-memory LRU cache (bounded to 500 entries) to avoid redundant concurrent requests
    private val ongoingResolutions: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 500
        }
    )
    // Separate in-progress flags for poster and banner to avoid blocking each other (B1)
    private val inProgressPoster = ConcurrentHashMap<String, Boolean>()
    private val inProgressBanner = ConcurrentHashMap<String, Boolean>()

    // Cache expiry: 7 days in milliseconds
    private val CACHE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L

    /**
     * Resolves the poster URL for an anime using multi-source fallback.
     * Priority: Cached URL → Provided poster → AniList fallback → Placeholder
     */
    suspend fun resolvePosterUrl(anime: Anime): String? = withContext(Dispatchers.IO) {
        val animeId = anime.id
        val title = anime.title

        Log.d(TAG, "Resolving poster for: $title (id: $animeId)")

        // Check if poster resolution already in progress
        if (inProgressPoster[animeId] == true) {
            Log.d(TAG, "Resolution already in progress for: $title")
            return@withContext null
        }

        // Mark poster as in progress
        inProgressPoster[animeId] = true

        try {
            // Check memory cache for ongoing resolution
            ongoingResolutions[animeId]?.let { cached ->
                Log.d(TAG, "Returning in-memory cached poster for: $title")
                return@withContext cached
            }

            // Check database cache
            val cachedImage = database.imageCacheDao().getImageCacheById(animeId)
            if (cachedImage != null && !isCacheExpired(cachedImage.resolvedAt)) {
                cachedImage.resolvedPosterUrl?.let { poster ->
                    Log.d(TAG, "Returning cached poster from DB for: $title")
                    ongoingResolutions[animeId] = poster
                    return@withContext poster
                }
            }

            // Normalize provided poster
            val normalizedPoster = normalizeImageUrl(anime.poster)
            Log.d(TAG, "Primary poster: ${normalizedPoster ?: "null"}")

            if (isValidImageUrl(normalizedPoster)) {
                Log.d(TAG, "Primary poster is valid for: $title")
                cacheImage(animeId, normalizedPoster, anime.banner, "allanime")
                ongoingResolutions[animeId] = normalizedPoster!!
                return@withContext normalizedPoster
            }

            Log.d(TAG, "Primary poster unavailable or invalid, attempting AniList fallback for: $title")

            // Attempt AniList fallback
            val anilistPoster = fetchAniListPoster(anime)
            if (isValidImageUrl(anilistPoster)) {
                Log.d(TAG, "AniList fallback resolved for: $title")
                cacheImage(animeId, anilistPoster, anime.banner, "anilist")
                ongoingResolutions[animeId] = anilistPoster!!
                return@withContext anilistPoster
            }

            Log.d(TAG, "AniList fallback failed, attempting Jikan fallback for: $title")

            // Attempt Jikan fallback
            val jikanPoster = fetchJikanPoster(anime)
            if (isValidImageUrl(jikanPoster)) {
                Log.d(TAG, "Jikan fallback resolved for: $title")
                cacheImage(animeId, jikanPoster, anime.banner, "jikan")
                ongoingResolutions[animeId] = jikanPoster!!
                return@withContext jikanPoster
            }

            Log.d(TAG, "Jikan fallback failed, attempting Kitsu fallback for: $title")

            // Attempt Kitsu fallback
            val kitsuPoster = fetchKitsuPoster(anime)
            if (isValidImageUrl(kitsuPoster)) {
                Log.d(TAG, "Kitsu fallback resolved for: $title")
                cacheImage(animeId, kitsuPoster, anime.banner, "kitsu")
                ongoingResolutions[animeId] = kitsuPoster!!
                return@withContext kitsuPoster
            }

            Log.d(TAG, "All image sources failed for: $title")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving poster for $title: ${e.message}", e)
            null
        } finally {
            // Clear in-progress poster flag to allow retries after error
            inProgressPoster.remove(animeId)
        }
    }

    /**
     * Resolves the banner URL for an anime.
     */
    suspend fun resolveBannerUrl(anime: Anime): String? = withContext(Dispatchers.IO) {
        val animeId = anime.id
        val title = anime.title

        Log.d(TAG, "Resolving banner for: $title (id: $animeId)")

        // Check if banner resolution already in progress (separate flag from poster)
        if (inProgressBanner[animeId] == true) {
            Log.d(TAG, "Banner resolution already in progress for: $title")
            return@withContext null
        }

        // Mark banner as in progress
        inProgressBanner[animeId] = true

        try {
            // Check database cache
            val cachedImage = database.imageCacheDao().getImageCacheById(animeId)
            if (cachedImage != null && !isCacheExpired(cachedImage.resolvedAt)) {
                cachedImage.resolvedBannerUrl?.let { banner ->
                    Log.d(TAG, "Returning cached banner from DB for: $title")
                    return@withContext banner
                }
            }

            // Normalize provided banner
            val normalizedBanner = normalizeImageUrl(anime.banner)
            if (isValidImageUrl(normalizedBanner)) {
                Log.d(TAG, "Primary banner is valid for: $title")
                cacheImage(animeId, anime.poster, normalizedBanner, "allanime")
                return@withContext normalizedBanner
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving banner for $title: ${e.message}", e)
            null
        } finally {
            // Clear in-progress banner flag to allow retries after error
            inProgressBanner.remove(animeId)
        }
    }

    /**
     * Normalizes image URLs to handle protocol-relative URLs and other edge cases.
     */
    private fun normalizeImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null

        val trimmed = url.trim()

        // Already valid https
        if (trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }

        // Convert http to https
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            return trimmed.replace("http://", "https://", ignoreCase = true)
        }

        // Handle protocol-relative URLs (//cdn.example.com/image.jpg)
        if (trimmed.startsWith("//")) {
            return "https:$trimmed"
        }

        // Any other format is considered invalid
        Log.w(TAG, "Invalid URL format: $trimmed")
        return null
    }

    /**
     * Validates if a URL is suitable for image loading.
     */
    private fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        // Must start with https
        if (!url.startsWith("https://", ignoreCase = true)) return false

        // Must have a valid image extension or be from a known CDN
        val validExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif")
        val hasValidExtension = validExtensions.any { url.lowercase().endsWith(it) }

        // Allow URLs without extensions if they're from known CDNs (common for dynamic image services)
        val isKnownCdn = url.contains("cdn") || url.contains("imgur") || url.contains("anilist")

        return hasValidExtension || isKnownCdn
    }

    /**
     * Fetches poster from AniList using safe anime matching.
     */
    private suspend fun fetchAniListPoster(anime: Anime): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching AniList metadata for: ${anime.title}")

            // Use AniList's detailed metadata fetch with fuzzy matching
            val anilistAnime = aniListSource.fetchDetailedMetadata(
                title = anime.title,
                year = anime.releaseYear
            )

            if (anilistAnime != null) {
                val poster = normalizeImageUrl(anilistAnime.poster)
                if (isValidImageUrl(poster)) {
                    Log.d(TAG, "AniList match found: ${anilistAnime.title} (id: ${anilistAnime.id})")
                    return@withContext poster
                } else {
                    Log.w(TAG, "AniList match found but poster is invalid: ${anilistAnime.title}")
                }
            } else {
                Log.d(TAG, "No AniList match found for: ${anime.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AniList fallback for ${anime.title}: ${e.message}")
        }

        null
    }

    /**
     * Fetches poster from Jikan with enhanced matching.
     */
    private suspend fun fetchJikanPoster(anime: Anime): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching Jikan metadata for: ${anime.title}")

            val jikanAnime = jikanProvider.searchAnime(anime.title)

            if (jikanAnime != null) {
                // Enhanced matching: verify year and type
                val yearMatch = anime.releaseYear == null || jikanAnime.year == null ||
                               kotlin.math.abs(anime.releaseYear - jikanAnime.year) <= 1
                val typeMatch = anime.format == null || jikanAnime.type == null ||
                               anime.format.equals(jikanAnime.type, ignoreCase = true)

                if (yearMatch && typeMatch) {
                    // Prefer WebP, fallback to JPG
                    val poster = normalizeImageUrl(jikanAnime.webpUrl ?: jikanAnime.imageUrl)
                    if (isValidImageUrl(poster)) {
                        Log.d(TAG, "Jikan match found: ${jikanAnime.title} (mal_id: ${jikanAnime.malId})")
                        return@withContext poster
                    } else {
                        Log.w(TAG, "Jikan match found but poster is invalid: ${jikanAnime.title}")
                    }
                } else {
                    Log.d(TAG, "Jikan match rejected due to year/type mismatch: ${jikanAnime.title}")
                }
            } else {
                Log.d(TAG, "No Jikan match found for: ${anime.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Jikan fallback for ${anime.title}: ${e.message}")
        }

        null
    }

    /**
     * Fetches poster from Kitsu with enhanced matching.
     */
    private suspend fun fetchKitsuPoster(anime: Anime): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching Kitsu metadata for: ${anime.title}")

            val kitsuAnime = kitsuProvider.searchAnime(anime.title)

            if (kitsuAnime != null) {
                // Enhanced matching: verify year from startDate
                val kitsuYear = kitsuAnime.startDate?.substring(0, 4)?.toIntOrNull()
                val yearMatch = anime.releaseYear == null || kitsuYear == null ||
                               kotlin.math.abs(anime.releaseYear - kitsuYear) <= 1
                val typeMatch = anime.format == null || kitsuAnime.subtype == null ||
                               anime.format.equals(kitsuAnime.subtype, ignoreCase = true)

                if (yearMatch && typeMatch) {
                    val poster = normalizeImageUrl(kitsuAnime.posterUrl ?: kitsuAnime.coverUrl)
                    if (isValidImageUrl(poster)) {
                        Log.d(TAG, "Kitsu match found: ${kitsuAnime.canonicalTitle} (id: ${kitsuAnime.kitsuId})")
                        return@withContext poster
                    } else {
                        Log.w(TAG, "Kitsu match found but poster is invalid: ${kitsuAnime.canonicalTitle}")
                    }
                } else {
                    Log.d(TAG, "Kitsu match rejected due to year/type mismatch: ${kitsuAnime.canonicalTitle}")
                }
            } else {
                Log.d(TAG, "No Kitsu match found for: ${anime.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Kitsu fallback for ${anime.title}: ${e.message}")
        }

        null
    }

    /**
     * Caches resolved image URLs in the database.
     */
    private suspend fun cacheImage(
        animeId: String,
        posterUrl: String?,
        bannerUrl: String?,
        source: String
    ) {
        try {
            val cacheEntity = ImageCacheEntity(
                animeId = animeId,
                resolvedPosterUrl = posterUrl,
                resolvedBannerUrl = bannerUrl,
                source = source
            )
            database.imageCacheDao().upsertImageCache(cacheEntity)
            Log.d(TAG, "Cached image mapping for animeId: $animeId (source: $source)")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching image for animeId $animeId: ${e.message}")
        }
    }

    /**
     * Checks if a cache entry has expired.
     */
    private fun isCacheExpired(resolvedAt: Long): Boolean {
        return System.currentTimeMillis() - resolvedAt > CACHE_EXPIRY_MS
    }

    /**
     * Clears expired cache entries to prevent database bloat.
     */
    suspend fun clearExpiredCache() = withContext(Dispatchers.IO) {
        try {
            val expiryTime = System.currentTimeMillis() - CACHE_EXPIRY_MS
            database.imageCacheDao().deleteExpiredCache(expiryTime)
            Log.d(TAG, "Cleared expired image cache entries")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing expired cache: ${e.message}")
        }
    }

    /**
     * Clears cache for a specific anime (useful for manual refresh).
     */
    suspend fun clearCacheForAnime(animeId: String) = withContext(Dispatchers.IO) {
        try {
            database.imageCacheDao().deleteCache(animeId)
            ongoingResolutions.remove(animeId)
            inProgressPoster.remove(animeId)
            inProgressBanner.remove(animeId)
            Log.d(TAG, "Cleared cache for animeId: $animeId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache for animeId $animeId: ${e.message}")
        }
    }

    // ==================== MANGA SUPPORT ====================

    /**
     * Resolves the cover/poster URL for a manga using multi-source fallback.
     * Priority: Cached URL → Provided cover → AniList fallback (if applicable) → Placeholder
     */
    suspend fun resolvePosterUrl(manga: Manga): String? = withContext(Dispatchers.IO) {
        val mangaId = manga.id
        val title = manga.title

        Log.d(TAG, "Resolving manga cover for: $title (id: $mangaId)")

        // Check if resolution already in progress
        if (inProgressPoster[mangaId] == true) {
            Log.d(TAG, "Manga resolution already in progress for: $title")
            return@withContext null
        }

        // Mark as in progress
        inProgressPoster[mangaId] = true

        try {
            // Check memory cache for ongoing resolution
            ongoingResolutions[mangaId]?.let { cached ->
                Log.d(TAG, "Returning in-memory cached manga cover for: $title")
                return@withContext cached
            }

            // Check database cache
            val cachedImage = database.imageCacheDao().getImageCacheById(mangaId)
            if (cachedImage != null && !isCacheExpired(cachedImage.resolvedAt)) {
                cachedImage.resolvedPosterUrl?.let { poster ->
                    Log.d(TAG, "Returning cached manga cover from DB for: $title")
                    ongoingResolutions[mangaId] = poster
                    return@withContext poster
                }
            }

            // Normalize provided cover
            val normalizedCover = normalizeImageUrl(manga.coverUrl)
            Log.d(TAG, "Primary manga cover: ${normalizedCover ?: "null"}")

            if (isValidImageUrl(normalizedCover)) {
                Log.d(TAG, "Primary manga cover is valid for: $title")
                cacheImage(mangaId, normalizedCover, null, "manga_source")
                ongoingResolutions[mangaId] = normalizedCover!!
                return@withContext normalizedCover
            }

            Log.d(TAG, "Primary manga cover unavailable or invalid for: $title")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving manga cover for $title: ${e.message}", e)
            null
        } finally {
            inProgressPoster.remove(mangaId)
        }
    }

    /**
     * Resolves banner URL for manga (not typically available, returns null).
     * Kept for API compatibility with HeroCarousel.
     */
    suspend fun resolveBannerUrl(manga: Manga): String? = withContext(Dispatchers.IO) {
        null
    }

    companion object {
        @Volatile
        private var instance: ImageResolver? = null

        fun getInstance(context: Context): ImageResolver {
            return instance ?: synchronized(this) {
                instance ?: ImageResolver(context.applicationContext).also { instance = it }
            }
        }
    }
}