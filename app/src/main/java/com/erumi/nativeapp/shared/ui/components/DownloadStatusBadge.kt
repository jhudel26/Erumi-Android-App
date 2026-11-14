package com.erumi.nativeapp.shared.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erumi.nativeapp.domain.model.DownloadStatus
import com.erumi.nativeapp.ui.theme.ErumiError
import com.erumi.nativeapp.ui.theme.ErumiMuted
import com.erumi.nativeapp.ui.theme.ErumiPrimary
import com.erumi.nativeapp.ui.theme.ErumiSecondary
import com.erumi.nativeapp.ui.theme.SuccessGreen

@Composable
fun DownloadStatusBadge(
    status: DownloadStatus?,
    modifier: Modifier = Modifier,
    progress: Int = 0,
    downloadedBytes: Long = 0L,
    fallbackText: String? = null
) {
    if (status == null) {
        if (!fallbackText.isNullOrBlank()) {
            Text(
                text = fallbackText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = ErumiMuted,
                modifier = modifier
            )
        }
        return
    }

    val mb = downloadedBytes / (1024 * 1024)
    val (label, tint, icon) = when (status) {
        DownloadStatus.COMPLETED -> Triple(
            "Ready Offline",
            SuccessGreen,
            Icons.Default.CheckCircle
        )
        DownloadStatus.DOWNLOADING -> {
            val text = if (progress > 0) {
                "Downloading $progress%${if (mb > 0) " (${mb} MB)" else ""}"
            } else if (mb > 0) {
                "Downloading (${mb} MB)..."
            } else {
                "Downloading..."
            }
            Triple(text, ErumiPrimary, Icons.Default.Downloading)
        }
        DownloadStatus.PENDING, DownloadStatus.QUEUED -> Triple(
            "Queued",
            ErumiSecondary,
            Icons.Default.Schedule
        )
        DownloadStatus.FAILED -> Triple(
            "Download failed",
            ErumiError,
            Icons.Default.ErrorOutline
        )
        DownloadStatus.PAUSED -> Triple(
            "Paused",
            ErumiMuted,
            Icons.Default.Schedule
        )
        DownloadStatus.CANCELLED -> Triple(
            "Cancelled",
            ErumiMuted,
            Icons.Default.ErrorOutline
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = tint
        )
    }
}
