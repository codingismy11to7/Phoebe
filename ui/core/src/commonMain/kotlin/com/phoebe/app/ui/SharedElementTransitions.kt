package com.phoebe.app.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedArtworkTransition(key: String?): Modifier {
    if (!LocalSharedElementTransitionsEnabled.current) return this

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    if (key == null || sharedTransitionScope == null || animatedVisibilityScope == null) return this

    return with(sharedTransitionScope) {
        this@sharedArtworkTransition.sharedElement(
            sharedContentState = rememberSharedContentState("artwork:$key"),
            animatedVisibilityScope = animatedVisibilityScope,
            zIndexInOverlay = 1f,
        )
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedBoundsTransition(key: String?): Modifier {
    if (!LocalSharedElementTransitionsEnabled.current) return this

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    if (key == null || sharedTransitionScope == null || animatedVisibilityScope == null) return this

    return with(sharedTransitionScope) {
        this@sharedBoundsTransition.sharedBounds(
            sharedContentState = rememberSharedContentState("bounds:$key"),
            animatedVisibilityScope = animatedVisibilityScope,
            zIndexInOverlay = 1f,
        )
    }
}
