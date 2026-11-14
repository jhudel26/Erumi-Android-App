package com.erumi.nativeapp.features.watchlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.shared.ui.components.MediaEmptyState
import com.erumi.nativeapp.shared.ui.components.MediaPosterCard
import com.erumi.nativeapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchlistScreen(
    onAnimeClick: (Anime) -> Unit,
    viewModel: WatchlistViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var animeToRemove by remember { mutableStateOf<Anime?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredList = remember(state.watchlist, searchQuery) {
        if (searchQuery.isBlank()) {
            state.watchlist
        } else {
            state.watchlist.filter {
                it.title.contains(searchQuery.trim(), ignoreCase = true) ||
                it.englishName?.contains(searchQuery.trim(), ignoreCase = true) == true ||
                it.romajiName?.contains(searchQuery.trim(), ignoreCase = true) == true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ErumiBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My Watchlist",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = ErumiPrimaryText
                )
                Text(
                    text = "Keep track of anime you want to watch or are watching",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErumiSecondaryText
                )
            }
            IconButton(onClick = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
            }) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search Watchlist",
                    tint = ErumiPrimaryText
                )
            }
        }

        if (isSearchActive) {
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter saved anime...", color = ErumiMuted) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard,
                    focusedTextColor = ErumiPrimaryText,
                    unfocusedTextColor = ErumiPrimaryText,
                    cursorColor = ErumiPrimary,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.watchlist.isEmpty()) {
            MediaEmptyState(
                message = "Your Watchlist is empty",
                subtitle = "Save anime to keep track of what you want to watch",
                icon = Icons.Default.BookmarkBorder,
                modifier = Modifier.fillMaxSize()
            )
        } else if (filteredList.isEmpty()) {
            MediaEmptyState(
                message = "No saved anime matches '$searchQuery'",
                subtitle = "Try checking for typos or searching another title",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 0.dp, bottom = 80.dp)
            ) {
                item {
                    Text(
                        text = "${filteredList.size} of ${state.watchlist.size} saved titles (long press to remove)",
                        style = MaterialTheme.typography.labelMedium.copy(color = ErumiSecondaryText)
                    )
                }

                val chunked = filteredList.chunked(3)
                items(chunked) { rowAnimes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (anime in rowAnimes) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = { onAnimeClick(anime) },
                                        onLongClick = { animeToRemove = anime }
                                    )
                            ) {
                                MediaPosterCard(
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

        animeToRemove?.let { anime ->
            AlertDialog(
                onDismissRequest = { animeToRemove = null },
                title = { Text(text = "Remove from Watchlist", color = ErumiPrimaryText) },
                text = { Text(text = "Are you sure you want to remove '${anime.title}' from your watchlist?", color = ErumiSecondaryText) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.removeFromWatchlist(anime)
                            animeToRemove = null
                        }
                    ) {
                        Text("Remove", color = ErumiError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { animeToRemove = null }) {
                        Text("Cancel", color = ErumiSecondaryText)
                    }
                }
            )
        }
    }
}