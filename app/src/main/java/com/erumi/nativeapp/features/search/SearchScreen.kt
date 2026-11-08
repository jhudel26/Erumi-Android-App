package com.erumi.nativeapp.features.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.ui.components.AnimeCard
import com.erumi.nativeapp.ui.components.ErrorView
import com.erumi.nativeapp.ui.theme.*

@Composable
fun SearchScreen(
    onAnimeClick: (Anime) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val popularTags = listOf("Solo Leveling", "Jujutsu Kaisen", "Frieren", "Demon Slayer", "One Piece", "Bleach", "Attack on Titan")

    Scaffold(
        containerColor = ErumiBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = {
                    Text(text = "Search anime by title...", color = ErumiMuted)
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = ErumiPrimary)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = ErumiSecondaryText)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ErumiPrimary,
                    unfocusedBorderColor = ErumiBorder,
                    focusedContainerColor = ErumiElevatedSurface,
                    unfocusedContainerColor = ErumiSurface,
                    focusedTextColor = ErumiPrimaryText,
                    unfocusedTextColor = ErumiPrimaryText
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // When no active search, show Popular Searches & Recent Searches
            if (state.query.isBlank() && !state.hasSearched) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Recent Searches
                    if (state.recentSearches.isNotEmpty()) {
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Searches",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    TextButton(onClick = { viewModel.clearAllRecentSearches() }) {
                                        Text(text = "Clear All", color = ErumiSecondaryText, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    state.recentSearches.forEach { q ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ErumiSurface)
                                                .clickable { viewModel.performSearch(q) }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = q, style = MaterialTheme.typography.bodyMedium)
                                            IconButton(
                                                onClick = { viewModel.deleteRecentSearch(q) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    tint = ErumiMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Popular Search Tags
                    item {
                        Column {
                            Text(
                                text = "Popular Searches",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(popularTags) { tag ->
                                    Surface(
                                        color = ErumiElevatedSurface,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.clickable { viewModel.performSearch(tag) }
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelMedium.copy(color = ErumiPrimary),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Search Results
                if (state.isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ErumiPrimary)
                    }
                } else if (state.error != null) {
                    ErrorView(
                        message = state.error ?: "Search failed",
                        onRetry = { viewModel.performSearch(state.query) }
                    )
                } else if (state.searchResults.isEmpty() && state.hasSearched) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No anime found",
                                style = MaterialTheme.typography.titleMedium.copy(color = ErumiSecondaryText)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try searching with a different title or keyword",
                                style = MaterialTheme.typography.bodySmall.copy(color = ErumiMuted)
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(3) }) {
                            Text(
                                text = "${state.searchResults.size} results found",
                                style = MaterialTheme.typography.labelMedium.copy(color = ErumiSecondaryText),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        items(
                            items = state.searchResults,
                            key = { it.id }
                        ) { anime ->
                            AnimeCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
