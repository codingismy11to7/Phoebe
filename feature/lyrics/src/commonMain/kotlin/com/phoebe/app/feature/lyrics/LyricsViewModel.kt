package com.phoebe.app.feature.lyrics

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Inject
class LyricsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<LyricsRouteState?>(null)
    val state: StateFlow<LyricsRouteState?> = mutableState.asStateFlow()

    fun update(state: LyricsRouteState) {
        mutableState.value = state
    }
}
