package com.phoebe.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Keeps [LazyListState] instances alive across navigation so back restores scroll position.
 */
internal object RetainedLazyListStates {
    private val cache = mutableMapOf<String, LazyListState>()

    @Composable
    fun remember(key: String): LazyListState =
        remember(key) { cache.getOrPut(key) { LazyListState() } }
}

/**
 * Keeps [LazyGridState] instances alive across navigation so back restores scroll position.
 */
internal object RetainedLazyGridStates {
    private val cache = mutableMapOf<String, LazyGridState>()

    @Composable
    fun remember(key: String): LazyGridState =
        remember(key) { cache.getOrPut(key) { LazyGridState() } }
}
