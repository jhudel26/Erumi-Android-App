package com.erumi.nativeapp.manga.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.erumi.nativeapp.shared.navigation.BaseScreen
import java.net.URLEncoder

sealed class MangaScreen(
    override val route: String,
    override val title: String,
    override val icon: ImageVector? = null
) : BaseScreen(route, title, icon) {
    data object Home : MangaScreen("manga_home", "Home", Icons.Default.Home)
    data object Discover : MangaScreen("manga_discover", "Discover", Icons.Default.Explore)
    data object Search : MangaScreen("manga_search", "Search", Icons.Default.Search)
    data object Library : MangaScreen("manga_library", "Library", Icons.Default.LibraryBooks)
    data object History : MangaScreen("manga_history", "History", Icons.Default.History)
    data object Downloads : MangaScreen("manga_downloads", "Downloads", Icons.Default.Download)
    data object Settings : MangaScreen("manga_settings", "Settings", Icons.Default.Settings)

    data object Details : MangaScreen("manga_details/{mangaUrl}", "Details") {
        fun createRoute(mangaUrl: String): String {
            val encoded = URLEncoder.encode(mangaUrl, "UTF-8")
            return "manga_details/$encoded"
        }
    }

    data object Reader : MangaScreen(
        "manga_reader/{mangaUrl}/{chapterUrl}/{mangaId}/{title}/{cover}/{chapterId}/{chapterName}/{chapterNumber}",
        "Reader"
    ) {
        fun createRoute(
            mangaUrl: String,
            chapterUrl: String,
            mangaId: String,
            title: String,
            cover: String?,
            chapterId: String,
            chapterName: String,
            chapterNumber: Float?
        ): String {
            fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
            return "manga_reader/${enc(mangaUrl)}/${enc(chapterUrl)}/${enc(mangaId)}/${enc(title)}/" +
                "${enc(cover.orEmpty())}/${enc(chapterId)}/${enc(chapterName)}/${chapterNumber ?: -1f}"
        }
    }

    companion object {
        val bottomNavItems = listOf(Home, Discover, Library, Downloads, Settings)
    }
}

fun mangaUrlFromId(mangaId: String): String {
    val slug = mangaId.substringAfter(':', mangaId)
    return "https://manhuaplus.org/manga/$slug"
}
