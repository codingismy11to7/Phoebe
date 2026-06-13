package com.phoebe.app.ui

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val PhoebeUpdateBlue = Color(0xFF3B82F6)

@Composable
fun UpdateProgressRing(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    if (progress == null) {
        CircularProgressIndicator(
            modifier = modifier,
            color = PhoebeUpdateBlue,
            strokeWidth = 2.dp,
            trackColor = PhoebeUpdateBlue.copy(alpha = 0.16f),
        )
    } else {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = PhoebeUpdateBlue,
            strokeWidth = 2.dp,
            trackColor = PhoebeUpdateBlue.copy(alpha = 0.16f),
        )
    }
}
