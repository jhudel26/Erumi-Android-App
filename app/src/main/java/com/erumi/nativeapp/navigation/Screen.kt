package com.erumi.nativeapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.erumi.nativeapp.shared.navigation.BaseScreen

sealed class Screen(
    override val route: String,
    override val title: String,
    override val icon: ImageVector? = null
) : BaseScreen(route, title, icon) {
    object ExperienceSelector : Screen("experience_selector", "Select Experience", null)
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Browse : Screen("browse", "Browse", Icons.Default.Tv)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Watchlist : Screen("watchlist", "Watchlist", Icons.Default.Bookmark)
    object Downloads : Screen("downloads", "Downloads", Icons.Default.Download)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object History : Screen("history", "History", Icons.Default.History)

    object Detail : Screen("detail/{animeTitle}", "Detail") {
        fun createRoute(animeTitle: String): String {
            val encoded = java.net.URLEncoder.encode(animeTitle, "UTF-8")
            return "detail/$encoded"
        }
    }

    object DownloadedDetail : Screen("downloaded_detail/{animeTitle}", "Downloaded Detail") {
        fun createRoute(animeTitle: String): String {
            val encoded = android.util.Base64.encodeToString(
                animeTitle.toByteArray(Charsets.UTF_8),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            )
            return "downloaded_detail/$encoded"
        }
    }

    object Player : Screen("player/{animeTitle}/{episodeNumber}?resumeTime={resumeTime}&animeId={animeId}&poster={poster}", "Player") {
        fun createRoute(
            animeTitle: String,
            episodeNumber: Int,
            resumeTime: Long = 0L,
            animeId: String? = null,
            poster: String? = null
        ): String {
            val encodedTitle = java.net.URLEncoder.encode(animeTitle, "UTF-8")
            val encodedId = java.net.URLEncoder.encode(animeId.orEmpty(), "UTF-8")
            val encodedPoster = java.net.URLEncoder.encode(poster.orEmpty(), "UTF-8")
            return "player/$encodedTitle/$episodeNumber?resumeTime=$resumeTime&animeId=$encodedId&poster=$encodedPoster"
        }
    }

    companion object {
        val bottomNavItems = listOf(Home, Browse, Watchlist, Downloads, Settings)
    }
}
