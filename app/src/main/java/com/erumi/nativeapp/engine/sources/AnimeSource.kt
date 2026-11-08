package com.erumi.nativeapp.engine.sources

import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.Episode
import com.erumi.nativeapp.domain.model.StreamSource

interface AnimeSource {
    val name: String
    suspend fun search(query: String): List<Anime>
    suspend fun getLatest(limit: Int = 18): List<Anime>
    suspend fun getPopular(limit: Int = 40): List<Anime>
    suspend fun browse(genre: String? = null, page: Int = 1, limit: Int = 30): List<Anime>
    suspend fun getEpisodes(animeTitle: String, showId: String? = null): List<Episode>
    suspend fun resolveStream(animeTitle: String, episodeNumber: Int, audio: String = "sub"): StreamSource?
}
