package com.erumi.nativeapp.core.network

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

object StreamRefererRegistry {
    private val referers = ConcurrentHashMap<String, String>()
    fun register(keyOrUrl: String, referer: String) {
        if (referer.isBlank()) return
        referers[keyOrUrl] = referer
        try {
            val u = URI(keyOrUrl)
            val host = u.host
            if (!host.isNullOrBlank()) {
                referers[host] = referer
                val parts = host.split(".")
                if (parts.size >= 2) {
                    val rootDomain = parts.takeLast(2).joinToString(".")
                    referers[rootDomain] = referer
                }
            }
            val cleanUrl = "${u.scheme}://${u.host}${u.path}"
            referers[cleanUrl] = referer
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun getReferer(url: String, customCacheKey: String? = null): String? {
        if (!customCacheKey.isNullOrBlank() && referers.containsKey(customCacheKey)) {
            return referers[customCacheKey]
        }
        if (referers.containsKey(url)) {
            return referers[url]
        }
        try {
            val u = URI(url)
            val cleanUrl = "${u.scheme}://${u.host}${u.path}"
            if (referers.containsKey(cleanUrl)) {
                return referers[cleanUrl]
            }
            val host = u.host
            if (!host.isNullOrBlank()) {
                if (referers.containsKey(host)) {
                    return referers[host]
                }
                val parts = host.split(".")
                if (parts.size >= 2) {
                    val rootDomain = parts.takeLast(2).joinToString(".")
                    if (referers.containsKey(rootDomain)) {
                        return referers[rootDomain]
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
        return null
    }
}
