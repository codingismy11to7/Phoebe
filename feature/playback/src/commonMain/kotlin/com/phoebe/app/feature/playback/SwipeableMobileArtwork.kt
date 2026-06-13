package com.phoebe.app.feature.playback

import com.phoebe.app.ui.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.phoebe.app.domain.Track
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeableMobileArtwork(
    track: Track,
    nextTrack: Track?,
    previousTrack: Track?,
    swipeOffset: Float,
    swipePreviewDirection: Int,
    modifier: Modifier = Modifier,
    trackContent: @Composable (Track) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .semantics {
                contentDescription = "Album artwork. Swipe left for next track, swipe right for previous track."
            },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            val tracksToRender = remember(track, nextTrack, previousTrack, swipePreviewDirection) {
                buildList {
                    if (previousTrack != null && swipePreviewDirection > 0) {
                        add(previousTrack to -1)
                    }
                    add(track to 0)
                    if (nextTrack != null && swipePreviewDirection < 0) {
                        add(nextTrack to 1)
                    }
                }
            }

            for ((t, position) in tracksToRender) {
                key(t.id) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset {
                                val baseOffset = when (position) {
                                    -1 -> -widthPx
                                    1 -> widthPx
                                    else -> 0f
                                }
                                IntOffset((baseOffset + swipeOffset).roundToInt(), 0)
                            }
                            .graphicsLayer {
                                if (position == 0) {
                                    val dragProgress = (abs(swipeOffset) / widthPx).coerceIn(0f, 1f)
                                    val scale = 1f - dragProgress * 0.03f
                                    scaleX = scale
                                    scaleY = scale
                                }
                            },
                    ) {
                        trackContent(t)
                    }
                }
            }
        }
    }
}
