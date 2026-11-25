package com.erumi.nativeapp.core.updater

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erumi.nativeapp.ui.theme.BgCard
import com.erumi.nativeapp.ui.theme.BgMain
import com.erumi.nativeapp.ui.theme.BgSurfaceElevated
import com.erumi.nativeapp.ui.theme.BorderSubtle
import com.erumi.nativeapp.ui.theme.DangerRed
import com.erumi.nativeapp.ui.theme.SuccessGreen
import com.erumi.nativeapp.ui.theme.TealLight
import com.erumi.nativeapp.ui.theme.TealPrimary
import com.erumi.nativeapp.ui.theme.TextPrimary
import com.erumi.nativeapp.ui.theme.TextSecondary
import com.erumi.nativeapp.ui.theme.WarningAmber
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialogHost(
    state: UpdateUiState,
    onStartDownload: (UpdateManifest, Boolean) -> Unit,
    onRemindLater: (UpdateManifest) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallApk: (File, UpdateManifest, Boolean) -> Unit,
    onOpenInstallSettings: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateUiState.Idle) return

    val isMandatory = when (state) {
        is UpdateUiState.Available -> state.isMandatory
        is UpdateUiState.Downloading -> state.isMandatory
        is UpdateUiState.Verifying -> state.isMandatory
        is UpdateUiState.ReadyToInstall -> state.isMandatory
        is UpdateUiState.PermissionRequired -> state.isMandatory
        is UpdateUiState.Error -> state.isMandatory
        else -> false
    }

    Dialog(
        onDismissRequest = { if (!isMandatory) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatory,
            dismissOnClickOutside = !isMandatory
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BgSurfaceElevated,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                when (state) {
                    UpdateUiState.Idle -> Unit
                    is UpdateUiState.Checking -> {
                        UpdateHeader(
                            icon = Icons.Default.SystemUpdate,
                            iconTint = TealPrimary,
                            title = "Checking for Updates"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = TealPrimary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Contacting update server...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    is UpdateUiState.UpToDate -> {
                        UpdateHeader(
                            icon = Icons.Default.CheckCircle,
                            iconTint = SuccessGreen,
                            title = "You're Up to Date"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Erumi v${state.currentVersionName} (${state.currentVersionCode}) is the latest version.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealPrimary,
                                    contentColor = BgMain
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateUiState.Available -> {
                        UpdateHeader(
                            icon = Icons.Default.SystemUpdate,
                            iconTint = if (state.isMandatory) WarningAmber else TealPrimary,
                            title = if (state.isMandatory) {
                                "Important Update Required"
                            } else {
                                "A New Update Is Available"
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (state.isMandatory) {
                            Text(
                                text = if (state.isUnsupportedVersion) {
                                    "Your current version is no longer supported. Please update to continue using Erumi."
                                } else {
                                    "This update includes critical fixes and is required to continue."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmber
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        VersionBadgeRow(
                            currentVersion = "v${state.currentVersionName} (${state.currentVersionCode})",
                            latestVersion = "v${state.manifest.versionName} (${state.manifest.versionCode})",
                            fileSizeBytes = state.manifest.fileSizeBytes
                        )

                        if (state.manifest.releaseNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "What's New",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TealLight
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BgCard)
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.manifest.releaseNotes.forEach { note ->
                                    Text(
                                        text = "• $note",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!state.isMandatory) {
                                TextButton(onClick = { onRemindLater(state.manifest) }) {
                                    Text("REMIND ME LATER", color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Button(
                                onClick = { onStartDownload(state.manifest, state.isMandatory) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealPrimary,
                                    contentColor = BgMain
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("UPDATE NOW", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateUiState.Downloading -> {
                        UpdateHeader(
                            icon = Icons.Default.Download,
                            iconTint = TealPrimary,
                            title = "Downloading update..."
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Erumi v${state.manifest.versionName}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (state.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                                color = TealPrimary,
                                trackColor = BgCard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                color = TealPrimary,
                                trackColor = BgCard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val sizeLabel = if (state.totalBytes > 0L) {
                                "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}"
                            } else {
                                formatBytes(state.downloadedBytes)
                            }
                            Text(
                                text = sizeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = if (state.totalBytes > 0L) "${state.progressPercent}%" else "...",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = TealLight
                            )
                        }

                        if (!state.isMandatory) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onCancelDownload) {
                                    Text("CANCEL", color = TextSecondary)
                                }
                            }
                        }
                    }

                    is UpdateUiState.Verifying -> {
                        UpdateHeader(
                            icon = Icons.Default.Security,
                            iconTint = TealPrimary,
                            title = "Download completed"
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = TealPrimary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Verifying installation package...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        UpdateHeader(
                            icon = Icons.Default.VerifiedUser,
                            iconTint = SuccessGreen,
                            title = "Ready to install"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Erumi v${state.manifest.versionName} has been verified and is ready to install. Your library, history, and settings will be preserved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (!state.isMandatory) {
                                TextButton(onClick = onDismiss) {
                                    Text("LATER", color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Button(
                                onClick = {
                                    onInstallApk(state.apkFile, state.manifest, state.isMandatory)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealPrimary,
                                    contentColor = BgMain
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("INSTALL UPDATE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateUiState.PermissionRequired -> {
                        UpdateHeader(
                            icon = Icons.Default.Security,
                            iconTint = WarningAmber,
                            title = "Permission Required"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Android requires you to enable \"Allow from this source\" for Erumi before installing updates. Tap Open Settings, enable the toggle, and return to the app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (!state.isMandatory) {
                                TextButton(onClick = onDismiss) {
                                    Text("CANCEL", color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Button(
                                onClick = onOpenInstallSettings,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealPrimary,
                                    contentColor = BgMain
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("OPEN SETTINGS", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateUiState.Error -> {
                        UpdateHeader(
                            icon = Icons.Default.ErrorOutline,
                            iconTint = DangerRed,
                            title = "Update Error"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (!state.isMandatory) {
                                TextButton(onClick = onDismiss) {
                                    Text("CLOSE", color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (state.canRetry) {
                                OutlinedButton(
                                    onClick = onRetry,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("RETRY", color = TealLight, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateHeader(
    icon: ImageVector,
    iconTint: Color,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
    }
}

@Composable
private fun VersionBadgeRow(
    currentVersion: String,
    latestVersion: String,
    fileSizeBytes: Long?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Current", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(currentVersion, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Latest", style = MaterialTheme.typography.labelSmall, color = TealLight)
            val suffix = fileSizeBytes?.takeIf { it > 0L }?.let { " • ${formatBytes(it)}" }.orEmpty()
            Text(
                text = "$latestVersion$suffix",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = TealLight
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
