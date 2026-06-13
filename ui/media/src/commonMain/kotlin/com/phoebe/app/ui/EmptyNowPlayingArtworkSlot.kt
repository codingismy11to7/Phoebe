package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoebe.app.platform.prefersReducedArtworkEffects

@Composable
fun EmptyNowPlayingArtworkSlot(
    modifier: Modifier = Modifier,
    glyphSp: TextUnit = 52.sp,
    shadowElevation: Dp = 18.dp,
    shadowAlpha: Float = 0.28f,
) {
    val shape = RoundedCornerShape(14.dp)
    val shadowColor = Color.Black.copy(alpha = shadowAlpha)
    val decorationModifier = if (shadowElevation > 0.dp && !prefersReducedArtworkEffects()) {
        modifier.shadow(
            shadowElevation,
            shape,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        )
    } else {
        modifier
    }
    Box(
        decorationModifier
            .clip(shape)
            .background(PhoebeUi.glass)
            .border(BorderStroke(1.dp, PhoebeUi.border), shape),
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(PhoebeIcon.Music, tint = PhoebeUi.mutedText.copy(alpha = 0.42f), modifier = Modifier.size(glyphSp.value.dp))
    }
}
