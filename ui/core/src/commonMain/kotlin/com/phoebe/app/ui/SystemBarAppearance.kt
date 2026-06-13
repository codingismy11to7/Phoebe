package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
expect fun ApplySystemBarAppearance(
    statusBarColor: Color,
    navigationBarColor: Color,
    useLightIcons: Boolean,
)
