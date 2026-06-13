package com.phoebe.app.feature.favorites

import androidx.lifecycle.ViewModel
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Inject
class FavoritePlaylistsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(FavoritePlaylistsRouteState())
    val state: StateFlow<FavoritePlaylistsRouteState> = mutableState.asStateFlow()

    fun update(state: FavoritePlaylistsRouteState) {
        mutableState.value = state
    }

    fun onSearchQuery(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }
}

@Inject
class FavoriteArtistsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<FavoriteArtistsRouteState?>(null)
    val state: StateFlow<FavoriteArtistsRouteState?> = mutableState.asStateFlow()

    fun update(state: FavoriteArtistsRouteState) {
        mutableState.value = state
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
class FavoriteAlbumsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<FavoriteAlbumsRouteState?>(null)
    val state: StateFlow<FavoriteAlbumsRouteState?> = mutableState.asStateFlow()

    fun update(state: FavoriteAlbumsRouteState) {
        mutableState.value = state
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
