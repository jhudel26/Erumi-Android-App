package com.erumi.nativeapp.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.manga.domain.models.Manga
import com.erumi.nativeapp.ui.components.PressScale
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiBorder
import com.erumi.nativeapp.ui.theme.ErumiCardBg
import com.erumi.nativeapp.ui.theme.ErumiElevatedSurface
import com.erumi.nativeapp.ui.theme.ErumiGold
import com.erumi.nativeapp.ui.theme.ErumiMuted
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText

/**
 * Unified Poster Card for Media Items (Anime, Manga, Manhua, Webtoons).
 */
@Composable
fun MediaPosterCard(
    title: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rating: Double? = null,
    badgeText: String? = null,
    cardWidth: Dp = 130.dp,
    aspectRatio: Float = 0.7f
) {
    PressScale(onClick = onClick, modifier = modifier.width(cardWidth)) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ErumiCardBg)
                    .border(1.dp, ErumiBorder, RoundedCornerShape(12.dp))
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(rememberShimmerBrush())
                        )
                    },
                    error = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(ErumiElevatedSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = ErumiMuted)
                        }
                    }
                )

                // Vignette gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                startY = 150f
                            )
                        )
                )

                // Rating Badge (Top Left)
                if (rating != null && rating > 0.0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ErumiBackground.copy(alpha = 0.85f))
                            .border(0.5.dp, ErumiBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = ErumiGold,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "%.1f".format(rating),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = ErumiPrimaryText
                            )
                        }
                    }
                }

                // Custom Badge / Episode / Chapter (Bottom End)
                if (!badgeText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ErumiBackground.copy(alpha = 0.85f))
                            .border(0.5.dp, ErumiBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            color = ErumiPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = ErumiPrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = ErumiSecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Adapter overload for Anime models.
 */
@Composable
fun MediaPosterCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MediaPosterCard(
        title = anime.title,
        coverUrl = anime.poster,
        onClick = onClick,
        modifier = modifier,
        subtitle = if (anime.episodes > 0) "${anime.episodes} Ep" else anime.format,
        rating = anime.score,
        badgeText = anime.status?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.titlecase() }
    )
}

/**
 * Adapter overload for Manga models.
 */
@Composable
fun MediaPosterCard(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MediaPosterCard(
        title = manga.title,
        coverUrl = manga.coverUrl,
        onClick = onClick,
        modifier = modifier,
        subtitle = manga.latestChapter,
        rating = manga.rating,
        badgeText = manga.contentType.name.takeUnless { it == "UNKNOWN" }
    )
}
