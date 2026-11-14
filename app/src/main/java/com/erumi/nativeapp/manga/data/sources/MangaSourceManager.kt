package com.erumi.nativeapp.manga.data.sources

import com.erumi.nativeapp.manga.domain.models.MangaSourceInfo
import java.util.Locale

class MangaSourceManager(
    initialSources: List<MangaSource> = listOf(
        ManhuaPlusSource(),
        ManganatoSource(),
        AsuraScansSource(),
        FlameComicsSource(),
        ManhuaScanSource()
    )
) {
    private val sources = LinkedHashMap<String, MangaSource>()

    @Volatile
    private var activeSourceId: String = ManhuaPlusSource.ID

    init {
        initialSources.forEach { register(it) }
        activeSourceId = sources.keys.firstOrNull() ?: ManhuaPlusSource.ID
    }

    fun register(source: MangaSource) {
        sources.putIfAbsent(source.info.id, source)
    }

    fun getAll(): List<MangaSourceInfo> = sources.values.map { it.info }

    fun get(sourceId: String): MangaSource? = sources[sourceId]

    fun require(sourceId: String): MangaSource =
        sources[sourceId] ?: error("Manga source not registered: $sourceId")

    fun getActive(): MangaSource = require(activeSourceId)

    fun setActive(sourceId: String) {
        require(sourceId)
        activeSourceId = sourceId
    }

    fun isRegistered(sourceId: String): Boolean = sources.containsKey(sourceId)

    fun findSourceForUrl(url: String): MangaSource? {
        val lower = url.lowercase(Locale.US)
        return sources.values.firstOrNull { source ->
            val base = source.info.baseUrl.lowercase(Locale.US)
                .removePrefix("https://").removePrefix("http://").trimEnd('/')
            lower.contains(base) ||
                (source.info.id == ManganatoSource.ID && ("manganato" in lower || "manganelo" in lower || "natomanga" in lower || "manganami" in lower || "nelomanga" in lower)) ||
                (source.info.id == AsuraScansSource.ID && ("asurascans" in lower || "asuracomic" in lower || "asura" in lower)) ||
                (source.info.id == FlameComicsSource.ID && ("flamecomics" in lower || "flamescans" in lower)) ||
                (source.info.id == ManhuaScanSource.ID && ("manhuascan" in lower || "manhuaus" in lower)) ||
                (source.info.id == ManhuaPlusSource.ID && ("manhuaplus" in lower))
        }
    }

    fun findSourceForMangaId(mangaId: String): MangaSource? {
        val prefix = mangaId.substringBefore(':')
        return sources[prefix]
    }
}
