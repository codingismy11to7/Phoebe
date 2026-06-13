package com.phoebe.app.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.playlistEntryKey
import com.phoebe.app.platform.currentTimeMs
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val PlaylistTrackReorderCommitHoldMs = 2_000L
private val PlaylistTrackReorderAutoScrollEdge = 132.dp
private val PlaylistTrackReorderHandleSize = 36.dp

class PlaylistTrackReorderState {
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set

    var draggingTrackKey by mutableStateOf<String?>(null)
        private set

    private var sourceTracks: List<Track> = emptyList()
    private var enabled: Boolean = false
    private var rowStepPx: Float = 1f
    private var autoScrollEdgePx: Float = 1f
    private var onMove: (Int, Int) -> Unit = { _, _ -> }
    private var dragStartIndex: Int? = null
    private var dragCurrentIndex: Int? = null
    private var pointerOffsetInDraggedItemPx: Float = 0f
    private var committedTrackKeys: List<String>? = null
    private var committedUntilMs: Long = 0L
    private var dragOffsetPx by mutableFloatStateOf(0f)
    private var pointerYRoot by mutableFloatStateOf(Float.NaN)
    private var listBounds by mutableStateOf<Rect?>(null)
    private val itemTopByTrackKey = hashMapOf<String, Float>()
    private val itemHeightByTrackKey = hashMapOf<String, Float>()

    val isDragging: Boolean
        get() = draggingTrackKey != null

    fun update(
        sourceTracks: List<Track>,
        enabled: Boolean,
        rowStepPx: Float,
        autoScrollEdgePx: Float,
        onMove: (Int, Int) -> Unit,
    ) {
        this.sourceTracks = sourceTracks
        this.enabled = enabled
        this.rowStepPx = rowStepPx.coerceAtLeast(1f)
        this.autoScrollEdgePx = autoScrollEdgePx.coerceAtLeast(1f)
        this.onMove = onMove
        if (!isDragging) {
            val committedKeys = committedTrackKeys
            if (committedKeys == null) {
                tracks = sourceTracks
            } else {
                val sourceKeys = sourceTracks.map { it.reorderKey() }
                if (
                    sourceKeys == committedKeys ||
                    sourceKeys.toSet() != committedKeys.toSet() ||
                    currentTimeMs() >= committedUntilMs
                ) {
                    committedTrackKeys = null
                    tracks = sourceTracks
                }
            }
        }
        if (!enabled && isDragging) {
            cancelDrag()
        }
    }

    fun listModifier(): Modifier =
        Modifier.onGloballyPositioned { listBounds = it.boundsInRoot() }

    fun itemModifier(track: Track): Modifier = itemModifier(track.reorderKey())

    private fun itemModifier(trackKey: String): Modifier {
        val isDraggingTrack = draggingTrackKey == trackKey
        val visualOffset = if (isDraggingTrack) dragOffsetPx else 0f
        return Modifier
            .offset { IntOffset(0, visualOffset.roundToInt()) }
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                itemTopByTrackKey[trackKey] = bounds.top - visualOffset
                itemHeightByTrackKey[trackKey] = bounds.height
            }
            .zIndex(if (isDraggingTrack) 2f else 0f)
    }

    fun startDrag(track: Track, index: Int, pointerRoot: Offset) {
        if (!enabled || index !in tracks.indices) return
        val trackKey = track.reorderKey()
        draggingTrackKey = trackKey
        dragStartIndex = index
        dragCurrentIndex = index
        dragOffsetPx = 0f
        pointerYRoot = pointerRoot.y
        val rowTop = itemTopByTrackKey[trackKey] ?: (pointerRoot.y - rowStepPx / 2f)
        val rowHeight = itemHeightByTrackKey[trackKey] ?: rowStepPx
        pointerOffsetInDraggedItemPx = (pointerRoot.y - rowTop).coerceIn(0f, rowHeight)
    }

    fun drag(pointerRoot: Offset, dragDeltaY: Float) {
        if (!enabled || draggingTrackKey == null) return
        pointerYRoot = pointerRoot.y
        if (!syncDragOffsetToPointer()) {
            dragOffsetPx += dragDeltaY
        }
        settleDraggedIndex()
    }

    fun endDrag() {
        val from = dragStartIndex
        val to = dragCurrentIndex
        if (from != null && to != null && from != to) {
            committedTrackKeys = tracks.map { it.reorderKey() }
            committedUntilMs = currentTimeMs() + PlaylistTrackReorderCommitHoldMs
        }
        resetDrag(restoreSource = false)
        if (from != null && to != null && from != to) {
            onMove(from, to)
        }
    }

    fun cancelDrag() {
        resetDrag(restoreSource = true)
    }

    suspend fun autoScroll(listState: LazyListState) {
        while (isDragging && enabled) {
            val delta = autoScrollDelta()
            if (delta != 0f) {
                val consumed = listState.scrollBy(delta)
                val reorderDelta = if (abs(consumed) > 0.5f) consumed else delta
                if (abs(reorderDelta) > 0.5f) {
                    applyDragDelta(reorderDelta)
                }
            }
            delay(16L)
        }
    }

    private fun autoScrollDelta(): Float {
        val bounds = listBounds ?: return 0f
        val y = pointerYRoot
        if (!y.isFinite()) return 0f
        val edge = min(autoScrollEdgePx, bounds.height / 3f).coerceAtLeast(1f)
        val maxDelta = (rowStepPx * 0.55f).coerceAtLeast(12f)
        return when {
            y < bounds.top + edge -> {
                -maxDelta * ((bounds.top + edge - y) / edge).coerceIn(0f, 1f)
            }
            y > bounds.bottom - edge -> {
                maxDelta * ((y - (bounds.bottom - edge)) / edge).coerceIn(0f, 1f)
            }
            else -> 0f
        }
    }

    private fun applyDragDelta(deltaY: Float) {
        dragOffsetPx += deltaY
        settleDraggedIndex()
    }

    private fun syncDragOffsetToPointer(): Boolean {
        val draggingKey = draggingTrackKey ?: return false
        val rowTop = itemTopByTrackKey[draggingKey] ?: return false
        if (!pointerYRoot.isFinite()) return false
        dragOffsetPx = pointerYRoot - pointerOffsetInDraggedItemPx - rowTop
        return true
    }

    private fun settleDraggedIndex() {
        val draggingKey = draggingTrackKey ?: return
        var currentIndex = dragCurrentIndex ?: return
        if (tracks.getOrNull(currentIndex)?.reorderKey() != draggingKey) {
            currentIndex = tracks.indexOfFirst { it.reorderKey() == draggingKey }
            if (currentIndex < 0) return
            dragCurrentIndex = currentIndex
        }

        if (dragOffsetPx > 0f && currentIndex < tracks.lastIndex) {
            val step = moveStepPx(currentIndex, currentIndex + 1)
            if (dragOffsetPx > step / 2f) {
                tracks = tracks.moved(currentIndex, currentIndex + 1)
                currentIndex += 1
                dragCurrentIndex = currentIndex
                dragOffsetPx -= step
            }
        } else if (dragOffsetPx < 0f && currentIndex > 0) {
            val step = moveStepPx(currentIndex, currentIndex - 1)
            if (dragOffsetPx < -step / 2f) {
                tracks = tracks.moved(currentIndex, currentIndex - 1)
                currentIndex -= 1
                dragCurrentIndex = currentIndex
                dragOffsetPx += step
            }
        }
        val lowerClamp = moveStepPx(currentIndex, (currentIndex - 1).coerceAtLeast(0)) / 2f
        val upperClamp = moveStepPx(currentIndex, (currentIndex + 1).coerceAtMost(tracks.lastIndex)) / 2f
        if (currentIndex == tracks.lastIndex && dragOffsetPx > upperClamp) {
            dragOffsetPx = upperClamp
        }
        if (currentIndex == 0 && dragOffsetPx < -lowerClamp) {
            dragOffsetPx = -lowerClamp
        }
    }

    private fun moveStepPx(from: Int, to: Int): Float {
        if (from == to || from !in tracks.indices || to !in tracks.indices) return rowStepPx
        val fromTop = itemTopByTrackKey[tracks[from].reorderKey()]
        val toTop = itemTopByTrackKey[tracks[to].reorderKey()]
        val measured = if (fromTop != null && toTop != null) abs(toTop - fromTop) else null
        return measured?.takeIf { it > 1f } ?: rowStepPx
    }

    private fun resetDrag(restoreSource: Boolean) {
        draggingTrackKey = null
        dragStartIndex = null
        dragCurrentIndex = null
        pointerOffsetInDraggedItemPx = 0f
        dragOffsetPx = 0f
        pointerYRoot = Float.NaN
        if (restoreSource) {
            committedTrackKeys = null
            tracks = sourceTracks
        }
    }
}

@Composable
fun rememberPlaylistTrackReorderState(
    tracks: List<Track>,
    enabled: Boolean,
    listState: LazyListState,
    rowStep: Dp,
    onMove: (Int, Int) -> Unit,
): PlaylistTrackReorderState {
    val density = LocalDensity.current
    val latestOnMove by rememberUpdatedState(onMove)
    val state = remember { PlaylistTrackReorderState() }
    state.update(
        sourceTracks = tracks,
        enabled = enabled,
        rowStepPx = with(density) { rowStep.toPx() },
        autoScrollEdgePx = with(density) { PlaylistTrackReorderAutoScrollEdge.toPx() },
        onMove = latestOnMove,
    )
    val draggingTrackKey = state.draggingTrackKey
    LaunchedEffect(draggingTrackKey, enabled, listState) {
        if (draggingTrackKey != null && enabled) {
            state.autoScroll(listState)
        }
    }
    return state
}

@Composable
fun PlaylistTrackReorderHandle(
    state: PlaylistTrackReorderState,
    track: Track,
    index: Int,
    modifier: Modifier = Modifier,
) {
    var origin by remember(track.id) { mutableStateOf(Offset.Zero) }
    val latestTrack by rememberUpdatedState(track)
    val latestIndex by rememberUpdatedState(index)
    Box(
        modifier
            .size(PlaylistTrackReorderHandleSize)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(track.id, state) {
                detectDragGestures(
                    onDragStart = { offset -> state.startDrag(latestTrack, latestIndex, origin + offset) },
                    onDrag = { change, drag ->
                        change.consume()
                        state.drag(origin + change.position, drag.y)
                    },
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.endDrag() },
                )
            }
            .semantics { contentDescription = "Reorder ${track.title}" },
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(PhoebeIcon.Drag, tint = PhoebeUi.mutedText, modifier = Modifier.size(17.dp))
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    val copy = toMutableList()
    val item = copy.removeAt(from)
    copy.add(to, item)
    return copy
}

fun Track.reorderKey(): String = playlistEntryKey()

fun Track.playlistRemovalKey(): String = playlistEntryKey()
