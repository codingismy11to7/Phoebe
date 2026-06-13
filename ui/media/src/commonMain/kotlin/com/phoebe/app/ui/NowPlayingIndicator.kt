package com.phoebe.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Three-bar animated equalizer used as a "this track is currently playing" indicator.
 */
@Composable
fun NowPlayingIndicator(
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    modifier: Modifier = Modifier,
    barColor: Color = PhoebeUi.accentLight,
) {
    when {
        isPlaying && isBuffering -> CircularProgressIndicator(
            modifier = modifier,
            color = barColor,
            strokeWidth = 2.dp,
            trackColor = barColor.copy(alpha = 0.22f),
        )
        else -> AnimatedNowPlayingIndicator(
            isPlaying = isPlaying,
            modifier = modifier,
            barColor = barColor,
        )
    }
}

@Composable
fun AnimatedNowPlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = PhoebeUi.accentLight,
) {
    val bar1 = remember { Animatable(0.25f) }
    val bar2 = remember { Animatable(0.6f) }
    val bar3 = remember { Animatable(0.4f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        launch {
            while (true) {
                bar1.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
                bar1.animateTo(0.25f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
            }
        }
        launch {
            while (true) {
                bar2.animateTo(0.35f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
                bar2.animateTo(0.6f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
            }
        }
        launch {
            while (true) {
                bar3.animateTo(0.85f, tween(durationMillis = 460, easing = FastOutSlowInEasing))
                bar3.animateTo(0.4f, tween(durationMillis = 460, easing = FastOutSlowInEasing))
            }
        }
    }

    NowPlayingIndicatorBars(
        heights = listOf(bar1.value, bar2.value, bar3.value),
        modifier = modifier,
        barColor = barColor,
    )
}

@Composable
fun NowPlayingIndicatorBars(
    heights: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = PhoebeUi.accentLight,
) {
    Canvas(modifier) {
        val barWidth = size.width / 7f
        val gap = barWidth
        val totalWidth = barWidth * 3 + gap * 2
        val startX = (size.width - totalWidth) / 2f
        heights.forEachIndexed { i, h ->
            val barH = (size.height * h).coerceAtLeast(barWidth)
            val x = startX + i * (barWidth + gap)
            val y = size.height - barH
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
