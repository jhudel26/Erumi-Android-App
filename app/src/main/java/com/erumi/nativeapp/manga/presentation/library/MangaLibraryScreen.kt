package com.erumi.nativeapp.manga.presentation.library

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.manga.data.repository.MangaRepository
import com.erumi.nativeapp.manga.domain.models.MangaLibraryItem
import com.erumi.nativeapp.manga.domain.models.MangaLibraryStatus
import com.erumi.nativeapp.manga.presentation.components.MangaCoverImage
import com.erumi.nativeapp.shared.ui.components.MediaEmptyState
import com.erumi.nativeapp.manga.presentation.components.MangaFilterChipRow
import com.erumi.nativeapp.ui.components.PressScale
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.TextPrimary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MangaLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = MangaRepository(application)
    val items: StateFlow<List<MangaLibraryItem>> = repo.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateStatus(mangaId: String, status: MangaLibraryStatus) {
        viewModelScope.launch { repo.updateLibraryStatus(mangaId, status) }
    }

    fun remove(mangaId: String) {
        viewModelScope.launch { repo.removeFromLibrary(mangaId) }
    }
}

@Composable
fun MangaLibraryScreen(
    onOpenManga: (mangaId: String, title: String) -> Unit,
    viewModel: MangaLibraryViewModel = viewModel()
) {
    val items by viewModel.items.collectAsState()
    val sortedItems = remember(items) {
        items.sortedByDescending { it.updatedAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ErumiBackground)
            .statusBarsPadding()
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
        Text(
            text = "Your saved manhua collection and bookmarked series",
            style = MaterialTheme.typography.bodyMedium,
            color = ErumiSecondaryText,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
        )

        if (sortedItems.isEmpty()) {
            MediaEmptyState(
                message = "Your library is empty",
                subtitle = "Bookmark your favorite manhua to track what you're reading"
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sortedItems, key = { it.mangaId }) { item ->
                    var menu by remember { mutableStateOf(false) }
                    PressScale(
                        onClick = { onOpenManga(item.mangaId, item.title) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            MangaCoverImage(
                                url = item.coverUrl,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.status.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.labelSmall.copy(color = ErumiPrimary),
                                modifier = Modifier.clickable { menu = true }
                            )
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                MangaLibraryStatus.entries.forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }) },
                                        onClick = {
                                            viewModel.updateStatus(item.mangaId, status)
                                            menu = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Remove from Library", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        viewModel.remove(item.mangaId)
                                        menu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
