package com.phoebe.app.ui

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    onBackProgress: ((Float) -> Unit)? = null,
    onBackCancel: (() -> Unit)? = null,
)
