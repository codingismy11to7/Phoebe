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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PlayHistoryScreen(
    kind: PlayHistoryKind,
    catalog: CatalogSnapshot,
    playHistory: PlayHistorySnapshot,
    resolvedTracksById: Map<String, Track> = emptyMap(),
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val catalogTrackIndexKey = catalog.trackIndexKey()
    val playHistoryKey = playHistory.derivationKey()
    val resolvedTracksKey = resolvedTracksById.keys.fold(0L) { acc, id -> acc * 31L + id.hashCode() }
    val rows by produceState<List<HomePlayedTrack>?>(null, kind, catalogTrackIndexKey, playHistoryKey, resolvedTracksKey) {
        value = withContext(Dispatchers.Default) {
            playHistoryRows(
                kind = kind,
                catalog = catalog,
                playHistory = playHistory,
                resolvedTracksById = resolvedTracksById,
            )
        }
    }
    val rankedTotal = when (kind) {
        PlayHistoryKind.MostPlayed -> playHistory.topMostPlayed.size
        PlayHistoryKind.RecentlyPlayed -> playHistory.topRecentlyPlayed.size
    }
    val showResolving = rows != null && rankedTotal > 0 && (rows?.size ?: 0) < rankedTotal
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayHistoryHeader(
            kind = kind,
            count = rows?.size,
            rankedTotal = rankedTotal.takeIf { showResolving },
            onBack = onBack,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val loaded = rows) {
                null -> PlayHistoryLoading(Modifier.fillMaxSize())
                else -> PlayHistoryTracks(
                    rows = loaded,
                    showPlayCount = kind == PlayHistoryKind.MostPlayed,
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PlayHistoryLoading(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = PhoebeUi.accentLight,
                strokeWidth = 3.dp,
                trackColor = PhoebeUi.progressTrack,
            )
            Text("Loading listening history…", color = PhoebeUi.secondaryText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PlayHistoryHeader(
    kind: PlayHistoryKind,
    count: Int?,
    rankedTotal: Int?,
    onBack: () -> Unit,
) {
    val title = when (kind) {
        PlayHistoryKind.RecentlyPlayed -> "Recently Played"
        PlayHistoryKind.MostPlayed -> "Most Played"
    }
    val subtitle = when (count) {
        null -> "Loading…"
        0 -> "No songs yet"
        1 -> if (rankedTotal != null && rankedTotal > 1) "1 of $rankedTotal songs" else "1 song"
        else -> if (rankedTotal != null && count < rankedTotal) "$count of $rankedTotal songs" else "$count songs"
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
            Text(title, color = PhoebeUi.primaryText, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = PhoebeUi.secondaryText, fontSize = 13.sp)
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
                sharedKey = null,
            )
        }
    }
}
