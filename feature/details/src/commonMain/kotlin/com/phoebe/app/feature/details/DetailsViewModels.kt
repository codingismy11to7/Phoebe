package com.phoebe.app.feature.details

import androidx.lifecycle.ViewModel
import com.phoebe.app.domain.LibraryColumnVisibility
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Inject
class ArtistDetailViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<ArtistDetailRouteState?>(null)
    val state: StateFlow<ArtistDetailRouteState?> = mutableState.asStateFlow()

    fun update(state: ArtistDetailRouteState) {
        mutableState.value = state
    }

    fun onLibraryColumns(columns: LibraryColumnVisibility) {
        mutableState.update { it?.copy(libraryUi = it.libraryUi.copy(columns = columns)) }
    }
}

@Inject
class AlbumDetailViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<AlbumDetailRouteState?>(null)
    val state: StateFlow<AlbumDetailRouteState?> = mutableState.asStateFlow()

    fun update(state: AlbumDetailRouteState) {
        mutableState.value = state
    }

    fun onLibraryColumns(columns: LibraryColumnVisibility) {
        mutableState.update { it?.copy(libraryUi = it.libraryUi.copy(columns = columns)) }
    }
}

@Inject
class SongDetailViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<SongDetailRouteState?>(null)
    val state: StateFlow<SongDetailRouteState?> = mutableState.asStateFlow()

    fun update(state: SongDetailRouteState) {
        mutableState.value = state
    }
}

@Inject
class PlaylistDetailViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<PlaylistDetailRouteState?>(null)
    val state: StateFlow<PlaylistDetailRouteState?> = mutableState.asStateFlow()

    fun update(state: PlaylistDetailRouteState) {
        mutableState.value = state
    }

    fun onSearchQuery(query: String) {
        mutableState.update { it?.copy(searchQuery = query) }
    }

    fun onLibraryColumns(columns: LibraryColumnVisibility) {
        mutableState.update { it?.copy(libraryUi = it.libraryUi.copy(columns = columns)) }
    }
}
