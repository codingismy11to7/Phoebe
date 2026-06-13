package com.phoebe.app.ui

import com.phoebe.app.domain.Track

/**
 * Maps a tap from a filtered/visible track list back into the unfiltered source queue,
 * rotated so Up Next includes the rest of the playlist after the chosen start point.
 */
fun playbackQueueForVisibleTrack(
    sourceTracks: List<Track>,
    visibleTracks: List<Track>,
    visibleIndex: Int,
): Pair<List<Track>, Int> {
    val visibleTrack = visibleTracks.getOrNull(visibleIndex) ?: return visibleTracks to visibleIndex
    val sourceIndex = sourceTracks.indexOfFirst { it.reorderKey() == visibleTrack.reorderKey() }
    return if (sourceIndex >= 0) sourceTracks.rotatedForPlayback(sourceIndex) to 0 else visibleTracks to visibleIndex
}

private fun List<Track>.rotatedForPlayback(startIndex: Int): List<Track> =
    if (startIndex > 0 && startIndex in indices) drop(startIndex) + take(startIndex) else this
