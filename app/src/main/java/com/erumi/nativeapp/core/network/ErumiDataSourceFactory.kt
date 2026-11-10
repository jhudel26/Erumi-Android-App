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

    override fun createDataSource(): DataSource {
        val baseFactory = OkHttpDataSource.Factory(ApiClient.okHttpClient)
            .setUserAgent(Constants.USER_AGENT)

        val headers = mutableMapOf<String, String>()
        if (!customReferer.isNullOrBlank()) {
            headers["X-Custom-Referer"] = customReferer
            headers["Referer"] = customReferer
        }
        headers.putAll(customHeaders)

        if (headers.isNotEmpty()) {
            baseFactory.setDefaultRequestProperties(headers)
        }

        val okHttpDataSource = baseFactory.createDataSource()
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
