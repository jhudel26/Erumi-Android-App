package com.erumi.nativeapp.shared.ui.tour

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.erumi.nativeapp.R
import com.erumi.nativeapp.ui.theme.ErumiElevatedSurface
import com.erumi.nativeapp.ui.theme.ErumiMuted
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText

@Composable
fun ErumiTourOverlay(
    tourState: ErumiTourState,
    navController: NavController
) {
    val step = tourState.currentStep ?: return

    if (step == ErumiTourStep.ANIME_WELCOME || step == ErumiTourStep.MANGA_WELCOME) {
        ErumiTourWelcomeDialog(
            step = step,
            onStartTour = { tourState.next(navController) },
            onSkip = { tourState.skip(navController) }
        )
        return
    }

    var overlayWindowOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) {
                    overlayWindowOffset = coordinates.positionInWindow()
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Consume background touches */ }
            )
    ) {
        val screenHeight = maxHeight
        val windowRect = step.targetKey?.let { tourState.targets[it] }
        val localRect = windowRect?.let {
            Rect(
                left = it.left - overlayWindowOffset.x,
                top = it.top - overlayWindowOffset.y,
                right = it.right - overlayWindowOffset.x,
                bottom = it.bottom - overlayWindowOffset.y
            )
        }

        // Pulse animation for spotlight ring
        val infiniteTransition = rememberInfiniteTransition(label = "spotlightPulse")
        val pulseExpansion by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseExpansion"
        )

        // Darkened canvas with cutout
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            // 1. Darken the screen
            drawRect(color = Color.Black.copy(alpha = 0.83f))

            // 2. Cutout spotlight over target
            localRect?.let { r ->
                val pad = 8.dp.toPx()
                val corner = 16.dp.toPx()
                val cutLeft = (r.left - pad).coerceAtLeast(0f)
                val cutTop = (r.top - pad).coerceAtLeast(0f)
                val cutRight = (r.right + pad).coerceAtMost(size.width)
                val cutBottom = (r.bottom + pad).coerceAtMost(size.height)
                val cutWidth = (cutRight - cutLeft).coerceAtLeast(0f)
                val cutHeight = (cutBottom - cutTop).coerceAtLeast(0f)

                // Hole
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(cutLeft, cutTop),
                    size = Size(cutWidth, cutHeight),
                    cornerRadius = CornerRadius(corner, corner),
                    blendMode = BlendMode.Clear
                )

                // Glowing neon border
                drawRoundRect(
                    color = ErumiPrimary,
                    topLeft = Offset(cutLeft - pulseExpansion, cutTop - pulseExpansion),
                    size = Size(cutWidth + pulseExpansion * 2, cutHeight + pulseExpansion * 2),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        // Determine if target is in the upper half or lower half
        val isTargetInLowerHalf = localRect != null && localRect.top > (screenHeight.value * 0.45f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isTargetInLowerHalf) {
                        Modifier
                            .statusBarsPadding()
                            .padding(top = 16.dp)
                    } else {
                        Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    }
                ),
            contentAlignment = if (isTargetInLowerHalf) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "tourDialogStep"
            ) { activeStep ->
                TourGuideBubble(
                    step = activeStep,
                    onNext = { tourState.next(navController) },
                    onPrev = { tourState.previous(navController) },
                    onSkip = { tourState.skip(navController) }
                )
            }
        }
    }
}

@Composable
private fun ErumiTourWelcomeDialog(
    step: ErumiTourStep,
    onStartTour: () -> Unit,
    onSkip: () -> Unit
) {
    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = ErumiElevatedSurface,
                border = BorderStroke(1.5.dp, ErumiPrimary.copy(alpha = 0.6f)),
                tonalElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Branding Icon with glow
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0x6600DC82), Color.Transparent)
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_icon),
                            contentDescription = "Erumi Branding",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, ErumiPrimary, RoundedCornerShape(16.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = ErumiPrimaryText
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val subtitle = if (step == ErumiTourStep.ANIME_WELCOME) {
                        "Cinema Anime Streaming"
                    } else {
                        "Manhua · Manhwa · Manga Reader"
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = ErumiPrimary,
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = ErumiSecondaryText,
                            lineHeight = 20.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onStartTour,
                        colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Start Tour",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = onSkip) {
                        Text(
                            text = "Skip Tour",
                            color = ErumiMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TourGuideBubble(
    step: ErumiTourStep,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit
) {
    val isLast = step.stepIndex == step.totalSteps

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mascot character as guide
            Image(
                painter = painterResource(id = R.drawable.fullbodybranding),
                contentDescription = "Erumi Mascot Guide",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(110.dp)
                    .height(210.dp)
            )

            // Dialogue Card
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = ErumiElevatedSurface,
                border = BorderStroke(1.5.dp, ErumiPrimary.copy(alpha = 0.7f)),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ERUMI GUIDE",
                            color = ErumiPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${step.stepIndex}/${step.totalSteps}",
                            color = ErumiSecondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = step.title,
                        color = ErumiPrimaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = step.description,
                        color = ErumiSecondaryText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (step.stepIndex > 1) {
                            TextButton(
                                onClick = onPrev,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Back", color = ErumiSecondaryText, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        TextButton(
                            onClick = onSkip,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Skip", color = ErumiMuted, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isLast) "Finish" else "Next",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
