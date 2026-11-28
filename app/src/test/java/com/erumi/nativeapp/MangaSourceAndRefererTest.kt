package com.erumi.nativeapp

import com.erumi.nativeapp.manga.data.sources.AsuraScansSource
import com.erumi.nativeapp.manga.data.sources.ElfToonSource
import com.erumi.nativeapp.manga.data.sources.FlameComicsSource
import com.erumi.nativeapp.manga.data.sources.MangaHttpClient
import com.erumi.nativeapp.manga.data.sources.MangaSourceManager
import com.erumi.nativeapp.manga.data.sources.ManganatoSource
import com.erumi.nativeapp.manga.data.sources.ManhuaPlusSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaSourceAndRefererTest {

    @Test
    fun `refererForImageUrl resolves expected referer per CDN`() {
        assertEquals("https://elftoon.net/", MangaHttpClient.refererForImageUrl("https://cdn.elftoon.net/img/01.jpg"))
        assertEquals("https://www.manganato.gg/", MangaHttpClient.refererForImageUrl("https://s1.nelomanga.com/ch1/1.webp"))
        assertEquals("https://asuracomic.net/", MangaHttpClient.refererForImageUrl("https://gg.asuracomic.net/storage/media/1.webp"))
        assertEquals("https://flamecomics.xyz/", MangaHttpClient.refererForImageUrl("https://cdn.flamecomics.xyz/uploads/1.png"))
        assertEquals("https://manhuascan.io/", MangaHttpClient.refererForImageUrl("https://img.manhuaus.com/ch/1.jpg"))
        assertEquals("https://manhuaplus.org/", MangaHttpClient.refererForImageUrl("https://manhuaplus.org/wp-content/1.jpg"))
        assertNull(MangaHttpClient.refererForImageUrl("https://example.com/image.jpg"))
    }

    @Test
    fun `MangaSourceManager resolves sources by URL and ID`() {
        val manager = MangaSourceManager()

        assertEquals(ElfToonSource.ID, manager.findSourceForUrl("https://elftoon.net/manga/solo")?.info?.id)
        assertEquals(ManganatoSource.ID, manager.findSourceForUrl("https://www.nelomanga.com/manga-abc")?.info?.id)
        assertEquals(AsuraScansSource.ID, manager.findSourceForUrl("https://asuracomic.net/series/test")?.info?.id)
        assertEquals(FlameComicsSource.ID, manager.findSourceForUrl("https://flamecomics.xyz/series/1")?.info?.id)
        assertEquals(ManhuaPlusSource.ID, manager.findSourceForUrl("https://manhuaplus.org/manga/test")?.info?.id)

        assertEquals(ElfToonSource.ID, manager.findSourceForMangaId("${ElfToonSource.ID}:123")?.info?.id)
        assertNull(manager.findSourceForMangaId("unknown:123"))
    }
}
