package com.erumi.nativeapp.manga.data.sources

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Dedicated HTTP client for Manga sources.
 * Intentionally separate from Anime [com.erumi.nativeapp.core.network.ApiClient]
 * so stream referer interceptors never affect Manga traffic.
 */
object MangaHttpClient {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.179 Mobile Safari/537.36"

    /**
     * Resolves the required `Referer` header for manga CDN/image URLs so Coil and
     * offline chapter downloads share one source of truth.
     */
    fun refererForImageUrl(url: String): String? {
        val lower = url.lowercase()
        return when {
            "2xstorage" in lower || "manganato" in lower || "manganelo" in lower ||
                "readmanganato" in lower || "natomanga" in lower || "nelomanga" in lower -> "https://www.manganato.gg/"
            "manhuaplus" in lower -> "https://manhuaplus.org/"
            "asura" in lower || "asuracomic" in lower -> "https://asuracomic.net/"
            "flamecomics" in lower || "flamescans" in lower -> "https://flamecomics.xyz/"
            "manhuascan" in lower || "manhuaus" in lower -> "https://manhuascan.io/"
            "elftoon" in lower -> "https://elftoon.net/"
            else -> null
        }
    }

    val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/json")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()
    }
}
