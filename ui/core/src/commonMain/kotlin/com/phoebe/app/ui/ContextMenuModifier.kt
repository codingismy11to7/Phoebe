package com.phoebe.app.ui

import androidx.compose.ui.Modifier

expect fun Modifier.openContextMenuOnSecondaryClick(
    enabled: Boolean = true,
    onOpen: () -> Unit,
): Modifier
