package com.erumi.nativeapp.core.di

import android.content.Context
import com.erumi.nativeapp.core.datastore.UserPreferencesDataStore
import com.erumi.nativeapp.data.repository.AnimeRepository
import com.erumi.nativeapp.data.repository.UserLibraryRepository
import com.erumi.nativeapp.engine.resolver.EpisodeResolver
import com.erumi.nativeapp.engine.sources.AllAnimeSource
import com.erumi.nativeapp.engine.sources.AniListSource
import com.erumi.nativeapp.manga.data.local.MangaPreferencesDataStore
import com.erumi.nativeapp.manga.data.repository.MangaRepository
import com.erumi.nativeapp.manga.data.sources.MangaSourceManager

/**
 * Lightweight application-scoped dependency container so repositories and
 * in-memory caches (e.g. [EpisodeResolver]) are shared across screens.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val allAnimeSource: AllAnimeSource by lazy { AllAnimeSource() }
    val aniListSource: AniListSource by lazy { AniListSource() }
    val episodeResolver: EpisodeResolver by lazy {
        EpisodeResolver(
            aniListSource = aniListSource,
            allAnimeSource = allAnimeSource
        )
    }

    val userPreferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(appContext)
    }

    val animeRepository: AnimeRepository by lazy {
        AnimeRepository(
            context = appContext,
            allAnimeSource = allAnimeSource,
            aniListSource = aniListSource,
            episodeResolver = episodeResolver
        )
    }

    val userLibraryRepository: UserLibraryRepository by lazy {
        UserLibraryRepository(appContext)
    }

    val mangaSourceManager: MangaSourceManager by lazy {
        MangaSourceManager()
    }

    val mangaPreferencesDataStore: MangaPreferencesDataStore by lazy {
        MangaPreferencesDataStore(appContext)
    }

    val mangaRepository: MangaRepository by lazy {
        MangaRepository(
            context = appContext,
            sourceManager = mangaSourceManager,
            preferences = mangaPreferencesDataStore
        )
    }
}
