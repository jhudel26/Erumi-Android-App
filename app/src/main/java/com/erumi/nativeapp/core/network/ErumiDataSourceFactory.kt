package com.erumi.nativeapp.core.network

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.erumi.nativeapp.core.common.Constants
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class ErumiDataSourceFactory(
    private val customReferer: String? = null,
    private val customHeaders: Map<String, String> = emptyMap()
) : DataSource.Factory {

    private val client: OkHttpClient = ApiClient.okHttpClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val origRequest = chain.request()
            val url = origRequest.url.toString()

            val resolvedRef = when {
                !customReferer.isNullOrBlank() -> customReferer
                else -> StreamRefererRegistry.getReferer(url) ?: defaultRefererFor(url)
            }

            val origin = try {
                val u = URI(resolvedRef)
                "${u.scheme}://${u.host}"
            } catch (e: Exception) {
                "https://megacloud.tv"
            }

            val reqBuilder = origRequest.newBuilder()
                .header("User-Agent", Constants.USER_AGENT)
                .header("Referer", resolvedRef)
                .header("Origin", origin)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Accept", "*/*")

            customHeaders.forEach { (k, v) ->
                reqBuilder.header(k, v)
            }

            val response = chain.proceed(reqBuilder.build())
            android.util.Log.d(
                "ErumiDS",
                "[${response.code}] ${origRequest.url.host}${origRequest.url.encodedPath.take(60)} ref=$resolvedRef"
            )
            response
        }
        .build()

    private val okHttpDataSourceFactory = OkHttpDataSource.Factory(client)

    override fun createDataSource(): DataSource {
        return DisguiseHeaderDataSource(okHttpDataSourceFactory.createDataSource())
    }

    companion object {
        fun defaultRefererFor(url: String): String {
            return try {
                val host = URI(url).host?.lowercase() ?: ""
                when {
                    "dramahot" in host || "drama1" in host || "zokoanime" in host -> "https://zokoanime.video/"
                    "megaplay.buzz" in host -> "https://megaplay.buzz/"
                    "rapid-cloud" in host || "rapidcloud" in host -> "https://rapid-cloud.co/"
                    "rabbitstream" in host -> "https://rabbitstream.net/"
                    "megacloud" in host -> "https://megacloud.tv/"
                    "dokicloud" in host -> "https://dokicloud.one/"
                    "sharecloud" in host -> "https://sharecloud.online/"
                    "streamtape" in host -> "https://streamtape.com/"
                    "mp4upload" in host -> "https://www.mp4upload.com/"
                    "vivibebe.site" in host || "ibyteimg.com" in host -> "https://vivibebe.site/"
                    "anidb.app" in host -> "https://anidb.app/"
                    "anineko" in host -> "https://anineko.to/"
                    "allmanga" in host -> "https://allmanga.to/"
                    "hianime" in host -> "https://hianime.to/"
                    "krussdomi" in host -> "https://krussdomi.com/"
                    else -> StreamRefererRegistry.getReferer(url) ?: "https://megacloud.tv/"
                }
            } catch (e: Exception) {
                StreamRefererRegistry.getReferer(url) ?: "https://megacloud.tv/"
            }
        }
    }
}
