package com.erumi.nativeapp.manga.presentation.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.BuildConfig
import com.erumi.nativeapp.manga.data.local.MangaPreferencesDataStore
import com.erumi.nativeapp.manga.data.repository.MangaRepository
import com.erumi.nativeapp.manga.domain.models.MangaDownloadSettings
import com.erumi.nativeapp.manga.domain.models.MangaImageScale
import com.erumi.nativeapp.manga.domain.models.MangaReaderSettings
import com.erumi.nativeapp.manga.domain.models.MangaReadingDirection
import com.erumi.nativeapp.manga.domain.models.MangaReadingMode
import com.erumi.nativeapp.shared.ui.tour.ErumiTourStep
import com.erumi.nativeapp.shared.ui.tour.LocalErumiTourState
import com.erumi.nativeapp.shared.ui.tour.TourTargetKey
import com.erumi.nativeapp.shared.ui.tour.tourHighlight
import com.erumi.nativeapp.ui.theme.ErumiBackground
import com.erumi.nativeapp.ui.theme.ErumiBorder
import com.erumi.nativeapp.ui.theme.ErumiElevatedSurface
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiPrimaryText
import com.erumi.nativeapp.ui.theme.ErumiSecondary
import com.erumi.nativeapp.ui.theme.ErumiSecondaryText
import com.erumi.nativeapp.ui.theme.ErumiSurface
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MangaSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = MangaPreferencesDataStore(application)
    private val repo = MangaRepository(application)

    val readerSettings = prefs.readerSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaReaderSettings())
    val downloadSettings = prefs.downloadSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaDownloadSettings())
    val activeSource = prefs.activeSourceId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "manhuaplus")
    val sources = repo.listSources()

    fun updateReader(settings: MangaReaderSettings) = viewModelScope.launch { prefs.updateReaderSettings(settings) }
    fun updateDownloads(settings: MangaDownloadSettings) = viewModelScope.launch { prefs.updateDownloadSettings(settings) }
    fun setSource(id: String) = viewModelScope.launch { repo.setActiveSource(id) }
    fun clearHistory() = viewModelScope.launch { repo.clearHistory() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaSettingsScreen(
    onSwitchExperience: () -> Unit,
    onBack: () -> Unit = {},
    onReplayTour: (() -> Unit)? = null,
    viewModel: MangaSettingsViewModel = viewModel()
) {
    val reader by viewModel.readerSettings.collectAsState()
    val downloads by viewModel.downloadSettings.collectAsState()
    val activeSource by viewModel.activeSource.collectAsState()

    var showReadingModeDialog by remember { mutableStateOf(false) }
    var showDirectionDialog by remember { mutableStateOf(false) }
    var showImageScaleDialog by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val tourState = LocalErumiTourState.current

    LaunchedEffect(tourState?.currentStep) {
        when (tourState?.currentStep) {
            ErumiTourStep.MANGA_SETTINGS_READER -> listState.animateScrollToItem(0)
            ErumiTourStep.MANGA_SETTINGS_SOURCE -> listState.animateScrollToItem(1)
            ErumiTourStep.MANGA_SETTINGS_DOWNLOADS -> listState.animateScrollToItem(2)
            ErumiTourStep.MANGA_SETTINGS_STORAGE -> listState.animateScrollToItem(3)
            ErumiTourStep.MANGA_SETTINGS_EXPERIENCE -> listState.animateScrollToItem(4)
            else -> Unit
        }
    }

    Scaffold(
        containerColor = ErumiBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Settings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ErumiBackground)
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            // READER SETTINGS
            item {
                SettingsSection(
                    title = "READER",
                    modifier = Modifier.tourHighlight(TourTargetKey.MANGA_SETTINGS_READER)
                ) {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "Reading Mode",
                        subtitle = reader.readingMode.name.lowercase().replaceFirstChar { it.titlecase() },
                        onClick = { showReadingModeDialog = true }
                    )
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        title = "Reading Direction",
                        subtitle = reader.readingDirection.name,
                        onClick = { showDirectionDialog = true }
                    )
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    SettingsItem(
                        icon = Icons.Default.ZoomIn,
                        title = "Image Scale",
                        subtitle = reader.imageScale.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() },
                        onClick = { showImageScaleDialog = true }
                    )
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    SettingsSwitchItem(
                        icon = Icons.Default.Image,
                        title = "Fullscreen Reader",
                        subtitle = "Hide system UI while reading",
                        checked = reader.fullscreen,
                        onCheckedChange = { viewModel.updateReader(reader.copy(fullscreen = it)) }
                    )
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    SettingsSwitchItem(
                        icon = Icons.Default.Check,
                        title = "Auto-Save Progress",
                        subtitle = "Automatically save reading position",
                        checked = reader.autoSaveProgress,
                        onCheckedChange = { viewModel.updateReader(reader.copy(autoSaveProgress = it)) }
                    )
                }
            }

            // SOURCE
            item {
                SettingsSection(
                    title = "SOURCE",
                    modifier = Modifier.tourHighlight(TourTargetKey.MANGA_SETTINGS_SOURCE)
                ) {
                    viewModel.sources.forEach { source ->
                        SettingsItem(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = source.name,
                            subtitle = if (source.id == activeSource) "Active" else "Available",
                            onClick = { viewModel.setSource(source.id) }
                        )
                        if (source != viewModel.sources.last()) {
                            HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                        }
                    }
                }
            }

            // DOWNLOADS
            item {
                SettingsSection(
                    title = "DOWNLOADS",
                    modifier = Modifier.tourHighlight(TourTargetKey.MANGA_SETTINGS_DOWNLOADS)
                ) {
                    SettingsSwitchItem(
                        icon = Icons.Default.Wifi,
                        title = "Download Over Wi-Fi Only",
                        subtitle = "Prevent downloads on cellular mobile data",
                        checked = downloads.wifiOnly,
                        onCheckedChange = { viewModel.updateDownloads(downloads.copy(wifiOnly = it)) }
                    )
                }
            }

            // STORAGE
            item {
                SettingsSection(
                    title = "STORAGE",
                    modifier = Modifier.tourHighlight(TourTargetKey.MANGA_SETTINGS_STORAGE)
                ) {
                    SettingsItem(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear Reading History",
                        subtitle = "Remove all reading history",
                        onClick = { viewModel.clearHistory() }
                    )
                }
            }

            // EXPERIENCE (Switch button in Settings)
            item {
                SettingsSection(
                    title = "EXPERIENCE",
                    modifier = Modifier.tourHighlight(TourTargetKey.MANGA_SETTINGS_EXPERIENCE)
                ) {
                    SettingsItem(
                        icon = Icons.Default.Movie,
                        title = "Switch to Anime",
                        subtitle = "Watch Anime with Erumi",
                        onClick = onSwitchExperience
                    )
                }
            }

            // ABOUT
            item {
                SettingsSection(title = "ABOUT ERUMI") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        subtitle = "Erumi v${BuildConfig.VERSION_NAME}"
                    )
                }
            }

            // HELP & TUTORIAL
            item {
                SettingsSection(title = "HELP & TUTORIAL") {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        title = "Manga Tour & Walkthrough",
                        subtitle = "Replay the feature guide for Manga, catalog sources & gestures",
                        onClick = {
                            if (onReplayTour != null) {
                                onReplayTour()
                            } else {
                                showTutorial = true
                            }
                        }
                    )
                }
            }
        }

        if (showTutorial) {
            com.erumi.nativeapp.shared.ui.components.ExperienceTutorialDialog(
                experience = com.erumi.nativeapp.manga.data.local.ErumiExperience.MANGA,
                onDismiss = { showTutorial = false }
            )
        }

        // Reading Mode Dialog
        if (showReadingModeDialog) {
            val modes = listOf("Vertical", "Horizontal")
            AlertDialog(
                onDismissRequest = { showReadingModeDialog = false },
                title = { Text(text = "Reading Mode", color = ErumiPrimaryText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateReader(
                                            reader.copy(
                                                readingMode = if (mode == "Vertical") MangaReadingMode.VERTICAL else MangaReadingMode.HORIZONTAL
                                            )
                                        )
                                        showReadingModeDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = mode, color = ErumiPrimaryText)
                                if ((mode == "Vertical" && reader.readingMode == MangaReadingMode.VERTICAL) ||
                                    (mode == "Horizontal" && reader.readingMode == MangaReadingMode.HORIZONTAL)) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = ErumiPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showReadingModeDialog = false }) {
                        Text(text = "Done", color = ErumiPrimary)
                    }
                },
                containerColor = ErumiElevatedSurface
            )
        }

        // Direction Dialog
        if (showDirectionDialog) {
            val directions = listOf("LTR", "RTL")
            AlertDialog(
                onDismissRequest = { showDirectionDialog = false },
                title = { Text(text = "Reading Direction", color = ErumiPrimaryText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        directions.forEach { dir ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateReader(
                                            reader.copy(
                                                readingDirection = if (dir == "LTR") MangaReadingDirection.LTR else MangaReadingDirection.RTL
                                            )
                                        )
                                        showDirectionDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = dir, color = ErumiPrimaryText)
                                if ((dir == "LTR" && reader.readingDirection == MangaReadingDirection.LTR) ||
                                    (dir == "RTL" && reader.readingDirection == MangaReadingDirection.RTL)) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = ErumiPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDirectionDialog = false }) {
                        Text(text = "Done", color = ErumiPrimary)
                    }
                },
                containerColor = ErumiElevatedSurface
            )
        }

        // Image Scale Dialog
        if (showImageScaleDialog) {
            val scales = listOf("Fit Width", "Fit Height", "Original")
            AlertDialog(
                onDismissRequest = { showImageScaleDialog = false },
                title = { Text(text = "Image Scale", color = ErumiPrimaryText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        scales.forEach { scale ->
                            val scaleEnum = when (scale) {
                                "Fit Width" -> MangaImageScale.FIT_WIDTH
                                "Fit Height" -> MangaImageScale.FIT_HEIGHT
                                else -> MangaImageScale.ORIGINAL
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateReader(reader.copy(imageScale = scaleEnum))
                                        showImageScaleDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = scale, color = ErumiPrimaryText)
                                if (reader.imageScale == scaleEnum) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = ErumiPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showImageScaleDialog = false }) {
                        Text(text = "Done", color = ErumiPrimary)
                    }
                },
                containerColor = ErumiElevatedSurface
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = ErumiPrimary
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ErumiSurface)
                .padding(vertical = 4.dp),
            content = content
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ErumiSecondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(color = ErumiPrimaryText))
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall.copy(color = ErumiSecondaryText))
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ErumiSecondary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(color = ErumiPrimaryText))
                subtitle?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = it, style = MaterialTheme.typography.bodySmall.copy(color = ErumiSecondaryText))
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = ErumiPrimary)
        )
    }
}
