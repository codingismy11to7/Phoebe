package com.phoebe.app.feature.collections

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Inject
class CollectionsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<CollectionsRouteState?>(null)
    val state: StateFlow<CollectionsRouteState?> = mutableState.asStateFlow()

    fun update(state: CollectionsRouteState) {
        mutableState.value = state
    }

    fun onSearchQuery(query: String) {
        mutableState.update { it?.copy(searchQuery = query) }
    }
}

@Inject
class CollectionItemsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<CollectionItemsRouteState?>(null)
    val state: StateFlow<CollectionItemsRouteState?> = mutableState.asStateFlow()

    fun update(state: CollectionItemsRouteState) {
        mutableState.value = state
    }

    fun onSearchQuery(query: String) {
        mutableState.update { it?.copy(searchQuery = query) }
    }
}
