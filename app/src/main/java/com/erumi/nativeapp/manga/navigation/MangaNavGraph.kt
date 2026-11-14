package com.erumi.nativeapp.manga.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.erumi.nativeapp.manga.data.local.ErumiExperience
import com.erumi.nativeapp.manga.presentation.details.MangaDetailsScreen
import com.erumi.nativeapp.manga.presentation.discover.MangaDiscoverScreen
import com.erumi.nativeapp.manga.presentation.downloads.MangaDownloadsScreen
import com.erumi.nativeapp.manga.presentation.history.MangaHistoryScreen
import com.erumi.nativeapp.manga.presentation.home.MangaHomeScreen
import com.erumi.nativeapp.manga.presentation.library.MangaLibraryScreen
import com.erumi.nativeapp.manga.presentation.reader.MangaReaderScreen
import com.erumi.nativeapp.manga.presentation.reader.decodeReaderArgs
import com.erumi.nativeapp.manga.presentation.search.MangaSearchScreen
import com.erumi.nativeapp.manga.presentation.settings.MangaSettingsScreen
import com.erumi.nativeapp.shared.navigation.BaseNavGraph
import com.erumi.nativeapp.shared.ui.tour.ErumiTourOverlay
import com.erumi.nativeapp.shared.ui.tour.LocalErumiTourState
import com.erumi.nativeapp.shared.ui.tour.rememberErumiTourState


@Composable
fun MangaNavGraph(
    onSwitchExperience: () -> Unit = {},
    startTourOnLaunch: Boolean = false,
    onTourCompleted: () -> Unit = {}
) {
    val navController = rememberNavController()
    val tourState = rememberErumiTourState(ErumiExperience.MANGA, onCompleted = onTourCompleted)

    LaunchedEffect(startTourOnLaunch) {
        if (startTourOnLaunch && !tourState.isActive) {
            tourState.startTour(navController)
        }
    }

    CompositionLocalProvider(LocalErumiTourState provides tourState) {
        Box(modifier = Modifier.fillMaxSize()) {
            BaseNavGraph(
                bottomNavItems = MangaScreen.bottomNavItems,
                navController = navController,
                hideBottomBarRoutes = listOf("manga_reader")
            ) { innerPadding, _, currentRoute ->
        val isReaderScreen = currentRoute?.startsWith("manga_reader") == true
        val mangaBottomNavRoutes = remember { MangaScreen.bottomNavItems.map { it.route }.toSet() }

        NavHost(
            navController = navController,
            startDestination = MangaScreen.Home.route,
            modifier = Modifier.padding(bottom = if (isReaderScreen) 0.dp else innerPadding.calculateBottomPadding()),
            enterTransition = {
                val fromBottomNav = initialState.destination.route in mangaBottomNavRoutes
                val toBottomNav = targetState.destination.route in mangaBottomNavRoutes
                if (fromBottomNav && toBottomNav) {
                    fadeIn(animationSpec = tween(200))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(280))
                }
            },
            exitTransition = {
                val fromBottomNav = initialState.destination.route in mangaBottomNavRoutes
                val toBottomNav = targetState.destination.route in mangaBottomNavRoutes
                if (fromBottomNav && toBottomNav) {
                    fadeOut(animationSpec = tween(200))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth / 4 },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(200))
                }
            },
            popEnterTransition = {
                val fromBottomNav = initialState.destination.route in mangaBottomNavRoutes
                val toBottomNav = targetState.destination.route in mangaBottomNavRoutes
                if (fromBottomNav && toBottomNav) {
                    fadeIn(animationSpec = tween(200))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 4 },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                }
            },
            popExitTransition = {
                val fromBottomNav = initialState.destination.route in mangaBottomNavRoutes
                val toBottomNav = targetState.destination.route in mangaBottomNavRoutes
                if (fromBottomNav && toBottomNav) {
                    fadeOut(animationSpec = tween(200))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(280))
                }
            }
        ) {
            // Home
            composable(MangaScreen.Home.route) {
                MangaHomeScreen(
                    onMangaClick = { manga ->
                        navController.navigate(MangaScreen.Details.createRoute(manga.url))
                    },
                    onContinueReading = { progress ->
                        val chapterId = progress.chapterId
                        val chapterUrl = progress.chapterUrl
                        navController.navigate(
                            MangaScreen.Reader.createRoute(
                                mangaUrl = progress.mangaId,
                                chapterUrl = chapterUrl,
                                mangaId = progress.mangaId,
                                title = progress.mangaTitle,
                                cover = progress.coverUrl,
                                chapterId = chapterId,
                                chapterName = progress.chapterName,
                                chapterNumber = progress.chapterNumber
                            )
                        )
                    },
                    onLibraryClick = { item ->
                        navController.navigate(MangaScreen.Details.createRoute(item.mangaId))
                    },
                    onSwitchExperience = onSwitchExperience,
                    onSearchClick = {
                        navController.navigate(MangaScreen.Search.route)
                    },
                    onSeeAllContinueReading = {
                        navController.navigate(MangaScreen.History.route)
                    },
                    onGoToDownloads = {
                        navController.navigate(MangaScreen.Downloads.route)
                    }
                )
            }

            // Discover
            composable(MangaScreen.Discover.route) {
                MangaDiscoverScreen(
                    onMangaClick = { manga ->
                        navController.navigate(MangaScreen.Details.createRoute(manga.url))
                    }
                )
            }

            // Search
            composable(MangaScreen.Search.route) {
                MangaSearchScreen(
                    onMangaClick = { manga ->
                        navController.navigate(MangaScreen.Details.createRoute(manga.url))
                    }
                )
            }

            // Library
            composable(MangaScreen.Library.route) {
                MangaLibraryScreen(
                    onOpenManga = { mangaId, _ ->
                        navController.navigate(MangaScreen.Details.createRoute(mangaId))
                    }
                )
            }

            // History
            composable(MangaScreen.History.route) {
                MangaHistoryScreen(
                    onOpenManga = { entry ->
                        navController.navigate(MangaScreen.Details.createRoute(entry.mangaId))
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Downloads
            composable(MangaScreen.Downloads.route) {
                MangaDownloadsScreen(
                    onMangaClick = { mangaId ->
                        navController.navigate(MangaScreen.Details.createRoute(mangaId))
                    }
                )
            }

            // Settings
            composable(MangaScreen.Settings.route) {
                MangaSettingsScreen(
                    onSwitchExperience = onSwitchExperience,
                    onBack = { navController.popBackStack() },
                    onReplayTour = {
                        tourState.startTour(navController)
                    }
                )
            }

            // Details
            composable(
                route = MangaScreen.Details.route,
                arguments = listOf(navArgument("mangaUrl") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("mangaUrl").orEmpty()
                val decodedUrl = try {
                    java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                } catch (_: Exception) {
                    encodedUrl
                }

                MangaDetailsScreen(
                    mangaUrlEncoded = decodedUrl,
                    onBack = { navController.popBackStack() },
                    onReadChapter = { _, chapterUrl, mangaId, title, cover, chapterId, chapterName, chapterNumber ->
                        navController.navigate(
                            MangaScreen.Reader.createRoute(
                                mangaUrl = decodedUrl,
                                chapterUrl = chapterUrl,
                                mangaId = mangaId,
                                title = title,
                                cover = cover,
                                chapterId = chapterId,
                                chapterName = chapterName,
                                chapterNumber = chapterNumber
                            )
                        )
                    }
                )
            }

            // Reader
            composable(
                route = MangaScreen.Reader.route,
                arguments = listOf(
                    navArgument("mangaUrl") { type = NavType.StringType },
                    navArgument("chapterUrl") { type = NavType.StringType },
                    navArgument("mangaId") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("cover") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                    navArgument("chapterName") { type = NavType.StringType },
                    navArgument("chapterNumber") { type = NavType.FloatType }
                )
            ) { backStackEntry ->
                val mangaUrl = backStackEntry.arguments?.getString("mangaUrl").orEmpty()
                val chapterUrl = backStackEntry.arguments?.getString("chapterUrl").orEmpty()
                val mangaId = backStackEntry.arguments?.getString("mangaId").orEmpty()
                val title = backStackEntry.arguments?.getString("title").orEmpty()
                val cover = backStackEntry.arguments?.getString("cover").orEmpty()
                val chapterId = backStackEntry.arguments?.getString("chapterId").orEmpty()
                val chapterName = backStackEntry.arguments?.getString("chapterName").orEmpty()
                val chapterNumber = backStackEntry.arguments?.getFloat("chapterNumber")

                val args = decodeReaderArgs(
                    mangaUrl = mangaUrl,
                    chapterUrl = chapterUrl,
                    mangaId = mangaId,
                    title = title,
                    cover = cover,
                    chapterId = chapterId,
                    chapterName = chapterName,
                    chapterNumber = chapterNumber?.toString() ?: ""
                )

                MangaReaderScreen(
                    argsEncoded = args,
                    onBack = {
                        val popped = navController.popBackStack(MangaScreen.Details.route, inclusive = false)
                        if (!popped) {
                            val targetManga = if (args.mangaUrl.isNotBlank()) args.mangaUrl else args.mangaId
                            navController.navigate(MangaScreen.Details.createRoute(targetManga)) {
                                popUpTo(MangaScreen.Reader.route) { inclusive = true }
                            }
                        }
                    },
                    onOpenChapter = { newArgs ->
                        navController.navigate(
                            MangaScreen.Reader.createRoute(
                                mangaUrl = newArgs.mangaUrl,
                                chapterUrl = newArgs.chapterUrl,
                                mangaId = newArgs.mangaId,
                                title = newArgs.title,
                                cover = newArgs.coverUrl,
                                chapterId = newArgs.chapterId,
                                chapterName = newArgs.chapterName,
                                chapterNumber = newArgs.chapterNumber
                            )
                        ) {
                            popUpTo(MangaScreen.Reader.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }

    // Spotlight Guided Tour Overlay
    if (tourState.isActive) {
        ErumiTourOverlay(
            tourState = tourState,
            navController = navController
        )
    }
}
}
}
