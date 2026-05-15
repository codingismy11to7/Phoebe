package com.phoebe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

@Composable
internal fun RatingStars(
    rating: Float?,
    enabled: Boolean,
    onRating: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 15.dp,
    gap: Dp = 1.dp,
    showClear: Boolean = false,
) {
    val normalized = rating.normalizedUiRating()
    Row(
        modifier.semantics {
            contentDescription = normalized?.let { "Rating ${formatRatingLabel(it)} out of 5" } ?: "Unrated"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { index ->
            val starValue = index + 1
            val fill = when {
                normalized == null -> 0f
                normalized >= starValue -> 1f
                normalized >= starValue - 0.5f -> 0.5f
                else -> 0f
            }
            Box(Modifier.size(starSize), contentAlignment = Alignment.Center) {
                StarGlyph(fill = fill, modifier = Modifier.size(starSize))
                if (enabled) {
                    Row(Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onRating(starValue - 0.5f) },
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onRating(starValue.toFloat()) },
                        )
                    }
                }
            }
            if (index != 4) Spacer(Modifier.width(gap))
        }
        if (showClear && enabled && normalized != null) {
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(starSize + 4.dp)
                    .clip(CircleShape)
                    .clickable { onRating(null) },
                contentAlignment = Alignment.Center,
            ) {
                PhoebeIconView(
                    PhoebeIcon.Close,
                    tint = PhoebeUi.mutedText.copy(alpha = 0.72f),
                    modifier = Modifier.size(starSize * 0.72f),
                )
            }
        }
    }
}

@Composable
private fun StarGlyph(fill: Float, modifier: Modifier) {
    val active = PhoebeUi.accentLight.copy(alpha = 0.86f)
    val inactive = PhoebeUi.mutedText.copy(alpha = 0.38f)
    Canvas(modifier) {
        val path = starPath(size.minDimension)
        drawPath(path, inactive, style = Stroke(width = (size.minDimension * 0.08f).coerceAtLeast(1f)))
        if (fill > 0f) {
            clipRect(right = size.width * fill.coerceIn(0f, 1f)) {
                drawPath(path, active)
            }
        }
    }
}

private fun starPath(side: Float): Path {
    val cx = side / 2f
    val cy = side / 2f
    val outer = side * 0.46f
    val inner = outer * 0.46f
    return Path().apply {
        for (point in 0 until 10) {
            val radius = if (point % 2 == 0) outer else inner
            val angle = -PI / 2.0 + point * PI / 5.0
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            if (point == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private fun Float?.normalizedUiRating(): Float? =
    this?.coerceIn(0f, 5f)
        ?.let { round(it * 2f) / 2f }
        ?.takeIf { it > 0f }

private fun formatRatingLabel(rating: Float): String =
    if (rating % 1f == 0f) rating.toInt().toString() else rating.toString()
