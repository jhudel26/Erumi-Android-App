package com.erumi.nativeapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.erumi.nativeapp.core.download.DownloadHelper
import com.erumi.nativeapp.manga.data.local.ErumiExperience
import com.erumi.nativeapp.manga.data.local.ExperiencePreferences
import com.erumi.nativeapp.manga.navigation.MangaNavGraph
import com.erumi.nativeapp.navigation.ErumiNavGraph
import com.erumi.nativeapp.shared.ui.components.ExperienceTutorialDialog
import com.erumi.nativeapp.ui.experience.ExperienceSelectorScreen
import com.erumi.nativeapp.ui.splash.ErumiSplashScreen
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Result handled - battery optimization may have been granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val keepSystemSplash = AtomicBoolean(true)
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSystemSplash.get() }
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge full screen with notch/cutout handling
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContent {
            ErumiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ErumiBackground
                ) {
                    var showBrandedSplash by remember { mutableStateOf(true) }
                    var batteryOptimizationIntent by remember { mutableStateOf<Intent?>(null) }
                    var currentExperience by remember { mutableStateOf<ErumiExperience?>(null) }
                    var showExperienceSelector by remember { mutableStateOf(false) }
                    var skipSelector by remember { mutableStateOf(false) }
                    var lastExperience by remember { mutableStateOf(ErumiExperience.SELECTOR) }
                    var animeTutorialCompleted by remember { mutableStateOf(false) }
                    var mangaTutorialCompleted by remember { mutableStateOf(false) }

                    val experiencePrefs = remember { ExperiencePreferences(this) }

                    LaunchedEffect(Unit) {
                        keepSystemSplash.set(false)

                        // Load experience preferences
                        skipSelector = experiencePrefs.skipSelectorOnLaunch.first()
                        lastExperience = experiencePrefs.lastExperience.first()
                        animeTutorialCompleted = experiencePrefs.animeTutorialCompleted.first()
                        mangaTutorialCompleted = experiencePrefs.mangaTutorialCompleted.first()

                        currentExperience = if (skipSelector && lastExperience != ErumiExperience.SELECTOR) {
                            lastExperience
                        } else {
                            ErumiExperience.SELECTOR
                        }

                        showExperienceSelector = currentExperience == ErumiExperience.SELECTOR
                    }

                    // Handle battery optimization intent
                    LaunchedEffect(batteryOptimizationIntent) {
                        batteryOptimizationIntent?.let { intent ->
                            batteryOptimizationLauncher.launch(intent)
                            batteryOptimizationIntent = null
                        }
                    }

                    // Immersive full screen during launch & selection screen only
                    val isFullScreenLaunch = showBrandedSplash || showExperienceSelector
                    DisposableEffect(isFullScreenLaunch) {
                        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        if (isFullScreenLaunch) {
                            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        } else {
                            insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        }
                        onDispose { }
                    }

                    if (showBrandedSplash) {
                        ErumiSplashScreen(
                            ready = true,
                            onFinished = { showBrandedSplash = false }
                        )
                    } else if (showExperienceSelector) {
                        ExperienceSelectorScreen(
                            onExperienceSelected = { experience ->
                                currentExperience = experience
                                showExperienceSelector = false
                                lifecycleScope.launch {
                                    experiencePrefs.setLastExperience(experience)
                                }
                            }
                        )
                    } else {
                        when (currentExperience) {
                            ErumiExperience.ANIME -> {
                                ErumiNavGraph(
                                    onRequestBatteryOptimization = { intent ->
                                        batteryOptimizationIntent = intent
                                    },
                                    onSwitchToManga = {
                                        currentExperience = ErumiExperience.MANGA
                                        lifecycleScope.launch {
                                            experiencePrefs.setLastExperience(ErumiExperience.MANGA)
                                        }
                                    },
                                    startTourOnLaunch = !animeTutorialCompleted,
                                    onTourCompleted = {
                                        animeTutorialCompleted = true
                                        lifecycleScope.launch {
                                            experiencePrefs.setAnimeTutorialCompleted(true)
                                        }
                                    }
                                )
                            }
                            ErumiExperience.MANGA -> {
                                MangaNavGraph(
                                    onSwitchExperience = {
                                        currentExperience = ErumiExperience.ANIME
                                        lifecycleScope.launch {
                                            experiencePrefs.setLastExperience(ErumiExperience.ANIME)
                                        }
                                    },
                                    startTourOnLaunch = !mangaTutorialCompleted,
                                    onTourCompleted = {
                                        mangaTutorialCompleted = true
                                        lifecycleScope.launch {
                                            experiencePrefs.setMangaTutorialCompleted(true)
                                        }
                                    }
                                )
                            }
                            else -> {
                                // Fallback to experience selector
                                showExperienceSelector = true
                            }
                        }
                    }
                }
            }
        }
    }
}
