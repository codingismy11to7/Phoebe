package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.isLikedSongsPlaylist

@Composable
fun SidebarPlaylistDropRow(
    playlist: Playlist,
    selectedPlaylistId: String?,
    onPlaylist: (Playlist) -> Unit,
) {
    val controller = LocalDragDrop.current
    val playlistActions = LocalPlaylistActions.current
    val isHovered = (controller?.draggedTrack != null || controller?.draggedPlaylist != null) &&
        controller.isHovering(playlist.id)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .draggablePlaylist(playlist)
            .playlistDropTarget(playlist)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isHovered) PhoebeUi.accentLight.copy(alpha = 0.32f) else PhoebeUi.sidebar)
            .border(
                BorderStroke(
                    width = if (isHovered) 1.5.dp else 0.dp,
                    color = if (isHovered) PhoebeUi.accentLight else PhoebeUi.sidebar,
                ),
                RoundedCornerShape(10.dp),
            )
            .padding(2.dp),
    ) {
        PlaylistRow(
            icon = if (playlist.isLikedSongsPlaylist()) PhoebeIcon.Heart else null,
            title = playlist.title,
            subtitle = "${playlist.trackCount} songs",
            thumbUrl = playlist.thumbUrl,
            accent = playlist.isLikedSongsPlaylist(),
            active = playlist.id == selectedPlaylistId,
            onClick = { onPlaylist(playlist) },
            onLongClick = { playlistActions.onShufflePlaylist(playlist) },
        )
    }
}

@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun SidebarPlaylistDropRowPreview() {
    PhoebeTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PhoebeUi.sidebar)
                .padding(14.dp)
                .width(208.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SidebarPlaylistDropRow(
                playlist = Playlist(
                    id = "liked",
                    title = "Liked Songs",
                    trackCount = 42,
                ),
                selectedPlaylistId = "liked",
                onPlaylist = {},
            )
            SidebarPlaylistDropRow(
                playlist = Playlist(
                    id = "late-night",
                    title = "Late Night Radio",
                    trackCount = 128,
                ),
                selectedPlaylistId = "liked",
                onPlaylist = {},
            )
            SidebarPlaylistDropRow(
                playlist = Playlist(
                    id = "weekend",
                    title = "Weekend Drive",
                    trackCount = 26,
                ),
                selectedPlaylistId = null,
                onPlaylist = {},
            )
        }
    }
}
