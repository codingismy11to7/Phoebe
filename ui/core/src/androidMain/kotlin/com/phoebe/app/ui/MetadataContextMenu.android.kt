package com.phoebe.app.ui

import androidx.compose.ui.Modifier

actual fun Modifier.openContextMenuOnSecondaryClick(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier = this
