package com.erumi.nativeapp.shared.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiBorder
import com.erumi.nativeapp.ui.theme.ErumiCardBg
import com.erumi.nativeapp.ui.theme.ErumiElevatedSurface
import com.erumi.nativeapp.ui.theme.ErumiPrimary

enum class ShimmerType {
    HOME,
    GRID,
    SEARCH,
    DETAILS,
    SHELF,
    SINGLE_BOX,
    CIRCULAR
}

@Composable
fun ShimmerLoader(
    type: ShimmerType = ShimmerType.HOME,
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
    columns: GridCells = GridCells.Fixed(2)
) {
    val brush = rememberShimmerBrush()
    when (type) {
        ShimmerType.CIRCULAR -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ErumiPrimary)
            }
        }
        ShimmerType.SINGLE_BOX -> {
            ShimmerBox(modifier = modifier, brush = brush)
        }
        ShimmerType.HOME -> {
            ShimmerHome(modifier = modifier, brush = brush)
        }
        ShimmerType.DETAILS -> {
            ShimmerDetails(modifier = modifier, brush = brush)
        }
        ShimmerType.GRID -> {
            ShimmerGrid(columns = columns, itemCount = itemCount, modifier = modifier, brush = brush)
        }
        ShimmerType.SEARCH -> {
            ShimmerSearch(columns = columns, itemCount = itemCount, modifier = modifier, brush = brush)
        }
        ShimmerType.SHELF -> {
            ShimmerShelf(itemCount = itemCount, modifier = modifier, brush = brush)
        }
    }
}

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -350f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return Brush.linearGradient(
        colors = listOf(
            ErumiCardBg,
            ErumiElevatedSurface,
            ErumiBorder.copy(alpha = 0.65f),
            ErumiElevatedSurface,
            ErumiCardBg
        ),
        start = Offset(translateAnim, translateAnim),
        end = Offset(translateAnim + 340f, translateAnim + 340f)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    brush: Brush? = null
) {
    val actualBrush = brush ?: rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(actualBrush)
    )
}

@Composable
fun ShimmerSectionHeader(
    brush: Brush,
    titleWidth: Dp = 150.dp,
    hasSeeAll: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ErumiPrimary.copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(8.dp))
            ShimmerBox(
                modifier = Modifier.width(titleWidth).height(18.dp),
                shape = RoundedCornerShape(4.dp),
                brush = brush
            )
        }
        if (hasSeeAll) {
            ShimmerBox(
                modifier = Modifier.width(50.dp).height(14.dp),
                shape = RoundedCornerShape(4.dp),
                brush = brush
            )
        }
    }
}

@Composable
fun ShimmerPosterCard(
    modifier: Modifier = Modifier,
    brush: Brush
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .width(36.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ErumiElevatedSurface.copy(alpha = 0.85f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .width(44.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ErumiElevatedSurface.copy(alpha = 0.85f))
            )
        }
        Spacer(Modifier.height(8.dp))
        ShimmerBox(
            modifier = Modifier.fillMaxWidth(0.85f).height(14.dp),
            shape = RoundedCornerShape(4.dp),
            brush = brush
        )
        Spacer(Modifier.height(4.dp))
        ShimmerBox(
            modifier = Modifier.fillMaxWidth(0.52f).height(12.dp),
            shape = RoundedCornerShape(4.dp),
            brush = brush
        )
    }
}

@Composable
fun ShimmerContinueCard(
    modifier: Modifier = Modifier,
    brush: Brush
) {
    Column(modifier = modifier.width(220.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(brush)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .background(ErumiPrimary.copy(alpha = 0.7f))
            )
        }
        Spacer(Modifier.height(6.dp))
        ShimmerBox(
            modifier = Modifier.width(160.dp).height(14.dp),
            shape = RoundedCornerShape(4.dp),
            brush = brush
        )
        Spacer(Modifier.height(4.dp))
        ShimmerBox(
            modifier = Modifier.width(90.dp).height(11.dp),
            shape = RoundedCornerShape(4.dp),
            brush = brush
        )
    }
}

@Composable
fun ShimmerHome(
    modifier: Modifier = Modifier,
    brush: Brush
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp)
    ) {
        // 1. Hero Banner Placeholder
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    ErumiCardBg.copy(alpha = 0.85f),
                                    ErumiBackground
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier.fillMaxWidth(0.6f).height(22.dp),
                        shape = RoundedCornerShape(4.dp),
                        brush = brush
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) {
                            ShimmerBox(
                                modifier = Modifier.width(55.dp).height(20.dp),
                                shape = RoundedCornerShape(10.dp),
                                brush = brush
                            )
                        }
                    }
                }
            }
        }

        // 2. Continue Watching / Reading Shelf
        item {
            Column {
                ShimmerSectionHeader(brush = brush, titleWidth = 150.dp, hasSeeAll = true)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(3) {
                        ShimmerContinueCard(brush = brush)
                    }
                }
            }
        }

        // 3. Trending Spotlight Shelf
        item {
            Column {
                ShimmerSectionHeader(brush = brush, titleWidth = 160.dp)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(5) {
                        ShimmerPosterCard(modifier = Modifier.width(130.dp), brush = brush)
                    }
                }
            }
        }

        // 4. Latest Releases Grid Header & Filter Chips
        item {
            Column {
                ShimmerSectionHeader(brush = brush, titleWidth = 140.dp)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(6) {
                        ShimmerBox(
                            modifier = Modifier.width(72.dp).height(32.dp),
                            shape = RoundedCornerShape(16.dp),
                            brush = brush
                        )
                    }
                }
            }
        }

        // 5. Latest Releases Cards (Row of 3 cards)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    ShimmerPosterCard(modifier = Modifier.weight(1f), brush = brush)
                }
            }
        }
    }
}

@Composable
fun ShimmerDetails(
    modifier: Modifier = Modifier,
    brush: Brush
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(brush)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    ErumiBackground
                                )
                            )
                        )
                )
            }
        }

        // 2. Poster & Main Metadata
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.offset(y = (-40).dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(125.dp)
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(brush)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 45.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth(0.9f).height(20.dp),
                            shape = RoundedCornerShape(4.dp),
                            brush = brush
                        )
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth(0.6f).height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                            brush = brush
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(3) {
                                ShimmerBox(
                                    modifier = Modifier.width(48.dp).height(20.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    brush = brush
                                )
                            }
                        }
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth(0.45f).height(12.dp),
                            shape = RoundedCornerShape(4.dp),
                            brush = brush
                        )
                    }
                }

                // Action buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-20).dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        brush = brush
                    )
                    ShimmerBox(
                        modifier = Modifier.width(44.dp).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        brush = brush
                    )
                    ShimmerBox(
                        modifier = Modifier.width(44.dp).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        brush = brush
                    )
                }
            }
        }

        // 3. Synopsis / Description
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ShimmerSectionHeader(brush = brush, titleWidth = 80.dp)
                Spacer(Modifier.height(8.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(12.dp), shape = RoundedCornerShape(4.dp), brush = brush)
                Spacer(Modifier.height(6.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.95f).height(12.dp), shape = RoundedCornerShape(4.dp), brush = brush)
                Spacer(Modifier.height(6.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp), shape = RoundedCornerShape(4.dp), brush = brush)
                Spacer(Modifier.height(6.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.55f).height(12.dp), shape = RoundedCornerShape(4.dp), brush = brush)
            }
        }

        // 4. Episodes / Chapters List Section
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ShimmerSectionHeader(brush = brush, titleWidth = 110.dp)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(5) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ErumiCardBg)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ShimmerBox(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                brush = brush
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ShimmerBox(
                                    modifier = Modifier.fillMaxWidth(0.6f).height(14.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    brush = brush
                                )
                                ShimmerBox(
                                    modifier = Modifier.fillMaxWidth(0.35f).height(10.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    brush = brush
                                )
                            }
                            ShimmerBox(
                                modifier = Modifier.size(24.dp),
                                shape = RoundedCornerShape(12.dp),
                                brush = brush
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerGrid(
    columns: GridCells = GridCells.Fixed(2),
    itemCount: Int = 6,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier,
    brush: Brush
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(itemCount) {
            ShimmerPosterCard(brush = brush)
        }
    }
}

@Composable
fun ShimmerSearch(
    columns: GridCells = GridCells.Fixed(3),
    itemCount: Int = 9,
    contentPadding: PaddingValues = PaddingValues(bottom = 80.dp),
    modifier: Modifier = Modifier,
    brush: Brush
) {
    Column(modifier = modifier.fillMaxSize()) {
        ShimmerBox(
            modifier = Modifier.padding(bottom = 12.dp).width(120.dp).height(14.dp),
            shape = RoundedCornerShape(4.dp),
            brush = brush
        )
        LazyVerticalGrid(
            columns = columns,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(itemCount) {
                ShimmerPosterCard(brush = brush)
            }
        }
    }
}

@Composable
fun ShimmerShelf(
    itemCount: Int = 5,
    modifier: Modifier = Modifier,
    brush: Brush
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        ShimmerSectionHeader(brush = brush, titleWidth = 140.dp, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(itemCount) {
                ShimmerPosterCard(modifier = Modifier.width(130.dp), brush = brush)
            }
        }
    }
}
