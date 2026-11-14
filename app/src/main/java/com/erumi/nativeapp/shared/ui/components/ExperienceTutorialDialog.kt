package com.erumi.nativeapp.shared.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erumi.nativeapp.manga.data.local.ErumiExperience
import com.erumi.nativeapp.ui.theme.BgCard
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiBorder
import com.erumi.nativeapp.ui.theme.ErumiElevatedSurface
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.ErumiSurface
import kotlinx.coroutines.launch

data class TutorialStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val highlightScreen: String
)

object TutorialContent {
    val AnimeSteps = listOf(
        TutorialStep(
            title = "Home & Streaming",
            subtitle = "Featured Anime & Fast Resume",
            description = "Browse trending anime, seasonal releases, and the top spotlight carousel. Resume your progress instantly from the 'Continue Watching' shelf.",
            icon = Icons.Rounded.Movie,
            highlightScreen = "Home Screen"
        ),
        TutorialStep(
            title = "Browse & Filters",
            subtitle = "Explore by Genre",
            description = "Filter through dozens of genres like Action, Comedy, Fantasy, Romance, and Shounen. Discover new anime matching your favorite categories.",
            icon = Icons.Rounded.Explore,
            highlightScreen = "Browse Screen"
        ),
        TutorialStep(
            title = "My Watchlist & Search",
            subtitle = "Keep Your Collection",
            description = "Bookmark shows you love or plan to watch. Search instantly across the anime catalog with real-time title search.",
            icon = Icons.Rounded.Bookmark,
            highlightScreen = "Watchlist Screen"
        ),
        TutorialStep(
            title = "Offline Downloads",
            subtitle = "Watch Anywhere with Zero Data",
            description = "Download episodes to your device and play them offline without internet. Queue downloads and track active progress effortlessly.",
            icon = Icons.Rounded.Download,
            highlightScreen = "Downloads Screen"
        ),
        TutorialStep(
            title = "Cinema Player & Switcher",
            subtitle = "Settings & Experience Switching",
            description = "Enjoy multi-server streaming with subtitle controls and video quality options. Tap the switch icon anytime in the top bar to swap to Manga!",
            icon = Icons.Rounded.SwapHoriz,
            highlightScreen = "Player & Header"
        )
    )

    val MangaSteps = listOf(
        TutorialStep(
            title = "Manga & Manhua Home",
            subtitle = "Trending Catalog & History",
            description = "Discover popular webtoons, manhua, and manga. Continue reading where you left off directly from the 'Continue Reading' shelf.",
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            highlightScreen = "Home Screen"
        ),
        TutorialStep(
            title = "Discover & Multi-Sources",
            subtitle = "Browse Trusted Scans",
            description = "Explore multiple registered sources (ManhuaPlus, Manganato, Asura Scans, Flame Comics, ManhuaScan). Filter by genre, popularity, and latest updates.",
            icon = Icons.Rounded.Explore,
            highlightScreen = "Discover Screen"
        ),
        TutorialStep(
            title = "Personal Library & History",
            subtitle = "Organize Your Reading",
            description = "Bookmark manga into custom statuses (Reading, Completed, Plan to Read). Your reading history is automatically recorded and synchronized.",
            icon = Icons.Rounded.CollectionsBookmark,
            highlightScreen = "Library Screen"
        ),
        TutorialStep(
            title = "Offline Chapter Downloads",
            subtitle = "Read Completely Offline",
            description = "Download chapters for offline reading. All downloaded chapters are stored securely and readable even without an internet connection.",
            icon = Icons.Rounded.Download,
            highlightScreen = "Downloads Screen"
        ),
        TutorialStep(
            title = "Full-Screen Reader Controls",
            subtitle = "Gestures & Switching to Anime",
            description = "Read in clean full-screen mode! Double-tap the screen to show or hide the top bar with the back button. Tap the switch icon at any time in the header to return to Anime.",
            icon = Icons.Rounded.AutoStories,
            highlightScreen = "Reader & Gestures"
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExperienceTutorialDialog(
    experience: ErumiExperience,
    onDismiss: () -> Unit
) {
    val steps = if (experience == ErumiExperience.MANGA) {
        TutorialContent.MangaSteps
    } else {
        TutorialContent.AnimeSteps
    }

    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == steps.lastIndex

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, ErumiBorder, RoundedCornerShape(24.dp)),
                color = ErumiSurface,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with screen badge & Skip button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ErumiPrimary.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = steps[pagerState.currentPage].highlightScreen,
                                color = ErumiPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(onClick = onDismiss) {
                            Text("Skip Tour", color = ErumiSecondaryText, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Pager Content
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) { page ->
                        val step = steps[page]
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Glowing Icon Circle
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                ErumiPrimary.copy(alpha = 0.25f),
                                                ErumiPrimary.copy(alpha = 0.05f)
                                            )
                                        )
                                    )
                                    .border(1.5.dp, ErumiPrimary.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    tint = ErumiPrimary,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Spacer(Modifier.height(18.dp))

                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ErumiPrimaryText,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = step.subtitle,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = ErumiPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ErumiSecondaryText,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Indicator dots
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(steps.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .height(6.dp)
                                    .width(if (isSelected) 22.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) ErumiPrimary else ErumiSecondaryText.copy(alpha = 0.3f))
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pagerState.currentPage > 0) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Previous", color = ErumiSecondaryText)
                            }
                        }

                        Button(
                            onClick = {
                                if (isLastPage) {
                                    onDismiss()
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(if (pagerState.currentPage > 0) 1.5f else 1f)
                        ) {
                            Text(
                                text = if (isLastPage) {
                                    if (experience == ErumiExperience.MANGA) "Start Reading" else "Start Watching"
                                } else "Next",
                                color = ErumiBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
