package com.phoebe.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
    onBackProgress: ((Float) -> Unit)?,
    onBackCancel: (() -> Unit)?,
) {
    val latestOnBack by rememberUpdatedState(onBack)
    val latestOnBackProgress by rememberUpdatedState(onBackProgress)
    val latestOnBackCancel by rememberUpdatedState(onBackCancel)
    if (onBackProgress == null && onBackCancel == null) {
        BackHandler(enabled = enabled, onBack = { latestOnBack() })
    } else {
        PredictiveBackHandler(enabled = enabled) { progress ->
            try {
                progress.collect { backEvent ->
                    latestOnBackProgress?.invoke(backEvent.progress)
                }
                latestOnBack()
            } catch (_: CancellationException) {
                latestOnBackCancel?.invoke()
            }
        }
    }
}
