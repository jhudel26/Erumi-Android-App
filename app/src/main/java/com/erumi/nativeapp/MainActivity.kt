package com.erumi.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.erumi.nativeapp.navigation.ErumiNavGraph
import com.erumi.nativeapp.ui.splash.ErumiSplashScreen
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiTheme
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
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

                    LaunchedEffect(Unit) {
                        keepSystemSplash.set(false)
                    }

                    if (showBrandedSplash) {
                        ErumiSplashScreen(
                            ready = true,
                            onFinished = { showBrandedSplash = false }
                        )
                    } else {
                        ErumiNavGraph()
                    }
                }
            }
        }
    }
}
