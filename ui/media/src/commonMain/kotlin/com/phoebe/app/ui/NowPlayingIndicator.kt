package com.phoebe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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

/**
 * Bar heights are stepped on a fixed [IndicatorStepIntervalMs] ticker rather than
 * animated continuously — a continuous [androidx.compose.animation.core.Animatable]
 * here forces a full-window redraw at display refresh rate for as long as anything
 * is playing anywhere in the list. Stepping the target values instead drops that to
 * ~11 redraws/sec, which reads identically for a 20px equalizer blip.
 */
@Composable
fun AnimatedNowPlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = PhoebeUi.accentLight,
) {
    val motionEnabled = LocalContinuousMotionEnabled.current
    var bar1 by remember { mutableFloatStateOf(0.25f) }
    var bar2 by remember { mutableFloatStateOf(0.6f) }
    var bar3 by remember { mutableFloatStateOf(0.4f) }

    LaunchedEffect(isPlaying, motionEnabled) {
        if (!isPlaying || !motionEnabled) return@LaunchedEffect
        var elapsedMs = 0L
        while (true) {
            bar1 = triangleWave(elapsedMs, periodMs = 1040L, min = 0.25f, max = 1f)
            bar2 = triangleWave(elapsedMs + 300L, periodMs = 1200L, min = 0.35f, max = 0.6f)
            bar3 = triangleWave(elapsedMs + 150L, periodMs = 920L, min = 0.4f, max = 0.85f)
            delay(IndicatorStepIntervalMs)
            elapsedMs += IndicatorStepIntervalMs
        }
    }

    NowPlayingIndicatorBars(
        heights = listOf(bar1, bar2, bar3),
        modifier = modifier,
        barColor = barColor,
    )
}

private const val IndicatorStepIntervalMs = 90L

private fun triangleWave(elapsedMs: Long, periodMs: Long, min: Float, max: Float): Float {
    val phase = (elapsedMs % periodMs).toFloat() / periodMs.toFloat()
    val triangle = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
    return min + (max - min) * triangle
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
