package com.erumi.nativeapp.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.erumi.nativeapp.domain.model.WatchProgress
import com.erumi.nativeapp.manga.domain.models.MangaReadingProgress
import com.erumi.nativeapp.shared.domain.models.MediaProgress
import com.erumi.nativeapp.ui.components.PressScale
import com.erumi.nativeapp.ui.theme.ErumiBorder
import com.erumi.nativeapp.ui.theme.ErumiCardBg
import com.erumi.nativeapp.ui.theme.ErumiElevatedSurface
import com.erumi.nativeapp.ui.theme.ErumiMuted
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondary

@Composable
fun ContinueMediaCard(
    progress: MediaProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 250.dp
) {
    PressScale(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(ErumiElevatedSurface)
            .border(1.dp, ErumiBorder, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ErumiCardBg)
                    .border(0.5.dp, ErumiBorder, RoundedCornerShape(8.dp))
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(progress.mediaCoverUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = progress.mediaTitle,
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = ErumiMuted)
                        }
                    }
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.mediaTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ErumiPrimaryText
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = progress.mediaSubtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ErumiSecondary,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ErumiPrimary,
                    trackColor = ErumiBorder
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${progress.progressPercentage}% completed",
                    style = MaterialTheme.typography.labelSmall.copy(color = ErumiMuted)
                )
            }
        }
    }
}

/**
 * Adapter overload for Anime WatchProgress.
 */
@Composable
fun ContinueMediaCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 250.dp
) {
    ContinueMediaCard(
        progress = progress as MediaProgress,
        onClick = onClick,
        modifier = modifier,
        cardWidth = cardWidth
    )
}

/**
 * Adapter overload for Manga MangaReadingProgress.
 */
@Composable
fun ContinueMediaCard(
    progress: MangaReadingProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 250.dp
) {
    ContinueMediaCard(
        progress = progress as MediaProgress,
        onClick = onClick,
        modifier = modifier,
        cardWidth = cardWidth
    )
}
