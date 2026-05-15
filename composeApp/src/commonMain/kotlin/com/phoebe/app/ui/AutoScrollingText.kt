package com.phoebe.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AutoScrollingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
) {
    val textModifier = if (LocalContinuousMotionEnabled.current) {
        modifier.basicMarquee(iterations = Int.MAX_VALUE)
    } else {
        modifier
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
