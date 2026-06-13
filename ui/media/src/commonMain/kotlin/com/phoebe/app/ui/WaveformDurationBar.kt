package com.phoebe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.Track

/** Stable per-track seed so wave shape differs across library even when ids are opaque or similar. */
fun trackWaveformSeed(track: Track): String =
    "${track.id}\u0000${track.title}\u0000${track.artist}\u0000${track.album}\u0000${track.durationMs}"

fun waveBarHeight(seed: String, index: Int): Float {
    var h = 0L
    for (c in seed) {
        h = h * 31L + c.code
    }
    var x = h xor (index.toLong() * 0x9e3779b9L)
    x = (x * 0x85ebca6bL) xor (x ushr 13)
    x = (x * 0xc2b2ae35L) xor (x ushr 16)
    var t = x xor (index.toLong() * 0x27d4eb2fL)
    t = t xor (t ushr 4)
    t *= 0xcc9e2d51L
    t = t xor (t ushr 11)
    val u = ((t ushr 8) and 0xffffL).toFloat() / 65536f
    val w = kotlin.math.sin(index * 1.17 + h * 2.1e-5 + t * 1.5e-4).toFloat()
    val w2 = kotlin.math.cos(index * 0.53 + (t and 0xffL).toDouble() * 0.11).toFloat()
    return (0.13f + 0.54f * u + 0.19f * w + 0.15f * w2).coerceIn(0.12f, 1f)
}

@Composable
fun WaveformDurationBar(
    seed: String,
    durationMs: Long,
    progress: Float?,
    bufferedProgress: Float?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isScrubbing: Boolean = false,
    maxBarSlots: Int = 140,
) {
    val p = progress?.coerceIn(0f, 1f)
    val bp = bufferedProgress?.coerceIn(0f, 1f)
    val playedColor = PhoebeUi.accentLight
    val bufferedColor = PhoebeUi.primaryText.copy(alpha = 0.34f)
    val unplayedBase = PhoebeUi.waveformUnplayed
    val playheadColor = PhoebeUi.waveformPlayhead
    val waveformAmplitudes = remember(seed, durationMs, maxBarSlots) {
        FloatArray(maxBarSlots.coerceAtLeast(20)) { index ->
            if (durationMs > 0L) waveBarHeight(seed, index) else 0.12f
        }
    }
    Canvas(
        modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val barSlots = (size.width / (2.2f * density)).toInt().coerceIn(20, maxBarSlots.coerceAtLeast(20))
        val slotW = size.width / barSlots
        val barW = (slotW * 0.62f).coerceAtLeast(1.2f)
        for (i in 0 until barSlots) {
            val frac = (i + 0.5f) / barSlots
            val amp = waveformAmplitudes[i]
            val barH = size.height * amp
            val x = i * slotW + (slotW - barW) / 2f
            val color = when {
                durationMs <= 0L -> unplayedBase.copy(alpha = (unplayedBase.alpha * 0.75f).coerceIn(0.08f, 0.5f))
                p == null -> {
                    val a = (0.12f + 0.52f * amp).coerceIn(0.12f, 0.55f)
                    unplayedBase.copy(alpha = a)
                }
                frac <= p -> playedColor
                bp != null && frac <= bp -> bufferedColor
                else -> unplayedBase
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW * 0.45f, barW * 0.45f),
            )
        }
        if (durationMs > 0L && p != null && p in 0.001f..0.999f) {
            val cx = size.width * p
            val playheadWidth = if (isScrubbing) 2.dp.toPx() else 1.dp.toPx()
            drawLine(
                color = playheadColor,
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = playheadWidth,
            )
            if (isScrubbing) {
                drawCircle(
                    color = playheadColor,
                    radius = 5.dp.toPx(),
                    center = Offset(cx, size.height / 2f),
                )
            }
        }
    }
}
