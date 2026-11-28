package com.erumi.nativeapp.core.network

import com.erumi.nativeapp.core.common.Constants
import com.erumi.nativeapp.manga.data.sources.MangaHttpClient
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

object ApiClient {
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.toString()
                val originalRef = original.header("Referer")
                val customRef = original.header("X-Custom-Referer")

                val mangaReferer = MangaHttpClient.refererForImageUrl(url)

                val resolvedRef = when {
                    !originalRef.isNullOrBlank() -> originalRef
                    !customRef.isNullOrBlank() -> customRef
                    mangaReferer != null -> mangaReferer
                    else -> StreamRefererRegistry.getReferer(url)
                }

                val reqBuilder = original.newBuilder()
                    .header("User-Agent", Constants.USER_AGENT)

                if (resolvedRef != null) {
                    val origin = try {
                        val u = URI(resolvedRef)
                        "${u.scheme}://${u.host}"
                    } catch (e: Exception) {
                        "https://megacloud.tv"
                    }
                    reqBuilder
                        .header("Referer", resolvedRef)
                        .header("Origin", origin)

                    if (mangaReferer == null) {
                        reqBuilder
                            .header("Sec-Fetch-Dest", "empty")
                            .header("Sec-Fetch-Mode", "cors")
                            .header("Sec-Fetch-Site", "cross-site")
                    }
                }

                var response = chain.proceed(reqBuilder.build())
                var retryCount = 0

                // E3: Exponential backoff retry interceptor for 429 Too Many Requests & 503 Service Unavailable
                while ((response.code == 429 || response.code == 503) && retryCount < 2) {
                    retryCount++
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull() ?: retryCount.toLong()
                    response.close()
                    val backoffMs = if (retryAfterSec > 60) 2000L else (retryAfterSec * 1000L).coerceAtLeast(1000L)
                    try {
                        Thread.sleep(backoffMs)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    response = chain.proceed(reqBuilder.build())
                }

                response
            }
            .build()
    }
}