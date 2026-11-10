package com.erumi.nativeapp.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.erumi.nativeapp.features.anime_details.DetailScreen
import com.erumi.nativeapp.features.anime_details.DetailViewModel
import com.erumi.nativeapp.features.downloads.DownloadedDetailScreen
import com.erumi.nativeapp.features.downloads.DownloadsScreen
import com.erumi.nativeapp.features.history.HistoryScreen
import com.erumi.nativeapp.features.home.HomeScreen
import com.erumi.nativeapp.features.player.PlayerScreen
import com.erumi.nativeapp.features.player.PlayerViewModel
import com.erumi.nativeapp.features.search.SearchScreen
import com.erumi.nativeapp.features.settings.SettingsScreen
import com.erumi.nativeapp.features.settings.SettingsViewModel
import com.erumi.nativeapp.features.watchlist.WatchlistScreen
import com.erumi.nativeapp.ui.theme.BgCard
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiSurface
import com.erumi.nativeapp.ui.theme.TealPrimary
import com.erumi.nativeapp.ui.theme.TextSecondary
import android.content.Intent

@Composable
fun ErumiNavGraph(
    onRequestBatteryOptimization: (Intent) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isPlayerScreen = currentRoute?.startsWith("player") == true

    Scaffold(
        containerColor = ErumiBackground,
        bottomBar = {
            AnimatedVisibility(
                visible = !isPlayerScreen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar(
                    containerColor = ErumiSurface,
                    tonalElevation = 8.dp
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                screen.icon?.let {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = screen.title
                                    )
                                }
                            },
                            label = { Text(text = screen.title) },
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealPrimary,
                                selectedTextColor = TealPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = BgCard
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(if (isPlayerScreen) PaddingValues(0.dp) else innerPadding)
        ) {
            // Home
            composable(Screen.Home.route) {
                HomeScreen(
                    onAnimeClick = { anime ->
                        navController.navigate(Screen.Detail.createRoute(anime.title))
                    },
                    onResumeWatch = { progress ->
                        navController.navigate(Screen.Player.createRoute(progress.animeTitle, progress.episodeNumber, progress.positionMs, progress.animeId, progress.poster))
                    },
                    onHistoryClick = {
                        navController.navigate(Screen.History.route)
                    },
                    onGoToDownloads = {
                        navController.navigate(Screen.Downloads.route)
                    }
                )
            }

            // Search
            composable(Screen.Search.route) {
                SearchScreen(
                    onAnimeClick = { anime ->
                        navController.navigate(Screen.Detail.createRoute(anime.title))
                    }
                )
            }

            // Watchlist
            composable(Screen.Watchlist.route) {
                WatchlistScreen(
                    onAnimeClick = { anime ->
                        navController.navigate(Screen.Detail.createRoute(anime.title))
                    }
                )
            }

            // History
            composable(Screen.History.route) {
                HistoryScreen(
                    onBackClick = { navController.popBackStack() },
                    onResumeWatch = { progress ->
                        navController.navigate(Screen.Player.createRoute(progress.animeTitle, progress.episodeNumber, progress.positionMs, progress.animeId, progress.poster))
                    }
                )
            }

            // Downloads
            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onAnimeClick = { animeTitle ->
                        navController.navigate(Screen.DownloadedDetail.createRoute(animeTitle))
                    }
                )
            }

            // Downloaded Anime Detail (Offline Only Episodes)
            composable(
                route = Screen.DownloadedDetail.route,
                arguments = listOf(navArgument("animeTitle") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedTitle = backStackEntry.arguments?.getString("animeTitle").orEmpty()
                val decodedTitle = try {
                    val bytes = android.util.Base64.decode(encodedTitle, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                    String(bytes, Charsets.UTF_8)
                } catch (_: Exception) {
                    try {
                        java.net.URLDecoder.decode(encodedTitle, "UTF-8")
                    } catch (_: Exception) {
                        encodedTitle
                    }
                }

                DownloadedDetailScreen(
                    animeTitle = decodedTitle,
                    onBackClick = { navController.popBackStack() },
                    onPlayEpisode = { title, ep, resumeMs, animeId, poster ->
                        navController.navigate(Screen.Player.createRoute(title, ep, resumeMs, animeId, poster))
                    },
                    onViewOnlineDetails = { title ->
                        navController.navigate(Screen.Detail.createRoute(title))
                    }
                )
            }

            // Settings
            composable(Screen.Settings.route) {
                val context = LocalContext.current
                val app = context.applicationContext as android.app.Application
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return SettingsViewModel(app) as T
                        }
                    }
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onRequestBatteryOptimization = onRequestBatteryOptimization
                )
            }

            // Anime Detail
            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("animeTitle") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedTitle = backStackEntry.arguments?.getString("animeTitle").orEmpty()
                val decodedTitle = runCatching { java.net.URLDecoder.decode(encodedTitle, "UTF-8") }.getOrDefault(encodedTitle)
                val context = LocalContext.current
                val app = context.applicationContext as android.app.Application
                val detailViewModel: DetailViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    key = "detail_$decodedTitle",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return DetailViewModel(app, decodedTitle) as T
                        }
                    }
                )

                DetailScreen(
                    animeTitle = decodedTitle,
                    onBackClick = { navController.popBackStack() },
                    onPlayEpisode = { title, ep, resumeMs, animeId, poster ->
                        navController.navigate(Screen.Player.createRoute(title, ep, resumeMs, animeId, poster))
                    },
                    onRelationClick = { relationTitle ->
                        navController.navigate(Screen.Detail.createRoute(relationTitle))
                    },
                    viewModel = detailViewModel
                )
            }

            // Video Player
            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("animeTitle") { type = NavType.StringType },
                    navArgument("episodeNumber") { type = NavType.IntType },
                    navArgument("resumeTime") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("animeId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("poster") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val encodedTitle = backStackEntry.arguments?.getString("animeTitle").orEmpty()
                val decodedTitle = runCatching { java.net.URLDecoder.decode(encodedTitle, "UTF-8") }.getOrDefault(encodedTitle)
                val episodeNumber = backStackEntry.arguments?.getInt("episodeNumber") ?: 1
                val resumeTime = backStackEntry.arguments?.getLong("resumeTime") ?: 0L
                val encodedId = backStackEntry.arguments?.getString("animeId").orEmpty()
                val decodedId = if (encodedId.isNotBlank()) java.net.URLDecoder.decode(encodedId, "UTF-8") else null
                val encodedPoster = backStackEntry.arguments?.getString("poster").orEmpty()
                val decodedPoster = if (encodedPoster.isNotBlank()) java.net.URLDecoder.decode(encodedPoster, "UTF-8") else null

                val context = LocalContext.current
                val app = context.applicationContext as android.app.Application
                val playerViewModel: PlayerViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    key = "player_${decodedTitle}_${episodeNumber}_${decodedId.orEmpty()}",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return PlayerViewModel(app, decodedTitle, episodeNumber, resumeTime, decodedId, decodedPoster) as T
                        }
                    }
                )

                PlayerScreen(
                    onBackClick = { navController.popBackStack() },
                    viewModel = playerViewModel
                )
            }
        }
    }
}
