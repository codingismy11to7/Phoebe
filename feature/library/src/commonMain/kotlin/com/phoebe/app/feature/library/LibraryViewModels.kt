package com.phoebe.app.feature.library

import androidx.lifecycle.ViewModel
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Inject
class LibraryViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<LibraryRouteState?>(null)
    val state: StateFlow<LibraryRouteState?> = mutableState.asStateFlow()

    fun update(state: LibraryRouteState) {
        mutableState.value = state
    }

    fun onFilter(filter: LibraryFilterTab) {
        mutableState.update { it?.copy(filter = filter) }
    }

    fun onSearchQuery(query: String) {
        mutableState.update { it?.copy(searchQuery = query) }
    }

    fun onLibrarySortBy(sortBy: LibrarySortBy) {
        mutableState.update { it?.copy(libraryUi = it.libraryUi.copy(sortBy = sortBy)) }
    }

    fun onLibraryAscending(ascending: Boolean) {
        mutableState.update { it?.copy(libraryUi = it.libraryUi.copy(ascending = ascending)) }
    }

    fun onLibraryColumns(columns: LibraryColumnVisibility) {
        mutableState.update { it?.copy(libraryUi = it.libraryUi.copy(columns = columns)) }
    }
}

@Inject
class PlaylistsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<PlaylistsRouteState?>(null)
    val state: StateFlow<PlaylistsRouteState?> = mutableState.asStateFlow()

    fun update(state: PlaylistsRouteState) {
        mutableState.value = state
    }

    fun onSearchQuery(query: String) {
        mutableState.update { it?.copy(searchQuery = query) }
    }
}

@Inject
class PlaylistDetailDesktopViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<PlaylistDetailDesktopRouteState?>(null)
    val state: StateFlow<PlaylistDetailDesktopRouteState?> = mutableState.asStateFlow()

    fun update(state: PlaylistDetailDesktopRouteState) {
        mutableState.value = state
    }

    fun onSearchQuery(query: String) {
        mutableState.update { it?.copy(searchQuery = query) }
    }

    fun onLibraryColumns(columns: LibraryColumnVisibility) {
        mutableState.update { it?.copy(libraryUi = it.libraryUi.copy(columns = columns)) }
    }
}
