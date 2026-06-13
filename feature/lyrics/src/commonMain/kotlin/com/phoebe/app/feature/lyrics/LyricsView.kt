package com.phoebe.app.feature.lyrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoebe.app.domain.LyricsDocument
import com.phoebe.app.domain.LyricsLine
import com.phoebe.app.domain.LyricsLoadState
import com.phoebe.app.domain.LyricsSource
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.ui.DetailBackButton
import com.phoebe.app.ui.PhoebeIcon
import com.phoebe.app.ui.PhoebeIconView
import com.phoebe.app.ui.PhoebeUi
import com.phoebe.app.ui.SectionLabel

private const val LyricsAutoScrollPauseMs = 5_000L

@Composable
fun LyricsView(
    track: Track?,
    currentTrackId: String?,
    positionMs: Long,
    state: LyricsLoadState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRetry: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(PhoebeUi.shellTop)
            .padding(
                start = 24.dp,
                top = 22.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                end = 24.dp,
                bottom = 22.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LyricsHeader(track = track, source = (state as? LyricsLoadState.Loaded)?.document?.source, onBack = onBack)
        when {
            track == null -> LyricsEmptyState("Start a song to see lyrics here.")
            state is LyricsLoadState.Loading -> LyricsLoadingState()
            state is LyricsLoadState.Loaded -> LyricsDocumentView(
                document = state.document,
                positionMs = if (track.id == currentTrackId) positionMs else 0L,
                syncEnabled = track.id == currentTrackId,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            state is LyricsLoadState.NotFound -> LyricsRetryState("No lyrics found for this song yet.", onRetry)
            state is LyricsLoadState.Failed -> LyricsRetryState(state.message, onRetry)
            else -> LyricsEmptyState("Lyrics will appear here when a song is selected.")
        }
    }
}

@Composable
private fun LyricsHeader(track: Track?, source: LyricsSource?, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            DetailBackButton(onBack = onBack)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("Lyrics", PhoebeUi.accentLight)
            Text(
                track?.title ?: "No song playing",
                color = PhoebeUi.primaryText,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (track != null) {
                Text(
                    listOfNotNull(track.artist, source?.label()).filter { it.isNotBlank() }.joinToString(" • "),
                    color = PhoebeUi.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LyricsDocumentView(
    document: LyricsDocument,
    positionMs: Long,
    syncEnabled: Boolean,
    modifier: Modifier,
) {
    if (document.instrumental) {
        LyricsEmptyState("Instrumental track.")
        return
    }
    if (!document.hasText) {
        LyricsEmptyState("No lyric text available.")
        return
    }
    val lines = document.lines
    val activeIndex by remember(lines, positionMs, syncEnabled) {
        derivedStateOf {
            if (!document.synced || !syncEnabled) -1 else activeLyricsIndex(lines, positionMs)
        }
    }
    val listState = rememberLazyListState()
    var lastUserTouchMs by remember(document.trackFingerprint) { mutableLongStateOf(0L) }
    fun markUserBrowsing() {
        lastUserTouchMs = currentTimeMs()
    }
    LaunchedEffect(document.trackFingerprint, activeIndex) {
        val autoScrollSuppressed = currentTimeMs() - lastUserTouchMs < LyricsAutoScrollPauseMs
        if (activeIndex >= 0 && !autoScrollSuppressed) {
            listState.animateScrollToItem((activeIndex - 3).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp))
            .pointerInput(document.trackFingerprint) {
                detectTapGestures(
                    onPress = {
                        markUserBrowsing()
                        tryAwaitRelease()
                    },
                )
            }
            .pointerInput(document.trackFingerprint) {
                detectDragGestures(
                    onDragStart = { markUserBrowsing() },
                    onDrag = { _, _ -> markUserBrowsing() },
                )
            }
            .pointerInput(document.trackFingerprint) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            markUserBrowsing()
                        }
                    }
                }
            },
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(
            items = lines,
            key = { index, line -> "${line.startMs ?: "plain"}:$index:${line.text.hashCode()}" },
        ) { index, line ->
            val active = index == activeIndex
            Text(
                line.text.ifBlank { " " },
                color = if (active) PhoebeUi.accentLight else PhoebeUi.primaryText.copy(alpha = if (document.synced) 0.56f else 0.86f),
                fontSize = if (active) 24.sp else 20.sp,
                lineHeight = if (active) 30.sp else 27.sp,
                fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
            )
        }
    }
}

private fun activeLyricsIndex(lines: List<LyricsLine>, positionMs: Long): Int =
    lines.indexOfLast { line ->
        val startMs = line.startMs
        startMs != null && startMs <= positionMs
    }

@Composable
private fun LyricsLoadingState() {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PhoebeUi.accentLight, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun LyricsEmptyState(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = PhoebeUi.secondaryText, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LyricsRetryState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(message, color = PhoebeUi.secondaryText, fontSize = 16.sp, textAlign = TextAlign.Center)
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onRetry)
                .background(PhoebeUi.elevatedFill)
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhoebeIconView(PhoebeIcon.Lyrics, tint = PhoebeUi.accentLight, modifier = Modifier.size(16.dp))
            Text("Retry", color = PhoebeUi.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(2.dp))
    }
}

private fun LyricsSource.label(): String = when (this) {
    LyricsSource.LocalEmbedded -> "Local tags"
    LyricsSource.LocalSidecar -> "Local file"
    LyricsSource.Lrclib -> "LRCLIB"
    LyricsSource.Cache -> "Cached"
}
