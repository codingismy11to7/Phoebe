package com.phoebe.app.feature.home

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Inject
class DesktopHomeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<DesktopHomeRouteState?>(null)
    val state: StateFlow<DesktopHomeRouteState?> = mutableState.asStateFlow()

    fun update(state: DesktopHomeRouteState) {
        mutableState.value = state
    }
}

@Inject
class MobileHomeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<MobileHomeRouteState?>(null)
    val state: StateFlow<MobileHomeRouteState?> = mutableState.asStateFlow()

    fun update(state: MobileHomeRouteState) {
        mutableState.value = state
    }
}

@Inject
class RecentlyAddedViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<RecentlyAddedRouteState?>(null)
    val state: StateFlow<RecentlyAddedRouteState?> = mutableState.asStateFlow()

    fun update(state: RecentlyAddedRouteState) {
        mutableState.value = state
    }
}
