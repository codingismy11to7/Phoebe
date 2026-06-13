package com.phoebe.app.ui

import androidx.compose.runtime.compositionLocalOf

/** Windows desktop: app content draws under the native caption; insets omit the top safe area. */
val LocalDesktopMergesTitleBar = compositionLocalOf { false }
