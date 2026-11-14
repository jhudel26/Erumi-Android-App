package com.erumi.nativeapp.manga.presentation.reader

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.erumi.nativeapp.manga.data.sources.MangaHttpClient
import com.erumi.nativeapp.manga.data.local.MangaPreferencesDataStore
import com.erumi.nativeapp.manga.data.repository.MangaRepository
import com.erumi.nativeapp.manga.domain.models.MangaImageScale
import com.erumi.nativeapp.manga.domain.models.MangaPage
import com.erumi.nativeapp.manga.domain.models.MangaReaderSettings
import com.erumi.nativeapp.manga.domain.models.MangaReadingDirection
import com.erumi.nativeapp.manga.domain.models.MangaReadingMode
import com.erumi.nativeapp.manga.domain.models.MangaReadingProgress
import com.erumi.nativeapp.manga.domain.models.MangaResult
import com.erumi.nativeapp.manga.presentation.components.MangaErrorState
import com.erumi.nativeapp.shared.ui.components.rememberShimmerBrush
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder

data class ReaderArgs(
    val mangaUrl: String,
    val chapterUrl: String,
    val mangaId: String,
    val title: String,
    val coverUrl: String?,
    val chapterId: String,
    val chapterName: String,
    val chapterNumber: Float?
)

data class ReaderUiState(
    val pages: List<MangaPage> = emptyList(),
    val currentPage: Int = 0,
    val settings: MangaReaderSettings = MangaReaderSettings(),
    val controlsVisible: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val chapters: List<Pair<String, String>> = emptyList()
)

class MangaReaderViewModel(
    application: Application,
    private val args: ReaderArgs
) : AndroidViewModel(application) {
    private val repo = MangaRepository(application)
    private val prefs = MangaPreferencesDataStore(application)
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState = _uiState.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            prefs.readerSettings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val progress = repo.getProgress(args.mangaId)
            val startPage = if (progress?.chapterId == args.chapterId) progress.pageIndex else 0
            when (val result = repo.getPages(args.chapterUrl)) {
                is MangaResult.Success -> {
                    val chapters = when (val ch = repo.getChapters(args.mangaUrl, args.mangaId)) {
                        is MangaResult.Success -> ch.data.map { it.url to it.name }
                        else -> emptyList()
                    }
                    _uiState.update {
                        it.copy(
                            pages = result.data,
                            currentPage = startPage.coerceIn(0, (result.data.size - 1).coerceAtLeast(0)),
                            chapters = chapters,
                            isLoading = false
                        )
                    }
                }
                is MangaResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(controlsVisible = !it.controlsVisible) }
    }

    fun onPageChanged(index: Int) {
        _uiState.update { it.copy(currentPage = index) }
        scheduleSave(index)
    }

    fun updateSettings(settings: MangaReaderSettings) {
        viewModelScope.launch { prefs.updateReaderSettings(settings) }
    }

    fun saveNow() {
        val state = _uiState.value
        if (state.pages.isEmpty()) return
        viewModelScope.launch {
            if (!prefs.readerSettings.first().autoSaveProgress) return@launch
            val pageCount = state.pages.size
            val page = state.currentPage.coerceIn(0, pageCount - 1)
            val percentage = if (pageCount <= 1) 100f else ((page + 1f) / pageCount) * 100f
            repo.saveProgress(
                MangaReadingProgress(
                    mangaId = args.mangaId,
                    sourceId = "manhuaplus",
                    mangaTitle = args.title,
                    coverUrl = args.coverUrl,
                    chapterId = args.chapterId,
                    chapterUrl = args.chapterUrl,
                    chapterName = args.chapterName,
                    chapterNumber = args.chapterNumber,
                    pageIndex = page,
                    pageCount = pageCount,
                    percentage = percentage,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun scheduleSave(index: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            _uiState.update { it.copy(currentPage = index) }
            saveNow()
        }
    }

    fun adjacentChapter(next: Boolean): Pair<String, String>? {
        val list = _uiState.value.chapters
        val idx = list.indexOfFirst { it.first == args.chapterUrl }
        if (idx < 0) return null
        // Chapters from the source are newest-first, so a higher chapter number sits at a
        // LOWER index. "Next" chapter (higher number) is therefore at idx - 1, and the
        // "Previous" chapter (lower number) is at idx + 1.
        return if (next) list.getOrNull(idx - 1) else list.getOrNull(idx + 1)
    }

    class Factory(
        private val application: Application,
        private val args: ReaderArgs
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MangaReaderViewModel(application, args) as T
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaReaderScreen(
    argsEncoded: ReaderArgs,
    onBack: () -> Unit,
    onOpenChapter: (ReaderArgs) -> Unit,
    viewModel: MangaReaderViewModel = viewModel(
        factory = MangaReaderViewModel.Factory(
            LocalContext.current.applicationContext as Application,
            argsEncoded
        )
    )
) {
    val state by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }

    DisposableEffect(state.controlsVisible) {
        activity?.let { act ->
            val window = act.window
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (state.controlsVisible) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveNow()
            activity?.let { act ->
                val window = act.window
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler {
        viewModel.saveNow()
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ErumiPrimary)
            }
            state.error != null -> MangaErrorState(state.error!!, onRetry = viewModel::load)
            else -> {
                if (state.settings.readingMode == MangaReadingMode.VERTICAL) {
                    VerticalReader(
                        pages = state.pages,
                        initialPage = state.currentPage,
                        scaleMode = state.settings.imageScale,
                        onPageVisible = viewModel::onPageChanged,
                        onToggleControls = viewModel::toggleControls,
                        adjacentChapter = viewModel::adjacentChapter,
                        onOpenAdjacentChapter = { (url, name) ->
                            viewModel.saveNow()
                            onOpenChapter(argsEncoded.copy(chapterUrl = url, chapterName = name, chapterId = url, chapterNumber = null))
                        }
                    )
                } else {
                    HorizontalReader(
                        pages = state.pages,
                        initialPage = state.currentPage,
                        rtl = state.settings.readingDirection == MangaReadingDirection.RTL,
                        scaleMode = state.settings.imageScale,
                        onPageChanged = viewModel::onPageChanged,
                        onToggleControls = viewModel::toggleControls
                    )
                }
            }
        }

        if (state.controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        viewModel.saveNow()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ErumiPrimaryText)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(argsEncoded.title, color = ErumiPrimaryText, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(argsEncoded.chapterName, color = ErumiSecondaryText, fontSize = 12.sp)
                    }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = ErumiPrimaryText)
                    }
                }
                if (state.pages.isNotEmpty()) {
                    Text(
                        text = "Page ${state.currentPage + 1} / ${state.pages.size}",
                        color = ErumiSecondaryText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }

        // Prev / Next bottom bar is only shown when user reaches the end of the chapter
        val isEndOfChapter = state.pages.isNotEmpty() && state.currentPage >= state.pages.lastIndex
        if (isEndOfChapter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val prev = viewModel.adjacentChapter(false)
                TextButton(
                    onClick = {
                        prev?.let { (url, name) ->
                            viewModel.saveNow()
                            onOpenChapter(argsEncoded.copy(chapterUrl = url, chapterName = name, chapterId = url, chapterNumber = null))
                        }
                    },
                    enabled = prev != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, tint = if (prev != null) ErumiPrimaryText else ErumiSecondaryText)
                    Text("Prev", color = if (prev != null) ErumiPrimaryText else ErumiSecondaryText)
                }
                val next = viewModel.adjacentChapter(true)
                if (next != null) {
                    Button(
                        onClick = {
                            viewModel.saveNow()
                            onOpenChapter(
                                argsEncoded.copy(
                                    chapterUrl = next.first,
                                    chapterName = next.second,
                                    chapterId = next.first,
                                    chapterNumber = null
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary)
                    ) { Text("Next Chapter") }
                } else {
                    Text("No next chapter", color = ErumiSecondaryText, fontSize = 12.sp)
                }
            }
        }

        if (showSettings) {
            ReaderSettingsPanel(
                settings = state.settings,
                onChange = viewModel::updateSettings,
                onClose = { showSettings = false },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun VerticalReader(
    pages: List<MangaPage>,
    initialPage: Int,
    scaleMode: MangaImageScale,
    onPageVisible: (Int) -> Unit,
    onToggleControls: () -> Unit,
    adjacentChapter: (Boolean) -> Pair<String, String>?,
    onOpenAdjacentChapter: (Pair<String, String>) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceAtMost(pages.lastIndex.coerceAtLeast(0)))
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex
        }.collect(onPageVisible)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(pages, key = { index, page -> "${page.index}-$index" }) { _, page ->
            ReaderPage(
                url = page.imageUrl,
                scaleMode = scaleMode,
                fillWidth = true,
                onDoubleTap = onToggleControls
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "End of Chapter",
                    color = ErumiSecondaryText,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val prev = adjacentChapter(false)
                    if (prev != null) {
                        TextButton(
                            onClick = { onOpenAdjacentChapter(prev) }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, tint = ErumiPrimaryText)
                            Text("Prev Chapter", color = ErumiPrimaryText)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    val next = adjacentChapter(true)
                    if (next != null) {
                        Button(
                            onClick = { onOpenAdjacentChapter(next) },
                            colors = ButtonDefaults.buttonColors(containerColor = ErumiPrimary)
                        ) {
                            Text("Next Chapter")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        }
                    } else {
                        Text("No Next Chapter", color = ErumiSecondaryText, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HorizontalReader(
    pages: List<MangaPage>,
    initialPage: Int,
    rtl: Boolean,
    scaleMode: MangaImageScale,
    onPageChanged: (Int) -> Unit,
    onToggleControls: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceAtMost(pages.lastIndex.coerceAtLeast(0)),
        pageCount = { pages.size }
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = rtl,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        ReaderPage(url = pages[page].imageUrl, scaleMode = scaleMode, fillWidth = false, onDoubleTap = onToggleControls)
    }
}

@Composable
private fun ReaderPage(
    url: String,
    scaleMode: MangaImageScale,
    fillWidth: Boolean,
    onDoubleTap: () -> Unit = {}
) {
    val contentScale = when (scaleMode) {
        MangaImageScale.FIT_WIDTH -> ContentScale.FillWidth
        MangaImageScale.FIT_HEIGHT -> ContentScale.Fit
        MangaImageScale.ORIGINAL -> ContentScale.None
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fillWidth) Modifier else Modifier.fillMaxSize())
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        val imageRequest = remember(url) {
            val referer = when {
                url.contains("2xstorage") || url.contains("manganato") || url.contains("manganelo") ||
                url.contains("readmanganato") || url.contains("natomanga") -> "https://www.manganato.gg/"
                url.contains("manhuaplus") -> "https://manhuaplus.org/"
                url.contains("asura") || url.contains("asuracomic") -> "https://asuracomic.net/"
                url.contains("flamecomics") -> "https://flamecomics.xyz/"
                url.contains("manhuascan") || url.contains("manhuaus") -> "https://manhuascan.io/"
                else -> null
            }
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .setHeader("User-Agent", MangaHttpClient.USER_AGENT)
                .setHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .apply {
                    if (referer != null) {
                        setHeader("Referer", referer)
                    }
                }
                .build()
        }

        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxWidth(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(rememberShimmerBrush())
                )
            },
            error = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Image failed to load", color = ErumiSecondaryText)
                }
            }
        )
    }
}

@Composable
private fun ReaderSettingsPanel(
    settings: MangaReaderSettings,
    onChange: (MangaReaderSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .background(ErumiBackground.copy(alpha = 0.95f))
            .padding(16.dp)
            .width(280.dp)
    ) {
        Text("Reader settings", color = ErumiPrimaryText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            onChange(
                settings.copy(
                    readingMode = if (settings.readingMode == MangaReadingMode.VERTICAL)
                        MangaReadingMode.HORIZONTAL else MangaReadingMode.VERTICAL
                )
            )
        }) { Text("Mode: ${settings.readingMode.name}", color = ErumiPrimaryText) }
        TextButton(onClick = {
            onChange(
                settings.copy(
                    readingDirection = if (settings.readingDirection == MangaReadingDirection.LTR)
                        MangaReadingDirection.RTL else MangaReadingDirection.LTR
                )
            )
        }) { Text("Direction: ${settings.readingDirection.name}", color = ErumiPrimaryText) }
        TextButton(onClick = {
            val next = when (settings.imageScale) {
                MangaImageScale.FIT_WIDTH -> MangaImageScale.FIT_HEIGHT
                MangaImageScale.FIT_HEIGHT -> MangaImageScale.ORIGINAL
                MangaImageScale.ORIGINAL -> MangaImageScale.FIT_WIDTH
            }
            onChange(settings.copy(imageScale = next))
        }) { Text("Scale: ${settings.imageScale.name}", color = ErumiPrimaryText) }
        TextButton(onClick = onClose) { Text("Close", color = ErumiPrimary) }
    }
}

fun decodeReaderArgs(
    mangaUrl: String,
    chapterUrl: String,
    mangaId: String,
    title: String,
    cover: String,
    chapterId: String,
    chapterName: String,
    chapterNumber: String
): ReaderArgs {
    fun dec(value: String) = URLDecoder.decode(value, "UTF-8")
    return ReaderArgs(
        mangaUrl = dec(mangaUrl),
        chapterUrl = dec(chapterUrl),
        mangaId = dec(mangaId),
        title = dec(title),
        coverUrl = dec(cover).ifBlank { null },
        chapterId = dec(chapterId),
        chapterName = dec(chapterName),
        chapterNumber = chapterNumber.toFloatOrNull()
    )
}
