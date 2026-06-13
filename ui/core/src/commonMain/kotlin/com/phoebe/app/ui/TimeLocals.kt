package com.phoebe.app.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * Current wall-clock reference used to render relative time strings.
 *
 * The app shell updates this periodically so "Just now" eventually slides to
 * "Today", "Today" slides to "Yesterday", and similar copy can update without
 * each feature owning its own timer.
 */
val LocalNowMs = compositionLocalOf { 0L }
