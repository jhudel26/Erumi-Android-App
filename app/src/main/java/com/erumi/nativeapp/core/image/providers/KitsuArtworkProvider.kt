package com.erumi.nativeapp.core.image.providers

import android.util.Log
import com.erumi.nativeapp.core.network.ApiClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Kitsu artwork provider.
 * Acts as third fallback after AniList and Jikan.
 */
class KitsuArtworkProvider(private val client: OkHttpClient = ApiClient.okHttpClient) {

    private val TAG = "ERUMI_IMAGE_KITSU"
    private val gson = Gson()
    private val BASE_URL = "https://kitsu.io/api/edge"

    /**
     * Search for anime by title and retrieve artwork information.
     */
    suspend fun searchAnime(query: String): KitsuAnime? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null

        try {
            val request = Request.Builder()
                .url("$BASE_URL/anime?filter[text]=${query.trim()}&page[limit]=5")
                .header("Accept", "application/vnd.api+json")
                .header("Content-Type", "application/vnd.api+json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Kitsu search failed: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = gson.fromJson(body, JsonObject::class.java)
            val dataArray = json.getAsJsonArray("data") ?: return@withContext null

            // Return first result
            dataArray.firstOrNull()?.let { elem ->
                parseKitsuAnime(elem.asJsonObject)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Kitsu: ${e.message}")
            null
        }
    }

    /**
     * Get anime by Kitsu ID.
     */
    suspend fun getAnimeById(kitsuId: Int): KitsuAnime? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/anime/$kitsuId")
                .header("Accept", "application/vnd.api+json")
                .header("Content-Type", "application/vnd.api+json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Kitsu get by ID failed: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = gson.fromJson(body, JsonObject::class.java)
            val data = json.getAsJsonObject("data") ?: return@withContext null

            parseKitsuAnime(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Kitsu by ID: ${e.message}")
            null
        }
    }

    private fun parseKitsuAnime(data: JsonObject): KitsuAnime {
        val attributes = data.getAsJsonObject("attributes")
        val titles = if (attributes?.has("titles") == true && !attributes.get("titles").isJsonNull) {
            attributes.getAsJsonObject("titles")
        } else null
        val posterImage = if (attributes?.has("posterImage") == true && !attributes.get("posterImage").isJsonNull) {
            attributes.getAsJsonObject("posterImage")
        } else null
        val coverImage = if (attributes?.has("coverImage") == true && !attributes.get("coverImage").isJsonNull) {
            attributes.getAsJsonObject("coverImage")
        } else null

        return KitsuAnime(
            kitsuId = data.get("id")?.asString ?: "",
            canonicalTitle = attributes?.get("canonicalTitle")?.asString ?: "",
            titleEnglish = titles?.get("en")?.asString,
            titleJapanese = titles?.get("ja_jp")?.asString ?: titles?.get("en_jp")?.asString,
            posterUrl = posterImage?.get("original")?.asString,
            coverUrl = coverImage?.get("original")?.asString,
            startDate = attributes?.get("startDate")?.asString,
            episodeCount = attributes?.get("episodeCount")?.asInt,
            subtype = attributes?.get("subtype")?.asString
        )
    }
}

data class KitsuAnime(
    val kitsuId: String,
    val canonicalTitle: String,
    val titleEnglish: String?,
    val titleJapanese: String?,
    val posterUrl: String?,
    val coverUrl: String?,
    val startDate: String?,
    val episodeCount: Int?,
    val subtype: String?
)
