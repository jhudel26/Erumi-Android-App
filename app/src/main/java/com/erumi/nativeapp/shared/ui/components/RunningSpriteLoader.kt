package com.erumi.nativeapp.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.erumi.nativeapp.R
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import kotlin.math.roundToInt

private val RUN_SPRITE_FRAMES = intArrayOf(
    R.drawable.run_01,
    R.drawable.run_02,
    R.drawable.run_03,
    R.drawable.run_04,
    R.drawable.run_05,
    R.drawable.run_06,
    R.drawable.run_07,
    R.drawable.run_08,
    R.drawable.run_09,
    R.drawable.run_10,
    R.drawable.run_11,
    R.drawable.run_12,
    R.drawable.run_13,
    R.drawable.run_14,
    R.drawable.run_15,
    R.drawable.run_16,
    R.drawable.run_17,
    R.drawable.run_18,
    R.drawable.run_19,
    R.drawable.run_20,
    R.drawable.run_21,
    R.drawable.run_22,
    R.drawable.run_23,
    R.drawable.run_24
)

/**
 * Vsync-locked animated sprite loader cycling through `run_01`..`run_24` frames.
 * Preloads all frames into memory and draws on a [Canvas] to avoid per-frame
 * recomposition and decode stutter.
 */
@Composable
fun RunningSpriteLoader(
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    frameDelayMs: Long = 33L,
    label: String? = null
) {
    val resources = LocalContext.current.resources
    val bitmaps = remember(resources) {
        RUN_SPRITE_FRAMES.map { resId ->
            ImageBitmap.imageResource(resources, resId)
        }
    }

    var frameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(bitmaps, frameDelayMs) {
        val startTime = withFrameMillis { it }
        val duration = frameDelayMs.coerceAtLeast(16L)
        while (true) {
            withFrameMillis { now ->
                val elapsed = (now - startTime).coerceAtLeast(0L)
                frameIndex = ((elapsed / duration) % bitmaps.size).toInt()
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = label ?: "Loading" }
        ) {
            val frame = bitmaps[frameIndex]
            val srcW = frame.width.toFloat()
            val srcH = frame.height.toFloat()
            if (srcW > 0f && srcH > 0f) {
                val scale = minOf(this.size.width / srcW, this.size.height / srcH)
                val dstW = (srcW * scale).roundToInt().coerceAtLeast(1)
                val dstH = (srcH * scale).roundToInt().coerceAtLeast(1)
                val offsetX = ((this.size.width - dstW) / 2f).roundToInt()
                val offsetY = ((this.size.height - dstH) / 2f).roundToInt()
                drawImage(
                    image = frame,
                    dstOffset = IntOffset(offsetX, offsetY),
                    dstSize = IntSize(dstW, dstH),
                    filterQuality = FilterQuality.High
                )
            }
        }
        if (!label.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(color = ErumiPrimaryText)
            )
        }
    }
}
