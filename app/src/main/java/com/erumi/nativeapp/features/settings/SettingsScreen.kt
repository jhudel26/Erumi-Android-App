package com.erumi.nativeapp.features.settings

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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.BuildConfig
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onRequestBatteryOptimization: (android.content.Intent) -> Unit = {},
    onSwitchToManga: () -> Unit = {},
    onReplayTour: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.settings

    var showQualityDialog by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val tourState = LocalErumiTourState.current

    LaunchedEffect(tourState?.currentStep) {
        when (tourState?.currentStep) {
            ErumiTourStep.ANIME_SETTINGS_PLAYBACK -> listState.animateScrollToItem(0)
            ErumiTourStep.ANIME_SETTINGS_DOWNLOADS -> listState.animateScrollToItem(1)
            ErumiTourStep.ANIME_SETTINGS_EXPERIENCE -> listState.animateScrollToItem(3)
            ErumiTourStep.ANIME_SETTINGS_STORAGE -> listState.animateScrollToItem(2)
            else -> Unit
        }
    }

    // Show snackbar when cache cleared message arrives
    LaunchedEffect(state.cacheClearedMessage) {
        state.cacheClearedMessage?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
                viewModel.dismissCacheMessage()
            }
        }
    }

    Scaffold(
        containerColor = ErumiBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
            contentPadding = PaddingValues(top = 0.dp, bottom = 80.dp)
        ) {
            // PLAYER SETTINGS
            item {
                SettingsSection(
                    title = "PLAYBACK",
                    modifier = Modifier.tourHighlight(TourTargetKey.ANIME_SETTINGS_PLAYBACK)
                ) {
                    SettingsItem(
                        icon = Icons.Default.HighQuality,
                        title = "Preferred Quality",
                        subtitle = settings.preferredQuality,
                        onClick = { showQualityDialog = true }
                    )
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    SettingsSwitchItem(
                        icon = Icons.Default.SkipNext,
                        title = "Auto-Next Episode",
                        subtitle = "Automatically play the next episode when current ends",
                        checked = settings.autoNextEpisode,
                        onCheckedChange = { viewModel.setAutoNext(it) }
                    )
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    SettingsSwitchItem(
                        icon = Icons.Default.FastForward,
                        title = "Skip Intro Automatically",
                        subtitle = "Show 85-second intro skip shortcut",
                        checked = settings.skipIntro,
                        onCheckedChange = { viewModel.setSkipIntro(it) }
                    )
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    SettingsItem(
                        icon = Icons.Default.Headphones,
                        title = "Default Audio",
                        subtitle = if (settings.defaultAudio == "sub") "Japanese (Subbed)" else "English (Dubbed)",
                        onClick = {
                            viewModel.setDefaultAudio(if (settings.defaultAudio == "sub") "dub" else "sub")
                        }
                    )
                }
            }

            // DOWNLOADS
            item {
                SettingsSection(
                    title = "DOWNLOADS",
                    modifier = Modifier.tourHighlight(TourTargetKey.ANIME_SETTINGS_DOWNLOADS)
                ) {
                    SettingsSwitchItem(
                        icon = Icons.Default.Wifi,
                        title = "Download Over Wi-Fi Only",
                        subtitle = "Prevent downloads on cellular mobile data",
                        checked = settings.wifiOnlyDownloads,
                        onCheckedChange = { viewModel.setWifiOnlyDownloads(it) }
                    )
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        SettingsItem(
                            icon = Icons.Default.BatteryChargingFull,
                            title = "Background Downloads",
                            subtitle = if (state.isIgnoringBatteryOptimizations) {
                                "Downloads continue when app is minimized"
                            } else {
                                "Enable for uninterrupted background downloads"
                            },
                            onClick = {
                                if (!state.isIgnoringBatteryOptimizations) {
                                    val intent = viewModel.requestBatteryOptimization()
                                    if (intent != null) {
                                        onRequestBatteryOptimization(intent)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // STORAGE & CACHE
            item {
                SettingsSection(
                    title = "STORAGE",
                    modifier = Modifier.tourHighlight(TourTargetKey.ANIME_SETTINGS_STORAGE)
                ) {
                    SettingsItem(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear Image Cache",
                        subtitle = "Free up memory and disk cache for posters & banners",
                        onClick = { viewModel.clearImageCache() }
                    )
                }
            }

            // EXPERIENCE (Switch button in Settings)
            item {
                SettingsSection(
                    title = "EXPERIENCE",
                    modifier = Modifier.tourHighlight(TourTargetKey.ANIME_SETTINGS_EXPERIENCE)
                ) {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "Switch to Manhua",
                        subtitle = "Read Manhua, Manhwa, and Manga",
                        onClick = onSwitchToManga
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
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    SettingsItem(
                        icon = Icons.Default.Code,
                        title = "Architecture",
                        subtitle = "100% Native Kotlin \u2022 Jetpack Compose \u2022 Media3"
                    )
                }
            }

            // HELP & TUTORIAL
            item {
                SettingsSection(title = "HELP & TUTORIAL") {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        title = "App Tour & Walkthrough",
                        subtitle = "Replay the feature guide for Anime screens and streaming",
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
                experience = com.erumi.nativeapp.manga.data.local.ErumiExperience.ANIME,
                onDismiss = { showTutorial = false }
            )
        }

        // Quality Dialog
        if (showQualityDialog) {
            val qualities = listOf("1080p", "720p", "Auto")
            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                title = { Text(text = "Preferred Video Quality", color = ErumiPrimaryText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        qualities.forEach { q ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setPreferredQuality(q)
                                        showQualityDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = q, color = ErumiPrimaryText)
                                if (settings.preferredQuality == q) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = ErumiPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityDialog = false }) {
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = ErumiBackground,
                checkedTrackColor = ErumiPrimary,
                uncheckedThumbColor = ErumiSecondaryText,
                uncheckedTrackColor = ErumiBorder
            )
        )
    }
}
