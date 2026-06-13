package com.phoebe.app.feature.auth

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Inject
class AuthWelcomeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<AuthWelcomeRouteState?>(null)
    val state: StateFlow<AuthWelcomeRouteState?> = mutableState.asStateFlow()

    fun update(state: AuthWelcomeRouteState) {
        mutableState.value = state
    }
}

@Inject
class PlexServerPickerViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<PlexServerPickerRouteState?>(null)
    val state: StateFlow<PlexServerPickerRouteState?> = mutableState.asStateFlow()

    fun update(state: PlexServerPickerRouteState) {
        mutableState.value = state
    }
}

@Inject
class PlexLibraryPickerViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<PlexLibraryPickerRouteState?>(null)
    val state: StateFlow<PlexLibraryPickerRouteState?> = mutableState.asStateFlow()

    fun update(state: PlexLibraryPickerRouteState) {
        mutableState.value = state
    }
}
