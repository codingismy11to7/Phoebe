package com.phoebe.app.feature.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.phoebe.app.domain.LyricsLoadState
import com.phoebe.app.domain.Track

@Immutable
data class LyricsRouteState(
    val track: Track?,
    val currentTrackId: String?,
    val positionMs: Long,
    val loadState: LyricsLoadState,
)

@Composable
fun LyricsRoute(
    state: LyricsRouteState,
    onBack: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LyricsView(
        track = state.track,
        currentTrackId = state.currentTrackId,
        positionMs = state.positionMs,
        state = state.loadState,
        modifier = modifier,
        onBack = onBack,
        onRetry = onRetry,
    )
}
