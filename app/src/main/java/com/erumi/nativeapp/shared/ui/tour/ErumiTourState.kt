package com.erumi.nativeapp.shared.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.navigation.NavController
import com.erumi.nativeapp.manga.data.local.ErumiExperience
import com.erumi.nativeapp.manga.navigation.MangaScreen
import com.erumi.nativeapp.navigation.Screen

enum class TourTargetKey {
    HOME_SEARCH,
    HOME_SWITCH,
    // Anime Settings
    ANIME_SETTINGS_PLAYBACK,
    ANIME_SETTINGS_DOWNLOADS,
    ANIME_SETTINGS_EXPERIENCE,
    ANIME_SETTINGS_STORAGE,
    // Manga Settings
    MANGA_SETTINGS_READER,
    MANGA_SETTINGS_SOURCE,
    MANGA_SETTINGS_DOWNLOADS,
    MANGA_SETTINGS_EXPERIENCE,
    MANGA_SETTINGS_STORAGE
}

enum class ErumiTourStep(
    val experience: ErumiExperience,
    val stepIndex: Int,
    val totalSteps: Int,
    val section: String,
    val title: String,
    val description: String,
    val targetKey: TourTargetKey?
) {
    // --- ANIME TOUR ---
    ANIME_WELCOME(
        experience = ErumiExperience.ANIME,
        stepIndex = 0,
        totalSteps = 6,
        section = "Welcome",
        title = "Welcome to Erumi",
        description = "Stream high-definition anime, track your watchlist, and download episodes for offline viewing.\n\nTake a quick guided tour to explore search, experience switching, and key playback settings!",
        targetKey = null
    ),
    ANIME_HOME_SEARCH(
        experience = ErumiExperience.ANIME,
        stepIndex = 1,
        totalSteps = 6,
        section = "Home",
        title = "Search Anime",
        description = "Looking for something specific? Tap the search icon to find anime series, movies, and episodes, or filter by genres.",
        targetKey = TourTargetKey.HOME_SEARCH
    ),
    ANIME_HOME_SWITCH(
        experience = ErumiExperience.ANIME,
        stepIndex = 2,
        totalSteps = 6,
        section = "Home",
        title = "Switch Experience",
        description = "Erumi includes both Anime and Manga! Tap this top switch icon anytime to jump between watching Anime and reading Manga / Manhua.",
        targetKey = TourTargetKey.HOME_SWITCH
    ),
    ANIME_SETTINGS_PLAYBACK(
        experience = ErumiExperience.ANIME,
        stepIndex = 3,
        totalSteps = 6,
        section = "Settings",
        title = "Playback Preferences",
        description = "Customize your video playback: choose 1080p or 720p preferred quality, toggle auto-next episode, auto-skip intro sequences, and select Subbed or Dubbed audio.",
        targetKey = TourTargetKey.ANIME_SETTINGS_PLAYBACK
    ),
    ANIME_SETTINGS_DOWNLOADS(
        experience = ErumiExperience.ANIME,
        stepIndex = 4,
        totalSteps = 6,
        section = "Settings",
        title = "Offline Downloads",
        description = "Manage mobile data usage: restrict downloading to Wi-Fi only, and enable background downloads to keep downloading when minimized.",
        targetKey = TourTargetKey.ANIME_SETTINGS_DOWNLOADS
    ),
    ANIME_SETTINGS_EXPERIENCE(
        experience = ErumiExperience.ANIME,
        stepIndex = 5,
        totalSteps = 6,
        section = "Settings",
        title = "Switch to Manhua in Settings",
        description = "You can also switch over to the Manga & Manhua reading experience right here inside Settings with a single tap!",
        targetKey = TourTargetKey.ANIME_SETTINGS_EXPERIENCE
    ),
    ANIME_SETTINGS_STORAGE(
        experience = ErumiExperience.ANIME,
        stepIndex = 6,
        totalSteps = 6,
        section = "Settings",
        title = "Storage & Tour Replay",
        description = "Clear cached posters to free up storage space. You can also replay this guided tour whenever you like from Help & Tutorial!",
        targetKey = TourTargetKey.ANIME_SETTINGS_STORAGE
    ),

    // --- MANGA TOUR ---
    MANGA_WELCOME(
        experience = ErumiExperience.MANGA,
        stepIndex = 0,
        totalSteps = 7,
        section = "Welcome",
        title = "Welcome to Erumi Manhua",
        description = "Read thousands of chapters across top webtoon & manga sources, customize your vertical/horizontal reader, and download chapters for offline reading.\n\nTake a quick guided tour to explore search, experience switching, and reader settings!",
        targetKey = null
    ),
    MANGA_HOME_SEARCH(
        experience = ErumiExperience.MANGA,
        stepIndex = 1,
        totalSteps = 7,
        section = "Home",
        title = "Search Manga & Manhua",
        description = "Search across integrated catalogs for webtoons, manhua, and manga titles, or browse popular and trending series.",
        targetKey = TourTargetKey.HOME_SEARCH
    ),
    MANGA_HOME_SWITCH(
        experience = ErumiExperience.MANGA,
        stepIndex = 2,
        totalSteps = 7,
        section = "Home",
        title = "Switch Experience",
        description = "Tap this switch icon at the top of the screen anytime to instantly switch back to Anime streaming!",
        targetKey = TourTargetKey.HOME_SWITCH
    ),
    MANGA_SETTINGS_READER(
        experience = ErumiExperience.MANGA,
        stepIndex = 3,
        totalSteps = 7,
        section = "Settings",
        title = "Reader Settings",
        description = "Configure your reader: choose Vertical Webtoon scrolling or Horizontal pager, reading direction (LTR or RTL Manga), and double-tap gestures to toggle controls.",
        targetKey = TourTargetKey.MANGA_SETTINGS_READER
    ),
    MANGA_SETTINGS_SOURCE(
        experience = ErumiExperience.MANGA,
        stepIndex = 4,
        totalSteps = 7,
        section = "Settings",
        title = "Multi-Source Catalog",
        description = "Easily select your active reading source from top providers like ManhuaPlus, Asura Scans, Flame Comics, and Manganato.",
        targetKey = TourTargetKey.MANGA_SETTINGS_SOURCE
    ),
    MANGA_SETTINGS_DOWNLOADS(
        experience = ErumiExperience.MANGA,
        stepIndex = 5,
        totalSteps = 7,
        section = "Settings",
        title = "Offline Downloads",
        description = "Download manga and manhua chapters for offline reading on the go, with Wi-Fi only protection to save your mobile data.",
        targetKey = TourTargetKey.MANGA_SETTINGS_DOWNLOADS
    ),
    MANGA_SETTINGS_EXPERIENCE(
        experience = ErumiExperience.MANGA,
        stepIndex = 6,
        totalSteps = 7,
        section = "Settings",
        title = "Switch to Anime in Settings",
        description = "You can also switch over to Anime streaming right here inside Settings whenever you want to watch anime!",
        targetKey = TourTargetKey.MANGA_SETTINGS_EXPERIENCE
    ),
    MANGA_SETTINGS_STORAGE(
        experience = ErumiExperience.MANGA,
        stepIndex = 7,
        totalSteps = 7,
        section = "Settings",
        title = "Storage & Tour Replay",
        description = "Clear reading history anytime to free up storage space. You can also replay this guided tour whenever you need from Help & Tutorial!",
        targetKey = TourTargetKey.MANGA_SETTINGS_STORAGE
    )
}

class ErumiTourState(
    val experience: ErumiExperience,
    private val onCompletedCallback: () -> Unit
) {
    var currentStep by mutableStateOf<ErumiTourStep?>(null)
        private set

    val targets = mutableStateMapOf<TourTargetKey, Rect>()

    val isActive: Boolean
        get() = currentStep != null

    fun registerTarget(key: TourTargetKey, rect: Rect) {
        targets[key] = rect
    }

    fun startTour(navController: NavController? = null) {
        val homeRoute = if (experience == ErumiExperience.ANIME) Screen.Home.route else MangaScreen.Home.route
        if (navController != null && navController.currentDestination?.route != homeRoute) {
            navController.navigate(homeRoute) {
                popUpTo(homeRoute) { inclusive = false }
            }
        }
        currentStep = if (experience == ErumiExperience.ANIME) ErumiTourStep.ANIME_WELCOME else ErumiTourStep.MANGA_WELCOME
    }

    fun next(navController: NavController) {
        val nextStep = when (currentStep) {
            // Anime flow
            ErumiTourStep.ANIME_WELCOME -> ErumiTourStep.ANIME_HOME_SEARCH
            ErumiTourStep.ANIME_HOME_SEARCH -> ErumiTourStep.ANIME_HOME_SWITCH
            ErumiTourStep.ANIME_HOME_SWITCH -> {
                navController.navigate(Screen.Settings.route)
                ErumiTourStep.ANIME_SETTINGS_PLAYBACK
            }
            ErumiTourStep.ANIME_SETTINGS_PLAYBACK -> ErumiTourStep.ANIME_SETTINGS_DOWNLOADS
            ErumiTourStep.ANIME_SETTINGS_DOWNLOADS -> ErumiTourStep.ANIME_SETTINGS_EXPERIENCE
            ErumiTourStep.ANIME_SETTINGS_EXPERIENCE -> ErumiTourStep.ANIME_SETTINGS_STORAGE
            ErumiTourStep.ANIME_SETTINGS_STORAGE -> {
                finish(navController)
                null
            }

            // Manga flow
            ErumiTourStep.MANGA_WELCOME -> ErumiTourStep.MANGA_HOME_SEARCH
            ErumiTourStep.MANGA_HOME_SEARCH -> ErumiTourStep.MANGA_HOME_SWITCH
            ErumiTourStep.MANGA_HOME_SWITCH -> {
                navController.navigate(MangaScreen.Settings.route)
                ErumiTourStep.MANGA_SETTINGS_READER
            }
            ErumiTourStep.MANGA_SETTINGS_READER -> ErumiTourStep.MANGA_SETTINGS_SOURCE
            ErumiTourStep.MANGA_SETTINGS_SOURCE -> ErumiTourStep.MANGA_SETTINGS_DOWNLOADS
            ErumiTourStep.MANGA_SETTINGS_DOWNLOADS -> ErumiTourStep.MANGA_SETTINGS_EXPERIENCE
            ErumiTourStep.MANGA_SETTINGS_EXPERIENCE -> ErumiTourStep.MANGA_SETTINGS_STORAGE
            ErumiTourStep.MANGA_SETTINGS_STORAGE -> {
                finish(navController)
                null
            }
            null -> null
        }
        currentStep = nextStep
    }

    fun previous(navController: NavController) {
        val prevStep = when (currentStep) {
            // Anime flow
            ErumiTourStep.ANIME_HOME_SWITCH -> ErumiTourStep.ANIME_HOME_SEARCH
            ErumiTourStep.ANIME_SETTINGS_PLAYBACK -> {
                navController.popBackStack(Screen.Home.route, false)
                ErumiTourStep.ANIME_HOME_SWITCH
            }
            ErumiTourStep.ANIME_SETTINGS_DOWNLOADS -> ErumiTourStep.ANIME_SETTINGS_PLAYBACK
            ErumiTourStep.ANIME_SETTINGS_EXPERIENCE -> ErumiTourStep.ANIME_SETTINGS_DOWNLOADS
            ErumiTourStep.ANIME_SETTINGS_STORAGE -> ErumiTourStep.ANIME_SETTINGS_EXPERIENCE

            // Manga flow
            ErumiTourStep.MANGA_HOME_SWITCH -> ErumiTourStep.MANGA_HOME_SEARCH
            ErumiTourStep.MANGA_SETTINGS_READER -> {
                navController.popBackStack(MangaScreen.Home.route, false)
                ErumiTourStep.MANGA_HOME_SWITCH
            }
            ErumiTourStep.MANGA_SETTINGS_SOURCE -> ErumiTourStep.MANGA_SETTINGS_READER
            ErumiTourStep.MANGA_SETTINGS_DOWNLOADS -> ErumiTourStep.MANGA_SETTINGS_SOURCE
            ErumiTourStep.MANGA_SETTINGS_EXPERIENCE -> ErumiTourStep.MANGA_SETTINGS_DOWNLOADS
            ErumiTourStep.MANGA_SETTINGS_STORAGE -> ErumiTourStep.MANGA_SETTINGS_EXPERIENCE

            else -> null
        }
        if (prevStep != null) {
            currentStep = prevStep
        }
    }

    fun skip(navController: NavController) {
        finish(navController)
    }

    fun finish(navController: NavController) {
        currentStep = null
        val homeRoute = if (experience == ErumiExperience.ANIME) Screen.Home.route else MangaScreen.Home.route
        if (navController.currentDestination?.route != homeRoute) {
            navController.navigate(homeRoute) {
                popUpTo(homeRoute) { inclusive = false }
            }
        }
        onCompletedCallback()
    }
}

val LocalErumiTourState = compositionLocalOf<ErumiTourState?> { null }

@Composable
fun rememberErumiTourState(
    experience: ErumiExperience,
    onCompleted: () -> Unit = {}
): ErumiTourState {
    return remember(experience) { ErumiTourState(experience, onCompleted) }
}

@Composable
fun Modifier.tourHighlight(key: TourTargetKey): Modifier {
    val tourState = LocalErumiTourState.current ?: return this
    return this.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            tourState.registerTarget(key, coordinates.boundsInWindow())
        }
    }
}
