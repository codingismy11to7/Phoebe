package com.phoebe.app.feature.history

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.phoebe.app.data.HomePlayedTrack
import com.phoebe.app.domain.PlayHistoryKind
import com.phoebe.app.domain.Track
import com.phoebe.app.ui.PhoebeIcon
import com.phoebe.app.ui.PhoebeIconView
import com.phoebe.app.ui.PhoebeUi

@Immutable
data class HistoryNowPlayingState(
    val trackId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
)

@Composable
fun PlayHistoryScreen(
    kind: PlayHistoryKind,
    state: PlayHistoryUiState,
    modifier: Modifier = Modifier,
    nowPlaying: HistoryNowPlayingState = HistoryNowPlayingState(),
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = 12.dp + bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayHistoryHeader(
            kind = kind,
            count = state.rows?.size,
            rankedTotal = state.rankedTotal.takeIf { state.showResolving },
            onBack = onBack,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val loaded = state.rows) {
                null -> PlayHistoryLoading(Modifier.fillMaxSize())
                else -> PlayHistoryTracks(
                    rows = loaded,
                    showPlayCount = kind == PlayHistoryKind.MostPlayed,
                    nowPlaying = nowPlaying,
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
            Text("Loading listening history...", color = PhoebeUi.secondaryText, fontSize = 14.sp)
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
        null -> "Loading..."
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
            Text(
                "Listening History".uppercase(),
                color = PhoebeUi.mutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
            )
            Text(title, color = PhoebeUi.primaryText, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = PhoebeUi.secondaryText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PlayHistoryTracks(
    rows: List<HomePlayedTrack>,
    showPlayCount: Boolean,
    nowPlaying: HistoryNowPlayingState,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        PlayHistoryEmpty("Play songs and your listening history will appear here.", modifier)
        return
    }
    val tracks = rows.map { it.track }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(rows, key = { _, row -> row.track.id }) { index, row ->
            HistoryTrackRow(
                row = row,
                isNowPlaying = row.track.id == nowPlaying.trackId,
                nowPlayingIsPlaying = nowPlaying.isPlaying,
                nowPlayingIsBuffering = nowPlaying.isBuffering,
                showPlayCount = showPlayCount,
                onPlay = { onPlayTracks(tracks, index) },
                onAddToUpNext = { onAddToUpNext(row.track) },
                onDownload = { onDownload(row.track) },
            )
        }
    }
}

@Composable
private fun HistoryTrackRow(
    row: HomePlayedTrack,
    isNowPlaying: Boolean,
    nowPlayingIsPlaying: Boolean,
    nowPlayingIsBuffering: Boolean,
    showPlayCount: Boolean,
    onPlay: () -> Unit,
    onAddToUpNext: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = row.track
    val rowShape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable(onClick = onPlay)
            .background(if (isNowPlaying) PhoebeUi.accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.045f))
            .border(BorderStroke(1.dp, if (isNowPlaying) PhoebeUi.accent.copy(alpha = 0.45f) else PhoebeUi.border), rowShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HistoryArtworkPlaceholder(
            seed = track.id,
            isNowPlaying = isNowPlaying,
            nowPlayingIsPlaying = nowPlayingIsPlaying,
            nowPlayingIsBuffering = nowPlayingIsBuffering,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track.title.ifBlank { "Unknown Title" },
                color = PhoebeUi.primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Unknown Artist" },
                color = PhoebeUi.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showPlayCount) {
                Text(playCountLabel(row.playCount), color = PhoebeUi.mutedText, fontSize = 11.sp)
            }
        }
        HistoryIconButton(PhoebeIcon.Queue, "Add to Up Next", onAddToUpNext)
        HistoryIconButton(PhoebeIcon.Download, "Download", onDownload)
    }
}

@Composable
private fun HistoryArtworkPlaceholder(
    seed: String,
    isNowPlaying: Boolean,
    nowPlayingIsPlaying: Boolean,
    nowPlayingIsBuffering: Boolean,
) {
    val colors = historyArtworkColors(seed)
    Box(
        Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        val icon = when {
            !isNowPlaying -> PhoebeIcon.Music
            nowPlayingIsBuffering -> PhoebeIcon.ActiveDot
            nowPlayingIsPlaying -> PhoebeIcon.Pause
            else -> PhoebeIcon.Play
        }
        PhoebeIconView(
            icon,
            tint = Color.White.copy(alpha = if (isNowPlaying) 0.92f else 0.76f),
            modifier = Modifier.size(if (isNowPlaying) 19.dp else 17.dp),
        )
    }
}

@Composable
private fun HistoryIconButton(
    icon: PhoebeIcon,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(PhoebeUi.subtleFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), CircleShape)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(icon, tint = PhoebeUi.secondaryText, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PlayHistoryEmpty(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = PhoebeUi.secondaryText, fontSize = 14.sp)
    }
}

private fun playCountLabel(count: Long): String =
    if (count == 1L) "1 play" else "$count plays"

private fun historyArtworkColors(seed: String): List<Color> {
    val hash = seed.hashCode()
    val hueA = (hash and 0xFF) / 255f
    val hueB = ((hash shr 8) and 0xFF) / 255f
    return listOf(
        Color(
            red = 0.18f + hueA * 0.24f,
            green = 0.22f + hueB * 0.22f,
            blue = 0.36f + (1f - hueA) * 0.28f,
            alpha = 1f,
        ),
        Color(
            red = 0.42f + hueB * 0.26f,
            green = 0.24f + (1f - hueA) * 0.22f,
            blue = 0.36f + hueA * 0.26f,
            alpha = 1f,
        ),
    )
}
