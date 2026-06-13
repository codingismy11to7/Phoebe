package com.phoebe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaylistRow(
    icon: PhoebeIcon?,
    title: String,
    subtitle: String?,
    thumbUrl: String? = null,
    accent: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .phoebeCombinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (active) PhoebeUi.accent.copy(alpha = 0.09f) else PhoebeUi.sidebar)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtworkImage(title, thumbUrl, Modifier.size(36.dp), radius = 6.dp)
            if (accent || icon != null) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (accent) {
                                Brush.linearGradient(
                                    listOf(
                                        PhoebeUi.accentLight.copy(alpha = 0.82f),
                                        Color(0xCC6D45E8),
                                    ),
                                )
                            } else {
                                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        PhoebeIconView(icon, tint = PhoebeUi.primaryText, modifier = Modifier.size(18.dp), filled = accent)
                    }
                }
            }
        }
        Column {
            Text(title, color = PhoebeUi.secondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = PhoebeUi.mutedText, fontSize = 12.sp)
        }
    }
}
