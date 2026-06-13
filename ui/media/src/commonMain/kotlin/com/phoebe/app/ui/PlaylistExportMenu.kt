package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.playlists.PlaylistExportFormat

@Composable
fun PlaylistExportMenu(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    actions: PlaylistActions = LocalPlaylistActions.current,
) {
    if (!playlist.isLocalPlaylist()) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .background(Color.White.copy(alpha = 0.04f))
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhoebeIconView(PhoebeIcon.Library, tint = PhoebeUi.secondaryText, modifier = Modifier.size(13.dp))
            Text("Export", color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("M3U8") },
                onClick = {
                    expanded = false
                    actions.onExportLocalPlaylist(playlist, PlaylistExportFormat.M3U8)
                },
            )
            DropdownMenuItem(
                text = { Text("Text") },
                onClick = {
                    expanded = false
                    actions.onExportLocalPlaylist(playlist, PlaylistExportFormat.Text)
                },
            )
            DropdownMenuItem(
                text = { Text("CSV") },
                onClick = {
                    expanded = false
                    actions.onExportLocalPlaylist(playlist, PlaylistExportFormat.Csv)
                },
            )
        }
    }
}
