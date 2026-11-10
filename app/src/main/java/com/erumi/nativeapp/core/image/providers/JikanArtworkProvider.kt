package com.erumi.nativeapp.core.image.providers

import android.util.Log
import com.erumi.nativeapp.core.network.ApiClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Jikan (MyAnimeList API) artwork provider.
 * Acts as second fallback after AniList.
 */
class JikanArtworkProvider(private val client: OkHttpClient = ApiClient.okHttpClient) {

    private val TAG = "ERUMI_IMAGE_JIKAN"
    private val gson = Gson()
    private val BASE_URL = "https://api.jikan.moe/v4"

    /**
     * Search for anime by title and retrieve artwork information.
     */
    suspend fun searchAnime(query: String): JikanAnime? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null

        try {
            val request = Request.Builder()
                .url("$BASE_URL/anime?q=${query.trim()}&limit=5")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Jikan search failed: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = gson.fromJson(body, JsonObject::class.java)
            val dataArray = json.getAsJsonArray("data") ?: return@withContext null

            // Return first result
            dataArray.firstOrNull()?.let { elem ->
                parseJikanAnime(elem.asJsonObject)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Jikan: ${e.message}")
            null
        }
    }

    /**
     * Get anime by MyAnimeList ID.
     */
    suspend fun getAnimeById(malId: Int): JikanAnime? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/anime/$malId/full")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Jikan get by ID failed: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = gson.fromJson(body, JsonObject::class.java)
            val data = json.getAsJsonObject("data") ?: return@withContext null

            parseJikanAnime(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Jikan by ID: ${e.message}")
            null
        }
    }

    private fun parseJikanAnime(data: JsonObject): JikanAnime {
        // Jikan v4 returns "titles" as a JSON array of {type, title} objects (B8)
        val titlesArray = data.getAsJsonArray("titles")
        val titleEnglish = titlesArray?.firstOrNull {
            it.asJsonObject.get("type")?.asString == "English"
        }?.asJsonObject?.get("title")?.asString
        val titleJapanese = titlesArray?.firstOrNull {
            it.asJsonObject.get("type")?.asString == "Japanese"
        }?.asJsonObject?.get("title")?.asString

        val images = data.getAsJsonObject("images")
        val jpg = images?.getAsJsonObject("jpg")
        val webp = images?.getAsJsonObject("webp")

        return JikanAnime(
            malId = data.get("mal_id")?.asInt ?: 0,
            title = data.get("title")?.asString ?: "",
            titleEnglish = titleEnglish,
            titleJapanese = titleJapanese,
            imageUrl = jpg?.get("large_image_url")?.asString ?: jpg?.get("image_url")?.asString,
            webpUrl = webp?.get("large_image_url")?.asString ?: webp?.get("image_url")?.asString,
            year = data.get("year")?.asInt,
            season = data.get("season")?.asString,
            type = data.get("type")?.asString,
            episodes = data.get("episodes")?.asInt
        )
    }
}

data class JikanAnime(
    val malId: Int,
    val title: String,
    val titleEnglish: String?,
    val titleJapanese: String?,
    val imageUrl: String?,
    val webpUrl: String?,
    val year: Int?,
    val season: String?,
    val type: String?,
    val episodes: Int?
)
