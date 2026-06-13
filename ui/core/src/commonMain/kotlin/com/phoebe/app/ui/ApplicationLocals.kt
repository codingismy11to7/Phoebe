package com.phoebe.app.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.CatalogSyncState

val LocalCatalogHasContent = compositionLocalOf { false }

val LocalCatalogSyncState = compositionLocalOf { CatalogSyncState() }

/** True while a foreground or background catalog sync is still running. */
val LocalCatalogSyncInProgress = compositionLocalOf { false }

/** False while the home track index and played rows are still being derived after sync. */
val LocalHomeTrackSectionsReady = compositionLocalOf { true }

/** True when SQL-ranked most-played rows exist but home has not resolved the full panel yet. */
val LocalMostPlayedResolving = compositionLocalOf { false }

data class MobileChromePadding(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

val LocalMobileChromePadding = compositionLocalOf { MobileChromePadding() }

/** When false, song rows skip playlist drag-and-drop in compact layouts. */
val LocalPlaylistDragEnabled = compositionLocalOf { true }

val LocalSharedElementTransitionsEnabled = compositionLocalOf { true }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
