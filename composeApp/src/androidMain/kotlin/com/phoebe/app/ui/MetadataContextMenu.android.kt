package com.phoebe.app.ui

import androidx.compose.ui.Modifier

internal actual fun Modifier.openContextMenuOnSecondaryClick(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier = this
