package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryToolbarButton(
    icon: PhoebeIcon,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    iconTint: Color = PhoebeUi.secondaryText,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(Color.White.copy(alpha = 0.04f))
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leadingContent != null) {
            leadingContent()
        } else {
            PhoebeIconView(icon, tint = iconTint, modifier = Modifier.size(13.dp))
        }
        Text(label, color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        value?.let {
            Text(
                it,
                color = PhoebeUi.mutedText,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 34.dp),
            )
        }
    }
}
