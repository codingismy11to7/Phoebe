package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun DragGhost() {
    val controller = LocalDragDrop.current ?: return
    val track = controller.draggedTrack ?: return
    val pointer = controller.pointer ?: return
    val density = LocalDensity.current
    val hoveringTitle = controller.hoveringPlaylistTitle
    val onTarget = hoveringTitle != null

    Box(
        modifier = Modifier
            .offset {
                // Anchor the ghost near the cursor without hiding the drop target.
                val px = with(density) { 14.dp.toPx() }
                val py = with(density) { 10.dp.toPx() }
                IntOffset(
                    x = (pointer.x + px).roundToInt(),
                    y = (pointer.y + py).roundToInt(),
                )
            }
            .zIndex(1000f)
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (onTarget) PhoebeUi.accentLight.copy(alpha = 0.96f) else PhoebeUi.accent.copy(alpha = 0.92f),
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = if (onTarget) 0.45f else 0.18f)),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (onTarget) "Add to $hoveringTitle" else "Moving",
                color = if (onTarget) Color.White else PhoebeUi.primaryText.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
            )
            AutoScrollingText(
                "♪  ${track.title}",
                color = PhoebeUi.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}
