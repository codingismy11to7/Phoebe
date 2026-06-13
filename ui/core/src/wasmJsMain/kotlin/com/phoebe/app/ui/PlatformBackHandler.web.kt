package com.phoebe.app.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    onBackProgress: ((Float) -> Unit)?,
    onBackCancel: (() -> Unit)?,
) {
}
