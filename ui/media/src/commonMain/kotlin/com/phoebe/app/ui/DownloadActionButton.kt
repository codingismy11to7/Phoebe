package com.phoebe.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.mergeDownloadCopiesById

@Composable
fun DownloadActionButton(
    label: String,
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val downloads = LocalDownloadStatus.current
    val downloadActions = LocalDownloadActions.current
    val uniqueTracks = remember(tracks) { tracks.mergeDownloadCopiesById() }
    val summary = downloads.summarize(uniqueTracks)
    val total = summary.total
    val complete = summary.complete
    val failed = summary.failed
    val unavailable = summary.unavailable
    val allComplete = total > 0 && complete == total
    val allDownloadableComplete = complete > 0 && complete + unavailable == total
    val confirmationKey = remember(uniqueTracks) {
        uniqueTracks.fold(17) { hash, track -> hash * 31 + track.id.hashCode() }
    }
    var confirmDelete by remember(confirmationKey) { mutableStateOf(false) }
    var confirmCancel by remember(confirmationKey) { mutableStateOf(false) }
    val progress = when {
        summary.active > 0 -> downloadActionProgress(
            completed = complete,
            activeCount = summary.active,
            activeProgress = summary.activeProgress,
            total = total,
        )
        allComplete || allDownloadableComplete -> 1f
        failed > 0 && total > 0 -> (complete.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        else -> null
    }
    val isActive = summary.active > 0
    val hasFailures = failed > 0
    val statusLabel = when {
        isActive && total > 1 -> downloadActionPercentLabel(progress ?: 0f)
        hasFailures && total > 1 -> downloadFailureStatusLabel(failed)
        allDownloadableComplete && unavailable > 0 -> "$unavailable skipped"
        else -> null
    }
    val labelText = when {
        isActive -> activeDownloadActionLabel()
        allComplete || allDownloadableComplete -> "Downloaded"
        hasFailures && complete > 0 -> "Partly Downloaded"
        hasFailures -> "Download Failed"
        else -> label
    }
    val iconColor = when {
        isActive || allComplete || allDownloadableComplete || hasFailures -> PhoebeUi.accentLight
        else -> PhoebeUi.mutedText
    }
    LibraryToolbarButton(
        icon = if (allComplete || allDownloadableComplete) PhoebeIcon.Check else PhoebeIcon.Download,
        label = labelText,
        value = statusLabel,
        iconTint = iconColor,
        modifier = modifier,
        onClick = {
            if (isActive) {
                confirmCancel = true
            } else if (allComplete || allDownloadableComplete) {
                confirmDelete = true
            } else {
                onClick()
            }
        },
        leadingContent = if (isActive) {
            {
                CircularProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.size(13.dp),
                    color = PhoebeUi.accentLight,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            null
        },
    )
    if (confirmCancel) {
        val noun = if (total == 1) "song" else "songs"
        val bodyTarget = if (total == 1) "this song" else "these $total $noun"
        ConfirmDeleteDownloadsDialog(
            title = "Cancel Download?",
            body = "Stop the current download and remove anything already downloaded for $bodyTarget from this device?",
            confirmLabel = "Cancel Download",
            onDismiss = { confirmCancel = false },
            onConfirm = {
                if (onCancel != null) onCancel() else downloadActions.onCancelDownloadedTracks(uniqueTracks)
                confirmCancel = false
            },
        )
    }
    if (confirmDelete) {
        val noun = if (total == 1) "song" else "songs"
        ConfirmDeleteDownloadsDialog(
            title = "Delete Downloads?",
            body = "Remove $total downloaded $noun from this device? Empty folders from the download will be cleaned up too.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                if (onDelete != null) onDelete() else downloadActions.onDeleteDownloadedTracks(uniqueTracks)
                confirmDelete = false
            },
        )
    }
}

fun activeDownloadActionLabel(): String =
    "Downloading"

fun downloadActionProgress(
    completed: Int,
    activeCount: Int,
    activeProgress: Float,
    total: Int,
): Float? {
    if (total <= 0 || activeCount <= 0) return null
    val completedProgress = completed.coerceIn(0, total).toFloat()
    return ((completedProgress + activeProgress.coerceAtLeast(0f)) / total.toFloat()).coerceIn(0f, 1f)
}

fun downloadFailureStatusLabel(failed: Int): String =
    if (failed == 1) "1 failed" else "$failed failed"

internal fun downloadActionPercentLabel(progress: Float): String {
    val normalized = progress.coerceIn(0f, 1f)
    val percent = (normalized * 100f).toInt()
    val visiblePercent = if (normalized > 0f && percent == 0) 1 else percent
    return "${visiblePercent.coerceIn(0, 100)}%"
}
