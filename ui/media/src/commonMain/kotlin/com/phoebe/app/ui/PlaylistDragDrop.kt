package com.phoebe.app.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.canAddToLocalPlaylist
import com.phoebe.app.domain.canAddToPlexPlaylist
import com.phoebe.app.domain.isRemoteProviderPlaylist

class DragDropController {
    var draggedPlaylist by mutableStateOf<Playlist?>(null)
        private set
    var draggedTrack by mutableStateOf<Track?>(null)
        private set
    var pointer by mutableStateOf<Offset?>(null)
        private set
    private val targets = mutableStateMapOf<String, DropTarget>()

    val hoveringPlaylistTitle: String?
        get() = currentHover()?.title

    fun start(track: Track, initialPointer: Offset) {
        draggedPlaylist = null
        draggedTrack = track
        pointer = initialPointer
    }

    fun start(playlist: Playlist, initialPointer: Offset) {
        draggedTrack = null
        draggedPlaylist = playlist
        pointer = initialPointer
    }

    fun update(pointer: Offset) {
        this.pointer = pointer
    }

    fun end(): Playlist? {
        val hit = currentHover()
        draggedPlaylist = null
        draggedTrack = null
        pointer = null
        return hit
    }

    fun cancel() {
        draggedPlaylist = null
        draggedTrack = null
        pointer = null
    }

    fun register(playlist: Playlist, bounds: Rect) {
        targets[playlist.id] = DropTarget(playlist, bounds)
    }

    fun unregister(playlistId: String) {
        targets.remove(playlistId)
    }

    fun isHovering(playlistId: String): Boolean {
        if (draggedTrack == null && draggedPlaylist == null) return false
        val pt = pointer ?: return false
        return targets[playlistId]?.bounds?.contains(pt) == true
    }

    private fun currentHover(): Playlist? {
        val pt = pointer ?: return null
        return targets.values.firstOrNull { it.bounds.contains(pt) }?.playlist
    }

    private data class DropTarget(val playlist: Playlist, val bounds: Rect)
}

val LocalDragDrop = compositionLocalOf<DragDropController?> { null }

fun Modifier.draggableSong(
    track: Track,
    enabled: Boolean = true,
    immediate: Boolean = false,
): Modifier = composed {
    val controller = LocalDragDrop.current ?: return@composed this
    if (!LocalPlaylistDragEnabled.current) return@composed this
    val actions = LocalPlaylistActions.current
    val allowDrag = enabled && actions.playlistsEnabled &&
        (track.canAddToLocalPlaylist() || track.canAddToPlexPlaylist())
    if (!allowDrag) return@composed this
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(track.id, immediate) {
            val onStart: (Offset) -> Unit = { offset -> controller.start(track, origin + offset) }
            val onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit = { change, _ ->
                controller.update(origin + change.position)
            }
            val onEnd: () -> Unit = {
                val target = controller.end()
                if (target != null) actions.onAddTrackToPlaylist(target, track)
            }
            val onCancel: () -> Unit = { controller.cancel() }
            if (immediate) {
                detectDragGestures(
                    onDragStart = onStart,
                    onDrag = onDrag,
                    onDragEnd = onEnd,
                    onDragCancel = onCancel,
                )
            } else {
                detectDragGesturesAfterLongPress(
                    onDragStart = onStart,
                    onDrag = onDrag,
                    onDragEnd = onEnd,
                    onDragCancel = onCancel,
                )
            }
        }
}

fun Modifier.draggablePlaylist(
    playlist: Playlist,
    enabled: Boolean = true,
): Modifier = composed {
    val controller = LocalDragDrop.current ?: return@composed this
    if (!LocalPlaylistDragEnabled.current) return@composed this
    val actions = LocalPlaylistActions.current
    val allowDrag = enabled && actions.playlistsEnabled && playlist.isRemoteProviderPlaylist()
    if (!allowDrag) return@composed this
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(playlist.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset -> controller.start(playlist, origin + offset) },
                onDrag = { change, _ -> controller.update(origin + change.position) },
                onDragEnd = {
                    val target = controller.end()
                    if (target != null && target.id != playlist.id) {
                        actions.onCopyPlaylistToPlaylist(playlist, target)
                    }
                },
                onDragCancel = { controller.cancel() },
            )
        }
}

fun Modifier.playlistDropTarget(playlist: Playlist): Modifier = composed {
    val controller = LocalDragDrop.current ?: return@composed this
    DisposableEffect(playlist.id) {
        onDispose { controller.unregister(playlist.id) }
    }
    onGloballyPositioned { controller.register(playlist, it.boundsInRoot()) }
}
