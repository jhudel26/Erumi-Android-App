package com.erumi.nativeapp.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erumi.nativeapp.R
import com.erumi.nativeapp.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Branded launch screen using Erumi artwork while the app initializes.
 */
@Composable
fun ErumiSplashScreen(
    ready: Boolean,
    onFinished: () -> Unit
) {
    var minTimeElapsed by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (ready && minTimeElapsed) 0f else 1f,
        animationSpec = tween(360),
        label = "splashFade"
    )

    fun finishOnce() {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        delay(500)
        minTimeElapsed = true
    }

    LaunchedEffect(ready, minTimeElapsed) {
        if (ready && minTimeElapsed) {
            delay(380)
            finishOnce()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ErumiBackground)
            .alpha(alpha.coerceAtLeast(0.01f))
    ) {
        Image(
            painter = painterResource(R.drawable.splash_brand),
            contentDescription = "ERUMI",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to ErumiBackground.copy(alpha = 0.25f),
                        0.55f to ErumiBackground.copy(alpha = 0.15f),
                        1f to ErumiBackground.copy(alpha = 0.88f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "ERUMI",
                color = ErumiPrimaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Anime Streaming",
                color = TealPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(16.dp))
            // Animated loading bar instead of static text
            LinearProgressIndicator(
                modifier = Modifier
                    .width(120.dp)
                    .height(3.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                color = TealPrimary,
                trackColor = ErumiPrimaryText.copy(alpha = 0.12f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Loading…",
                color = ErumiSecondaryText,
                fontSize = 12.sp
            )
        }
    }
}
