package com.phoebe.app.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

@Composable
actual fun Modifier.phoebeClickable(
    enabled: Boolean,
    onClickLabel: String?,
    role: Role?,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    hoverable(interactionSource, enabled)
        .clickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            interactionSource = interactionSource,
            indication = DesktopHoverIndication,
            onClick = onClick,
        )
}

@Composable
actual fun Modifier.phoebeCombinedClickable(
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)?,
    onClickLabel: String?,
    role: Role?,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    hoverable(interactionSource, enabled)
        .combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
            onClickLabel = onClickLabel,
            role = role,
            interactionSource = interactionSource,
            indication = DesktopHoverIndication,
        )
}

@Composable
actual fun PlatformInteractionLocals(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        content()
    }
}
