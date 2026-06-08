package com.phoebe.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

@Composable
actual fun Modifier.phoebeClickable(
    enabled: Boolean,
    onClickLabel: String?,
    role: Role?,
    onClick: () -> Unit,
): Modifier = clickable(
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    onClick = onClick,
)

@Composable
actual fun Modifier.phoebeCombinedClickable(
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)?,
    onClickLabel: String?,
    role: Role?,
): Modifier = combinedClickable(
    enabled = enabled,
    onClick = onClick,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    onClickLabel = onClickLabel,
    role = role,
)

@Composable
actual fun PlatformInteractionLocals(content: @Composable () -> Unit) {
    content()
}
