package com.erumi.nativeapp.manga.data.sources

import com.erumi.nativeapp.manga.domain.models.MangaSourceInfo
import java.util.Locale

class MangaSourceManager(
    initialSources: List<MangaSource> = listOf(
        ManhuaPlusSource(),
        ManganatoSource(),
        AsuraScansSource(),
        FlameComicsSource(),
        ManhuaScanSource(),
        ElfToonSource()
    )
) {
    private val sources = LinkedHashMap<String, MangaSource>()

    @Volatile
    private var activeSourceId: String = AUTO_SOURCE_ID

    init {
        initialSources.forEach { register(it) }
    }

    fun register(source: MangaSource) {
        sources.putIfAbsent(source.info.id, source)
    }

    fun getAll(): List<MangaSourceInfo> = sources.values.map { it.info }

    fun getAllSources(): List<MangaSource> = sources.values.toList()

    fun get(sourceId: String): MangaSource? = sources[sourceId]

    fun require(sourceId: String): MangaSource =
        if (sourceId == AUTO_SOURCE_ID) {
            sources.values.firstOrNull() ?: error("No manga sources registered")
        } else {
            sources[sourceId] ?: error("Manga source not registered: $sourceId")
        }

    fun getActive(): MangaSource = require(activeSourceId)

    fun setActive(sourceId: String) {
        if (sourceId != AUTO_SOURCE_ID) {
            require(sourceId)
        }
        activeSourceId = sourceId
    }

    fun sourcePriority(sourceId: String): Int = when (sourceId) {
        ElfToonSource.ID -> 6
        AsuraScansSource.ID -> 5
        ManhuaPlusSource.ID -> 4
        FlameComicsSource.ID -> 3
        ManganatoSource.ID -> 2
        ManhuaScanSource.ID -> 1
        else -> 0
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
                (source.info.id == ManhuaPlusSource.ID && ("manhuaplus" in lower)) ||
                (source.info.id == ElfToonSource.ID && ("elftoon" in lower))
        }
    }

    fun findSourceForMangaId(mangaId: String): MangaSource? {
        val prefix = mangaId.substringBefore(':')
        return sources[prefix]
    }

    companion object {
        const val AUTO_SOURCE_ID = "auto"
    }
}
