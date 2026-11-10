package com.erumi.nativeapp.data.repository

import android.content.Context
import com.erumi.nativeapp.core.common.Resource
import com.erumi.nativeapp.core.image.ImageResolver
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.Episode
import com.erumi.nativeapp.domain.model.StreamSource
import com.erumi.nativeapp.engine.resolver.EpisodeResolver
import com.erumi.nativeapp.engine.sources.AllAnimeSource
import com.erumi.nativeapp.engine.sources.AniListSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AnimeRepository(
    private val context: Context,
    private val allAnimeSource: AllAnimeSource = AllAnimeSource(),
    private val aniListSource: AniListSource = AniListSource(),
    private val episodeResolver: EpisodeResolver = EpisodeResolver()
) {

    private val imageResolver: ImageResolver? = try {
        ImageResolver(context)
    } catch (e: Exception) {
        android.util.Log.e("AnimeRepository", "Failed to initialize ImageResolver: ${e.message}", e)
        null
    }

    @Volatile private var cachedTrending: List<Anime>? = null
    @Volatile private var cachedLatest: List<Anime>? = null
    @Volatile private var cachedPopular: List<Anime>? = null

    fun getTrendingFlow(limit: Int = 18): Flow<Resource<List<Anime>>> = flow {
        emit(Resource.Loading(cachedTrending))
        cachedTrending?.let {
            if (it.isNotEmpty()) emit(Resource.Success(it))
        }
        try {
            var res = allAnimeSource.getPopular(limit)
            if (res.isEmpty()) {
                res = aniListSource.getLatest(limit)
            }
            if (res.isNotEmpty()) {
                cachedTrending = res
                emit(Resource.Success(res))
            } else if (cachedTrending == null) {
                emit(Resource.Error("Could not fetch trending anime."))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Failed to load trending anime.", cachedTrending))
        }
    }.flowOn(Dispatchers.IO)

    fun getLatestFlow(limit: Int = 20): Flow<Resource<List<Anime>>> = flow {
        emit(Resource.Loading(cachedLatest))
        cachedLatest?.let {
            if (it.isNotEmpty()) emit(Resource.Success(it))
        }
        try {
            var res = allAnimeSource.getLatest(limit)
            if (res.isEmpty()) {
                res = aniListSource.getLatest(limit)
            }
            if (res.isNotEmpty()) {
                cachedLatest = res
                emit(Resource.Success(res))
            } else if (cachedLatest == null) {
                emit(Resource.Error("Could not fetch latest releases."))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Failed to load latest anime.", cachedLatest))
        }
    }.flowOn(Dispatchers.IO)

    fun getPopularFlow(limit: Int = 40): Flow<Resource<List<Anime>>> = flow {
        emit(Resource.Loading(cachedPopular))
        cachedPopular?.let {
            if (it.isNotEmpty()) emit(Resource.Success(it))
        }
        try {
            var res = allAnimeSource.getPopular(limit)
            if (res.isEmpty()) {
                res = aniListSource.getPopular(limit)
            }
            if (res.isNotEmpty()) {
                cachedPopular = res
                emit(Resource.Success(res))
            } else if (cachedPopular == null) {
                emit(Resource.Error("Could not fetch popular anime."))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Failed to load popular anime.", cachedPopular))
        }
    }.flowOn(Dispatchers.IO)

    fun browseFlow(genre: String? = null, page: Int = 1, limit: Int = 30): Flow<Resource<List<Anime>>> = flow {
        emit(Resource.Loading())
        try {
            var res = allAnimeSource.browse(genre = genre, page = page, limit = limit)
            if (res.isEmpty()) {
                res = aniListSource.browse(genre = genre, page = page, limit = limit)
            }
            emit(Resource.Success(res))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Failed to browse anime."))
        }
    }.flowOn(Dispatchers.IO)

    fun searchFlow(query: String): Flow<Resource<List<Anime>>> = flow {
        emit(Resource.Loading())
        try {
            var res = allAnimeSource.search(query)
            if (res.isEmpty()) {
                res = aniListSource.search(query)
            }
            emit(Resource.Success(res))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Search failed for \"$query\"."))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getAnimeDetails(title: String, year: Int? = null): Anime? = withContext(Dispatchers.IO) {
        aniListSource.fetchDetailedMetadata(title, year)
    }

    suspend fun getEpisodes(animeTitle: String, totalEpisodes: Int? = null): List<Episode> = withContext(Dispatchers.IO) {
        episodeResolver.getEpisodes(animeTitle, totalEpisodes)
    }

    suspend fun resolveStream(
        animeTitle: String,
        episodeNumber: Int,
        audio: String = "sub"
    ): StreamSource? = withContext(Dispatchers.IO) {
        episodeResolver.resolveStream(animeTitle, episodeNumber, audio)
    }
}
