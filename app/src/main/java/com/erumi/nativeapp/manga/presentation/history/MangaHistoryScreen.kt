package com.erumi.nativeapp.manga.presentation.history

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.manga.data.repository.MangaRepository
import com.erumi.nativeapp.manga.domain.models.MangaHistoryEntry
import com.erumi.nativeapp.manga.presentation.components.MangaCoverImage
import com.erumi.nativeapp.shared.ui.components.MediaEmptyState
import com.erumi.nativeapp.ui.theme.BgSurface
import com.erumi.nativeapp.ui.theme.BorderSubtle
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiError
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.TextDim
import com.erumi.nativeapp.ui.theme.TextPrimary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MangaHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = MangaRepository(application)
    val items = repo.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(mangaId: String) = viewModelScope.launch { repo.deleteHistory(mangaId) }
    fun clear() = viewModelScope.launch { repo.clearHistory() }
}

@Composable
fun MangaHistoryScreen(
    onOpenManga: (MangaHistoryEntry) -> Unit,
    onBackClick: (() -> Unit)? = null,
    viewModel: MangaHistoryViewModel = viewModel()
) {
    val items by viewModel.items.collectAsState()
    var showConfirmClear by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<MangaHistoryEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ErumiBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = "Reading History",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (items.isNotEmpty()) {
                IconButton(onClick = { showConfirmClear = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = ErumiError
                    )
                }
            }
        }

        if (items.isEmpty()) {
            MediaEmptyState(
                message = "No reading history yet",
                subtitle = "Manga you read will appear here so you can easily pick up where you left off"
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.mangaId }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurface)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { onOpenManga(entry) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MangaCoverImage(
                            url = entry.coverUrl,
                            contentDescription = entry.title,
                            modifier = Modifier.width(56.dp).aspectRatio(0.7f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = entry.chapterName ?: "No chapter",
                                style = MaterialTheme.typography.labelSmall.copy(color = ErumiSecondaryText),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.openedAt)),
                                style = MaterialTheme.typography.labelSmall.copy(color = TextDim)
                            )
                        }
                        IconButton(onClick = { entryToDelete = entry }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextDim)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text(text = "Clear Reading History", color = ErumiPrimaryText) },
            text = { Text(text = "Are you sure you want to clear your entire reading history?", color = ErumiSecondaryText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clear()
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

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(text = "Remove from History", color = ErumiPrimaryText) },
            text = { Text(text = "Remove '${entry.title}' from your reading history?", color = ErumiSecondaryText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.remove(entry.mangaId)
                        entryToDelete = null
                    }
                ) {
                    Text("Remove", color = ErumiError)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel", color = ErumiSecondaryText)
                }
            }
        )
    }
}
