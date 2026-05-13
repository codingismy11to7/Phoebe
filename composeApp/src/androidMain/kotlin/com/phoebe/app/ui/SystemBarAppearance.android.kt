package com.phoebe.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun ApplySystemBarAppearance(
    statusBarColor: Color,
    navigationBarColor: Color,
    useLightIcons: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = statusBarColor.toArgb()
        window.navigationBarColor = navigationBarColor.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = useLightIcons
        controller.isAppearanceLightNavigationBars = useLightIcons
    }
}
