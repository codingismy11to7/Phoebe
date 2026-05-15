package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.PlayHistoryKind
import com.phoebe.app.domain.Track

@Composable
internal fun PlayHistoryScreen(
    kind: PlayHistoryKind,
    catalog: CatalogSnapshot,
    playHistory: PlayHistorySnapshot,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val rows = remember(kind, catalog, playHistory) {
        playHistoryRows(kind, catalog, playHistory)
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PlayHistoryHeader(kind, rows.size, onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PlayHistoryTracks(
                rows = rows,
                showPlayCount = kind == PlayHistoryKind.MostPlayed,
                onPlayTracks = onPlayTracks,
                onAddToUpNext = onAddToUpNext,
                onDownload = onDownload,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlayHistoryHeader(kind: PlayHistoryKind, count: Int, onBack: () -> Unit) {
    val title = when (kind) {
        PlayHistoryKind.RecentlyPlayed -> "Recently Played"
        PlayHistoryKind.MostPlayed -> "Most Played"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .background(PhoebeUi.elevatedFill)
                .border(BorderStroke(1.dp, PhoebeUi.border), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(PhoebeIcon.Back, tint = PhoebeUi.primaryText, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Listening History".uppercase(), color = PhoebeUi.mutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
            Text(title, color = PhoebeUi.primaryText, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("$count songs", color = PhoebeUi.secondaryText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PlayHistoryTracks(
    rows: List<HomePlayedTrack>,
    showPlayCount: Boolean,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        RecentlyAddedEmpty("Play songs and your listening history will appear here.", modifier)
        return
    }
    val tracks = rows.map { it.track }
    val nowPlaying = LocalNowPlaying.current
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(rows, key = { _, row -> row.track.id }) { index, row ->
            ContentTrackRow(
                track = row.track,
                libraryColumns = SongIdentityColumns,
                onPlay = { onPlayTracks(tracks, index) },
                onAddToUpNext = { onAddToUpNext(row.track) },
                onDownload = { onDownload(row.track) },
                compactLayout = true,
                isNowPlaying = row.track.id == nowPlaying.trackId,
                nowPlayingIsPlaying = nowPlaying.isPlaying,
                nowPlayingIsBuffering = nowPlaying.isBuffering,
                playCount = if (showPlayCount) row.playCount else null,
                sharedKey = "song:${row.track.id}",
            )
        }
    }
}

private fun playHistoryRows(
    kind: PlayHistoryKind,
    catalog: CatalogSnapshot,
    playHistory: PlayHistorySnapshot,
): List<HomePlayedTrack> {
    val tracksById = allLoadedTracks(catalog).associateBy { it.id }
    return when (kind) {
        PlayHistoryKind.RecentlyPlayed -> playHistory.byTrack.entries
            .sortedByDescending { it.value }
            .mapNotNull { (trackId, playedAt) ->
                tracksById[trackId]?.let { track ->
                    HomePlayedTrack(track, lastPlayedMs = playedAt, playCount = playHistory.playCountByTrack[trackId] ?: 0L)
                }
            }
        PlayHistoryKind.MostPlayed -> playHistory.playCountByTrack.entries
            .filter { it.value > 0L }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenByDescending { playHistory.byTrack[it.key] ?: 0L })
            .mapNotNull { (trackId, count) ->
                tracksById[trackId]?.let { track ->
                    HomePlayedTrack(track, lastPlayedMs = playHistory.byTrack[trackId], playCount = count)
                }
            }
    }
}
