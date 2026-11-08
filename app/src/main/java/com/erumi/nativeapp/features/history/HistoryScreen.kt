package com.erumi.nativeapp.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.domain.model.WatchProgress
import com.erumi.nativeapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

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

    Scaffold(
        containerColor = ErumiBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Watch History", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ErumiPrimaryText)
                    }
                },
                actions = {
                    if (state.historyItems.isNotEmpty()) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = ErumiMuted, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "No watch history", style = MaterialTheme.typography.titleMedium.copy(color = ErumiSecondaryText))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Anime you watch will show up here", style = MaterialTheme.typography.bodySmall.copy(color = ErumiMuted))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                items(
                    items = state.historyItems,
                    key = { "${it.animeId}_${it.episodeNumber}" }
                ) { item ->
                    val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(item.updatedAt))
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
                                text = "Episode ${item.episodeNumber} • $dateStr",
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

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = ErumiPrimary,
                            modifier = Modifier.size(28.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { pendingDelete = item },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete history entry",
                                tint = ErumiError,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showConfirmClear) {
            AlertDialog(
                onDismissRequest = { showConfirmClear = false },
                title = { Text(text = "Clear Watch History?", color = ErumiPrimaryText) },
                text = { Text(text = "Are you sure you want to remove all your watch history? This also clears Continue Watching on Home. This action cannot be undone.", color = ErumiSecondaryText) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearAllHistory()
                        showConfirmClear = false
                    }) {
                        Text(text = "Clear All", color = ErumiError, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmClear = false }) {
                        Text(text = "Cancel", color = ErumiSecondaryText)
                    }
                },
                containerColor = ErumiElevatedSurface
            )
        }

        pendingDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(text = "Delete this entry?", color = ErumiPrimaryText) },
                text = {
                    Text(
                        text = "Remove ${item.animeTitle} — Episode ${item.episodeNumber} from history?",
                        color = ErumiSecondaryText
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteHistoryEntry(item)
                        pendingDelete = null
                    }) {
                        Text(text = "Delete", color = ErumiError, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(text = "Cancel", color = ErumiSecondaryText)
                    }
                },
                containerColor = ErumiElevatedSurface
            )
        }
    }
}
