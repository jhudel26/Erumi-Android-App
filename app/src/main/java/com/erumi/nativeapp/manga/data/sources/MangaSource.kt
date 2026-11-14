package com.erumi.nativeapp.manga.data.sources

import com.erumi.nativeapp.manga.domain.models.MangaChapter
import com.erumi.nativeapp.manga.domain.models.MangaDetails
import com.erumi.nativeapp.manga.domain.models.MangaPage
import com.erumi.nativeapp.manga.domain.models.MangaPagedResult
import com.erumi.nativeapp.manga.domain.models.MangaResult
import com.erumi.nativeapp.manga.domain.models.MangaSortOption
import com.erumi.nativeapp.manga.domain.models.MangaSourceInfo

interface MangaSource {
    val info: MangaSourceInfo

    suspend fun getPopular(page: Int): MangaResult<MangaPagedResult>
    suspend fun getLatest(page: Int): MangaResult<MangaPagedResult>
    suspend fun search(query: String, page: Int): MangaResult<MangaPagedResult>
    suspend fun getByGenre(genre: String, page: Int): MangaResult<MangaPagedResult>
    suspend fun browse(
        page: Int,
        sort: MangaSortOption = MangaSortOption.LATEST_UPDATES,
        genre: String? = null,
        status: String? = null
    ): MangaResult<MangaPagedResult>

    suspend fun getDetails(mangaUrl: String): MangaResult<MangaDetails>
    suspend fun getChapters(mangaUrl: String): MangaResult<List<MangaChapter>>
    suspend fun getChapterPages(chapterUrl: String): MangaResult<List<MangaPage>>

    fun supportedGenres(): List<String> = emptyList()
}
