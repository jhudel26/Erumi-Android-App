package com.erumi.nativeapp.features.watchlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.ui.components.AnimeCard
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

    Scaffold(
        containerColor = ErumiBackground,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter saved anime...", color = ErumiMuted) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedTextColor = ErumiPrimaryText,
                                unfocusedTextColor = ErumiPrimaryText,
                                cursorColor = ErumiPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "My Watchlist",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
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
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved anime matches '$searchQuery'",
                    style = MaterialTheme.typography.bodyMedium.copy(color = ErumiSecondaryText)
                )
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
                        text = "${filteredList.size} of ${state.watchlist.size} saved titles (long press to remove)",
                        style = MaterialTheme.typography.labelMedium.copy(color = ErumiSecondaryText)
                    )
                }

                val chunked = filteredList.chunked(3)
                items(chunked) { rowAnimes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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