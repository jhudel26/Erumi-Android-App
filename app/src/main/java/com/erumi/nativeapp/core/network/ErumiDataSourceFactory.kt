package com.erumi.nativeapp.core.network

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.erumi.nativeapp.core.common.Constants
import java.net.URI

@OptIn(UnstableApi::class)
class ErumiDataSourceFactory(
    private val customReferer: String? = null,
    private val customHeaders: Map<String, String> = emptyMap()
) : DataSource.Factory {

    /**
     * Build a per-factory OkHttpClient that injects Referer/Origin/Sec-Fetch headers
     * on EVERY request (manifest + every HLS segment).
     *
     * We cannot rely on setDefaultRequestProperties() alone because Media3's
     * SegmentDownloader fires individual DataSpec requests per segment and those
     * DataSpec-level headers are NOT forwarded as OkHttp request headers that
     * ApiClient's interceptor can see. This per-instance interceptor solves that.
     */
    private val client = ApiClient.okHttpClient.newBuilder()
        .addInterceptor { chain ->
            val original = chain.request()
            val url = original.url.toString()

            // Resolve referer: explicit > registry > default-by-host
            val ref = when {
                !customReferer.isNullOrBlank() -> customReferer
                else -> StreamRefererRegistry.getReferer(url)
                    ?: defaultRefererFor(url)
            }

            val origin = try {
                val u = URI(ref)
                "${u.scheme}://${u.host}"
            } catch (e: Exception) {
                "https://megacloud.tv"
            }

            val reqBuilder = original.newBuilder()
                .header("User-Agent", Constants.USER_AGENT)
                .header("Referer", ref)
                .header("Origin", origin)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Accept", "*/*")

            // Apply any extra custom headers (e.g. API keys, auth tokens)
            customHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

            chain.proceed(reqBuilder.build())
        }
        .build()

    override fun createDataSource(): DataSource {
        val okHttpDataSource = OkHttpDataSource.Factory(client)
            .setUserAgent(Constants.USER_AGENT)
            .createDataSource()
        return DisguiseHeaderDataSource(okHttpDataSource)
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
