package com.erumi.nativeapp.ui.experience

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erumi.nativeapp.R
import com.erumi.nativeapp.manga.data.local.ErumiExperience
import com.erumi.nativeapp.ui.theme.BgCard
import com.erumi.nativeapp.ui.theme.BgSurface
import com.erumi.nativeapp.ui.theme.BgSurfaceElevated
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@Composable
fun ExperienceSelectorScreen(
    onExperienceSelected: (ErumiExperience) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var selectedExperience by remember { mutableStateOf<ErumiExperience?>(null) }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isSmallScreen = screenWidth < 360.dp
    val isLargeScreen = screenWidth > 411.dp

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ErumiBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            HeroSection(
                visible = visible,
                screenWidth = screenWidth,
                isSmallScreen = isSmallScreen
            )

            Spacer(modifier = Modifier.height(if (isSmallScreen) 8.dp else 12.dp))

            // Category Selection Cards with 3D Flip Animation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSmallScreen) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 8.dp else 12.dp)
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(
                        animationSpec = tween(durationMillis = 600, delayMillis = 200)
                    ) + slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(durationMillis = 600, delayMillis = 200)
                    )
                ) {
                    FlipSelectionCard(
                        title = "Anime",
                        description = "Explore amazing anime series and movies.",
                        icon = Icons.Filled.PlayArrow,
                        backgroundImageRes = R.drawable.anime,
                        isSelected = selectedExperience == ErumiExperience.ANIME,
                        isTargetCard = selectedExperience == ErumiExperience.ANIME,
                        experience = ErumiExperience.ANIME,
                        onClick = { selectedExperience = ErumiExperience.ANIME },
                        onContinue = { onExperienceSelected(ErumiExperience.ANIME) },
                        screenWidth = screenWidth,
                        isSmallScreen = isSmallScreen,
                        isLargeScreen = isLargeScreen
                    )
                }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(
                        animationSpec = tween(durationMillis = 600, delayMillis = 300)
                    ) + slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = tween(durationMillis = 600, delayMillis = 300)
                    )
                ) {
                    FlipSelectionCard(
                        title = "Manhua",
                        description = "Discover manhua, manhwa, and manga illustrated stories.",
                        icon = Icons.Filled.PlayArrow,
                        backgroundImageRes = R.drawable.manhua,
                        isSelected = selectedExperience == ErumiExperience.MANGA,
                        isTargetCard = selectedExperience == ErumiExperience.MANGA,
                        experience = ErumiExperience.MANGA,
                        onClick = { selectedExperience = ErumiExperience.MANGA },
                        onContinue = { onExperienceSelected(ErumiExperience.MANGA) },
                        screenWidth = screenWidth,
                        isSmallScreen = isSmallScreen,
                        isLargeScreen = isLargeScreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isSmallScreen) 8.dp else 12.dp))
        }
    }
}

@Composable
private fun FlipSelectionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundImageRes: Int,
    isSelected: Boolean,
    isTargetCard: Boolean,
    experience: ErumiExperience,
    onClick: () -> Unit,
    onContinue: () -> Unit,
    screenWidth: androidx.compose.ui.unit.Dp,
    isSmallScreen: Boolean,
    isLargeScreen: Boolean
) {
    val rotation by animateFloatAsState(
        targetValue = if (isTargetCard) 180f else 0f,
        animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "flip_rotation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "card_scale"
    )

    val cardHeight = when {
        isSmallScreen -> 200.dp
        isLargeScreen -> 240.dp
        else -> 220.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .clipToBounds()
    ) {
        // Front side (Category Card)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f
                }
        ) {
            if (rotation < 90f) {
                CategoryCardFront(
                    title = title,
                    description = description,
                    icon = icon,
                    backgroundImageRes = backgroundImageRes,
                    isSelected = isSelected,
                    onClick = onClick,
                    isSmallScreen = isSmallScreen,
                    isLargeScreen = isLargeScreen
                )
            }
        }

        // Back side (Continue Button)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation - 180f
                    cameraDistance = 12f
                }
        ) {
            if (rotation >= 90f) {
                CategoryCardBack(
                    experience = experience,
                    onClick = onContinue,
                    screenWidth = screenWidth,
                    isSmallScreen = isSmallScreen,
                    isLargeScreen = isLargeScreen
                )
            }
        }
    }
}

@Composable
private fun CategoryCardFront(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundImageRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    isSmallScreen: Boolean,
    isLargeScreen: Boolean
) {
    val padding = when {
        isSmallScreen -> 12.dp
        isLargeScreen -> 20.dp
        else -> 16.dp
    }

    val iconSize = when {
        isSmallScreen -> 24.dp
        isLargeScreen -> 32.dp
        else -> 28.dp
    }

    val titleFontSize = when {
        isSmallScreen -> 18.sp
        isLargeScreen -> 24.sp
        else -> 22.sp
    }

    val descriptionFontSize = when {
        isSmallScreen -> 11.sp
        isLargeScreen -> 13.sp
        else -> 12.sp
    }

    val ctaFontSize = when {
        isSmallScreen -> 11.sp
        isLargeScreen -> 13.sp
        else -> 12.sp
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(backgroundImageRes),
                contentDescription = "$title Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BgSurface.copy(alpha = 0.3f),
                                BgSurfaceElevated.copy(alpha = 0.5f),
                                ErumiBackground.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ErumiPrimary.copy(alpha = 0.3f),
                                    ErumiPrimary.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 8.dp else 12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ErumiPrimary,
                        modifier = Modifier.size(iconSize)
                    )
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 6.dp))

                Text(
                    text = description,
                    color = ErumiSecondaryText,
                    fontSize = descriptionFontSize,
                    lineHeight = (descriptionFontSize.value + 3).sp
                )

                Spacer(modifier = Modifier.height(if (isSmallScreen) 8.dp else 12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)
                ) {
                    Text(
                        text = "Explore $title",
                        color = ErumiPrimary,
                        fontSize = ctaFontSize,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = ErumiPrimary,
                        modifier = Modifier.size(if (isSmallScreen) 16.dp else 18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCardBack(
    experience: ErumiExperience,
    onClick: () -> Unit,
    screenWidth: androidx.compose.ui.unit.Dp,
    isSmallScreen: Boolean,
    isLargeScreen: Boolean
) {
    val isAnime = experience == ErumiExperience.ANIME
    val descriptionText = if (isAnime) {
        "Watch your favorite anime\nor explore new stories"
    } else {
        "Read your favorite manhua\nor explore new stories"
    }

    val padding = when {
        isSmallScreen -> 12.dp
        isLargeScreen -> 20.dp
        else -> 16.dp
    }

    val brandingSize = when {
        isSmallScreen -> screenWidth * 0.3f
        isLargeScreen -> screenWidth * 0.4f
        else -> screenWidth * 0.35f
    }

    val spacing = when {
        isSmallScreen -> 8.dp
        isLargeScreen -> 16.dp
        else -> 12.dp
    }

    val titleFontSize = when {
        isSmallScreen -> 12.sp
        isLargeScreen -> 15.sp
        else -> 14.sp
    }

    val descriptionFontSize = when {
        isSmallScreen -> 10.sp
        isLargeScreen -> 13.sp
        else -> 12.sp
    }

    val iconSize = when {
        isSmallScreen -> 16.dp
        isLargeScreen -> 24.dp
        else -> 20.dp
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        color = BgCard,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BgSurface,
                            BgSurfaceElevated,
                            ErumiBackground
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Branding image on the left
                Image(
                    painter = painterResource(R.drawable.fullbodybranding),
                    contentDescription = "Erumi Branding",
                    modifier = Modifier
                        .size(brandingSize)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )

                // Text on the right
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Click to continue",
                        color = ErumiPrimary,
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 6.dp))
                    Text(
                        text = descriptionText,
                        color = ErumiSecondaryText,
                        fontSize = descriptionFontSize,
                        lineHeight = (descriptionFontSize.value + 3).sp,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.height(if (isSmallScreen) 6.dp else 8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = ErumiPrimary,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    visible: Boolean,
    screenWidth: androidx.compose.ui.unit.Dp,
    isSmallScreen: Boolean
) {
    val heroHeight = when {
        isSmallScreen -> screenWidth * 0.35f
        else -> 300.dp
    }

    val padding = when {
        isSmallScreen -> 16.dp
        else -> 24.dp
    }

    val verticalPadding = when {
        isSmallScreen -> 12.dp
        else -> 16.dp
    }

    val welcomeFontSize = when {
        isSmallScreen -> 10.sp
        else -> 12.sp
    }

    val titleFontSize = when {
        isSmallScreen -> 20.sp
        else -> 24.sp
    }

    val descriptionFontSize = when {
        isSmallScreen -> 11.sp
        else -> 13.sp
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 800)
        ) + slideInVertically(
            initialOffsetY = { -it / 4 },
            animationSpec = tween(durationMillis = 800)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
        ) {
            // Background image with overlay
            Image(
                painter = painterResource(R.drawable.erumi_splash),
                contentDescription = "Hero Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )

            // Dark gradient overlay using theme colors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BgSurface.copy(alpha = 0.5f),
                                BgSurfaceElevated.copy(alpha = 0.7f),
                                ErumiBackground
                            )
                        )
                    )
            )

            // Hero content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = padding, vertical = verticalPadding)
                    .statusBarsPadding(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "WELCOME TO ERUMI",
                    color = ErumiPrimary,
                    fontSize = welcomeFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 6.dp))
                Text(
                    text = "What Would You Like to",
                    color = TextPrimary,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Explore?",
                    color = ErumiPrimary,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(if (isSmallScreen) 4.dp else 6.dp))
                Text(
                    text = "Choose your favorite category and start discovering something exciting!",
                    color = ErumiSecondaryText,
                    fontSize = descriptionFontSize,
                    lineHeight = (descriptionFontSize.value + 4).sp
                )
            }
        }
    }
}
