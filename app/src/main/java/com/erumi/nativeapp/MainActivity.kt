package com.erumi.nativeapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import com.erumi.nativeapp.core.download.DownloadHelper
import com.erumi.nativeapp.navigation.ErumiNavGraph
import com.erumi.nativeapp.ui.splash.ErumiSplashScreen
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiTheme
import java.util.concurrent.atomic.AtomicBoolean

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

        setContent {
            ErumiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ErumiBackground
                ) {
                    var showBrandedSplash by remember { mutableStateOf(true) }
                    var batteryOptimizationIntent by remember { mutableStateOf<Intent?>(null) }

                    LaunchedEffect(Unit) {
                        keepSystemSplash.set(false)
                    }

                    // Handle battery optimization intent
                    LaunchedEffect(batteryOptimizationIntent) {
                        batteryOptimizationIntent?.let { intent ->
                            batteryOptimizationLauncher.launch(intent)
                            batteryOptimizationIntent = null
                        }
                    }

                    if (showBrandedSplash) {
                        ErumiSplashScreen(
                            ready = true,
                            onFinished = { showBrandedSplash = false }
                        )
                    } else {
                        ErumiNavGraph(
                            onRequestBatteryOptimization = { intent ->
                                batteryOptimizationIntent = intent
                            }
                        )
                    }
                }
            }
        }
    }
}
