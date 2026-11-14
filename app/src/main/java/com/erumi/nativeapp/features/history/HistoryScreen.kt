package com.erumi.nativeapp.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.domain.model.WatchProgress
import com.erumi.nativeapp.shared.ui.components.MediaEmptyState
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiBorder
import com.erumi.nativeapp.ui.theme.ErumiError
import com.erumi.nativeapp.ui.theme.ErumiMuted
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.ErumiSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    onResumeWatch: (WatchProgress) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showConfirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WatchProgress?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy \u2022 h:mm a", Locale.getDefault()) }

    val filteredItems = remember(state.historyItems, searchQuery) {
        if (searchQuery.isBlank()) {
            state.historyItems
        } else {
            state.historyItems.filter {
                it.animeTitle.contains(searchQuery.trim(), ignoreCase = true)
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
                            placeholder = { Text("Filter history...", color = ErumiMuted) },
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
                        Text(text = "Watch History", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ErumiPrimaryText)
                    }
                },
                actions = {
                    if (state.historyItems.isNotEmpty()) {
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search History",
                                tint = ErumiPrimaryText
                            )
                        }
                        IconButton(onClick = { showConfirmClear = true }) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = ErumiError)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ErumiBackground)
            )
        }
    ) { padding ->
        if (state.historyItems.isEmpty()) {
            MediaEmptyState(
                message = "No watch history",
                subtitle = "Anime you watch will show up here",
                icon = Icons.Default.History,
                modifier = Modifier.padding(padding)
            )
        } else if (filteredItems.isEmpty()) {
            MediaEmptyState(
                message = "No watch history matches '$searchQuery'",
                subtitle = "Try searching with a different title",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 0.dp, bottom = 80.dp)
            ) {
                items(
                    items = filteredItems,
                    key = { "${it.animeId}_${it.episodeNumber}" }
                ) { item ->
                    val dateStr = dateFormatter.format(Date(item.updatedAt))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ErumiSurface)
                            .clickable { onResumeWatch(item) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.animeTitle,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = ErumiPrimaryText)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Episode ${item.episodeNumber} \u2022 $dateStr",
                                style = MaterialTheme.typography.labelSmall.copy(color = ErumiSecondaryText)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { item.progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = ErumiPrimary,
                                trackColor = ErumiBorder
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { pendingDelete = item },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete entry",
                                    tint = ErumiSecondaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onResumeWatch(item) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    tint = ErumiPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showConfirmClear) {
            AlertDialog(
                onDismissRequest = { showConfirmClear = false },
                title = { Text(text = "Clear History?", color = ErumiPrimaryText) },
                text = { Text(text = "This will remove all watch progress and clear Continue Watching from Home.", color = ErumiSecondaryText) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllHistory()
                            showConfirmClear = false
                        }
                    ) {
                        Text("Clear All", color = ErumiError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmClear = false }) {
                        Text("Cancel", color = ErumiSecondaryText)
                    }
                }
            )
        }

        pendingDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(text = "Remove Entry?", color = ErumiPrimaryText) },
                text = { Text(text = "Remove ${item.animeTitle} Ep ${item.episodeNumber} from history?", color = ErumiSecondaryText) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteHistoryEntry(item)
                            pendingDelete = null
                        }
                    ) {
                        Text("Remove", color = ErumiError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel", color = ErumiSecondaryText)
                    }
                }
            )
        }
    }
}
