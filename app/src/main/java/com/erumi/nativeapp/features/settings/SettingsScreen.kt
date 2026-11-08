package com.erumi.nativeapp.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erumi.nativeapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val settings = state.settings

    var showQualityDialog by remember { mutableStateOf(false) }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
        ) {
            // PLAYER SETTINGS
            item {
                SettingsSection(title = "PLAYBACK") {
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
                SettingsSection(title = "DOWNLOADS") {
                    SettingsSwitchItem(
                        icon = Icons.Default.Wifi,
                        title = "Download Over Wi-Fi Only",
                        subtitle = "Prevent downloads on cellular mobile data",
                        checked = settings.wifiOnlyDownloads,
                        onCheckedChange = { viewModel.setWifiOnlyDownloads(it) }
                    )
                }
            }

            // STORAGE & CACHE
            item {
                SettingsSection(title = "STORAGE") {
                    SettingsItem(
                        icon = Icons.Default.CleaningServices,
                        title = "Clear Image Cache",
                        subtitle = "Free up memory and disk cache for posters & banners",
                        onClick = { viewModel.clearImageCache() }
                    )
                }
            }

            // ABOUT
            item {
                SettingsSection(title = "ABOUT ERUMI") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        subtitle = "Erumi v1.0.0"
                    )
                    HorizontalDivider(color = ErumiBorder, thickness = 0.5.dp)
                    SettingsItem(
                        icon = Icons.Default.Code,
                        title = "Architecture",
                        subtitle = "100% Native Kotlin • Jetpack Compose • Media3"
                    )
                }
            }
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

        // Cache Cleared Snackbar
        state.cacheClearedMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissCacheMessage() },
                title = { Text(text = "Cache Cleared", color = ErumiPrimaryText) },
                text = { Text(text = msg, color = ErumiSecondaryText) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissCacheMessage() }) {
                        Text(text = "OK", color = ErumiPrimary)
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
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
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
