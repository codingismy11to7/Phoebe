package com.phoebe.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.phoebe.app.platform.isDesktopPlatform

/**
 * A [basicMarquee] running forever redraws the whole window at display refresh
 * rate for as long as it's on screen. On desktop — where a static list of tracks
 * nobody is pointing at is the common case — the marquee only engages on hover
 * (mouse) or focus (keyboard). Touch platforms have neither, so they keep the
 * unconditional behavior: the only way to read an overflowing title there is
 * to let it scroll on its own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    marqueeEnabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val requiresInteraction = isDesktopPlatform()
    val active = marqueeEnabled &&
        LocalContinuousMotionEnabled.current &&
        (!requiresInteraction || isHovered || isFocused)
    val hoverableModifier = if (requiresInteraction) {
        modifier.hoverable(interactionSource)
    } else {
        modifier
    }
    val textModifier = if (active) {
        hoverableModifier.basicMarquee(iterations = Int.MAX_VALUE)
    } else {
        hoverableModifier
    }
    Text(
        text,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
        textAlign = textAlign,
        modifier = textModifier,
    )
}
