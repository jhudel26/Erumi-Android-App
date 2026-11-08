package com.erumi.nativeapp.features.watchlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.ui.components.AnimeCard
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiMuted
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onAnimeClick: (Anime) -> Unit,
    viewModel: WatchlistViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = ErumiBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Watchlist",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ErumiBackground)
            )
        }
    ) { padding ->
        if (state.watchlist.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = ErumiMuted,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your Watchlist is empty",
                        style = MaterialTheme.typography.titleMedium.copy(color = ErumiSecondaryText)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Save anime to keep track of what you want to watch",
                        style = MaterialTheme.typography.bodySmall.copy(color = ErumiMuted)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                item {
                    Text(
                        text = "${state.watchlist.size} saved titles",
                        style = MaterialTheme.typography.labelMedium.copy(color = ErumiSecondaryText)
                    )
                }

                val chunked = state.watchlist.chunked(3)
                items(chunked) { rowAnimes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (anime in rowAnimes) {
                            Box(modifier = Modifier.weight(1f)) {
                                AnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeClick(anime) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        val remaining = 3 - rowAnimes.size
                        if (remaining > 0) {
                            Spacer(modifier = Modifier.weight(remaining.toFloat()))
                        }
                    }
                }
            }
        }
    }
}
