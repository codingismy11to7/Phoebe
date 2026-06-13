package com.phoebe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoebe.app.domain.CatalogSyncPhase

@Composable
fun CatalogMenuSyncIndicator(modifier: Modifier = Modifier) {
    val syncState = LocalCatalogSyncState.current
    if (!syncState.isActive) return
    val message = syncState.message ?: "Syncing library…"
    val totalPlaylists = syncState.totalPlaylists
    val detail = syncState.detail ?: when (syncState.phase) {
        CatalogSyncPhase.LoadingLibrary -> when {
            syncState.message?.contains("Organizing", ignoreCase = true) == true ->
                syncState.detail ?: "Linking artists to albums on device"
            totalPlaylists != null && totalPlaylists > 0 ->
                "${syncState.loadedAlbums} albums · $totalPlaylists playlists"
            syncState.loadedAlbums > 0 -> "${syncState.loadedAlbums} albums"
            else -> "From your server"
        }
        CatalogSyncPhase.LoadingSongs -> when {
            syncState.loadedTracks > 0 && syncState.totalTracks != null ->
                "${syncState.loadedTracks} / ${syncState.totalTracks} songs"
            syncState.loadedTracks > 0 -> "${syncState.loadedTracks} songs indexed"
            else -> null
        }
        CatalogSyncPhase.RefreshingPlaylists -> when {
            syncState.warmedPlaylists > 0 && totalPlaylists != null ->
                "${syncState.warmedPlaylists} / $totalPlaylists playlists"
            totalPlaylists != null -> "$totalPlaylists playlists"
            else -> null
        }
        CatalogSyncPhase.FinishingArtwork -> "Album and artist artwork"
        CatalogSyncPhase.Persisting -> "Writing to local database"
        CatalogSyncPhase.RestoringCache -> "Reading cached library"
        else -> when {
            syncState.loadedTracks > 0 && syncState.totalTracks != null ->
                "${syncState.loadedTracks} / ${syncState.totalTracks} songs"
            syncState.loadedTracks > 0 -> "${syncState.loadedTracks} songs"
            syncState.loadedAlbums > 0 -> "${syncState.loadedAlbums} albums"
            syncState.warmedPlaylists > 0 && syncState.totalPlaylists != null ->
                "${syncState.warmedPlaylists} / ${syncState.totalPlaylists} playlists"
            else -> null
        }
    }
    val progress = syncState.progress
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .size(16.dp)
                    .semantics { contentDescription = "Library sync in progress" },
                color = PhoebeUi.accentLight,
                strokeWidth = 2.dp,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .semantics { contentDescription = "Library sync in progress" },
                color = PhoebeUi.accentLight,
                strokeWidth = 2.dp,
            )
        }
        Column {
            Text(
                message,
                color = PhoebeUi.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    color = PhoebeUi.mutedText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
