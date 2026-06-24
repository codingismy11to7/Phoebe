package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.SmartPlaylist
import com.phoebe.app.domain.isLikedSongsPlaylist
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.domain.remoteProviderPrefix

@Composable
fun SmartPlaylistManagementActions(
    playlist: Playlist,
    modifier: Modifier = Modifier,
) {
    if (!playlist.isSmartPlaylist()) return
    val playlistActions = LocalPlaylistActions.current
    val smartPlaylist = playlistActions.smartPlaylists.firstOrNull { it.id == playlist.id } ?: return
    var menuExpanded by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { menuExpanded = true }) {
            Text("Manage", color = PhoebeUi.accentLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Save to provider") },
                onClick = {
                    menuExpanded = false
                    playlistActions.onSaveSmartPlaylistToProvider(playlist)
                },
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuExpanded = false
                    renaming = true
                },
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = {
                    menuExpanded = false
                    playlistActions.onDuplicateSmartPlaylist(smartPlaylist)
                },
            )
            DropdownMenuItem(
                text = { Text(if (smartPlaylist.enabled) "Disable" else "Enable") },
                onClick = {
                    menuExpanded = false
                    playlistActions.onSetSmartPlaylistEnabled(smartPlaylist, !smartPlaylist.enabled)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuExpanded = false
                    confirmingDelete = true
                },
            )
        }
    }

    if (renaming) {
        SmartPlaylistRenameDialog(
            playlist = smartPlaylist,
            onDismiss = { renaming = false },
            onConfirm = { title ->
                playlistActions.onRenameSmartPlaylist(smartPlaylist, title)
                renaming = false
            },
        )
    }
    if (confirmingDelete) {
        SmartPlaylistDeleteDialog(
            playlist = smartPlaylist,
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                playlistActions.onDeleteSmartPlaylist(smartPlaylist)
                confirmingDelete = false
            },
        )
    }
}

@Composable
fun PlaylistManagementMenuButton(
    playlist: Playlist,
    modifier: Modifier = Modifier,
) {
    if (!playlist.canShowPlaylistManagementMenu()) return
    val playlistActions = LocalPlaylistActions.current
    val smartPlaylist = playlistActions.smartPlaylists.firstOrNull { it.id == playlist.id }
    var menuExpanded by remember(playlist.id) { mutableStateOf(false) }
    var renamingSmartPlaylist by remember(playlist.id) { mutableStateOf(false) }
    var confirmingDelete by remember(playlist.id) { mutableStateOf(false) }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PhoebeIconView(
            PhoebeIcon.More,
            tint = PhoebeUi.secondaryText,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { menuExpanded = true }
                .padding(8.dp),
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (smartPlaylist != null) {
                DropdownMenuItem(
                    text = { Text("Save to provider") },
                    onClick = {
                        menuExpanded = false
                        playlistActions.onSaveSmartPlaylistToProvider(playlist)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuExpanded = false
                        renamingSmartPlaylist = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    onClick = {
                        menuExpanded = false
                        playlistActions.onDuplicateSmartPlaylist(smartPlaylist)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (smartPlaylist.enabled) "Disable" else "Enable") },
                    onClick = {
                        menuExpanded = false
                        playlistActions.onSetSmartPlaylistEnabled(smartPlaylist, !smartPlaylist.enabled)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuExpanded = false
                    confirmingDelete = true
                },
            )
        }
    }

    if (renamingSmartPlaylist && smartPlaylist != null) {
        SmartPlaylistRenameDialog(
            playlist = smartPlaylist,
            onDismiss = { renamingSmartPlaylist = false },
            onConfirm = { title ->
                playlistActions.onRenameSmartPlaylist(smartPlaylist, title)
                renamingSmartPlaylist = false
            },
        )
    }
    if (confirmingDelete) {
        ConfirmDeleteDownloadsDialog(
            title = "Delete playlist?",
            body = "Delete ${playlist.title}? Songs stay in your library.",
            confirmLabel = "Delete",
            onDismiss = { confirmingDelete = false },
            onConfirm = {
                playlistActions.onDeletePlaylist(playlist)
                confirmingDelete = false
            },
        )
    }
}

@Composable
private fun SmartPlaylistRenameDialog(
    playlist: SmartPlaylist,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(playlist.id) { mutableStateOf(playlist.title) }
    SmartPlaylistDialogFrame {
        Text("Rename smart playlist", color = PhoebeUi.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        PillTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Playlist name",
            contentDescription = "Smart playlist name",
            leadingIcon = PhoebeIcon.Edit,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PhoebeUi.secondaryText)
            }
            TextButton(onClick = { onConfirm(title) }) {
                Text("Save", color = PhoebeUi.accentLight, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun Playlist.canShowPlaylistManagementMenu(): Boolean =
    !isLikedSongsPlaylist() && (
        isSmartPlaylist() ||
            isLocalPlaylist() ||
            remoteProviderPrefix() == "plex"
        )

@Composable
private fun SmartPlaylistDeleteDialog(
    playlist: SmartPlaylist,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SmartPlaylistDialogFrame {
        Text("Delete smart playlist?", color = PhoebeUi.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Delete ${playlist.title}? This removes the smart rule only; songs stay in your library.",
            color = PhoebeUi.secondaryText,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PhoebeUi.secondaryText)
            }
            TextButton(onClick = onConfirm) {
                Text("Delete", color = PhoebeUi.accentLight, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SmartPlaylistDialogFrame(content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = {}) {
        Column(
            Modifier
                .widthIn(min = 300.dp, max = 430.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PhoebeUi.modalSurface)
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}
