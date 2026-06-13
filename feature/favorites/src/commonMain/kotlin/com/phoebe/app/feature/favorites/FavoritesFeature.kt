package com.phoebe.app.feature.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.Playlist
import com.phoebe.app.feature.library.FavoriteAlbumsDesktopView
import com.phoebe.app.feature.library.FavoriteAlbumsMobileView
import com.phoebe.app.feature.library.FavoriteArtistsDesktopView
import com.phoebe.app.feature.library.FavoriteArtistsMobileView
import com.phoebe.app.feature.library.FavoritePlaylistsDesktopView
import com.phoebe.app.feature.library.FavoritePlaylistsMobileView

@Immutable
data class FavoritePlaylistsRouteState(
    val playlists: List<Playlist> = emptyList(),
    val searchQuery: String = "",
)

class FavoritePlaylistsRouteActions(
    val onSearchQuery: (String) -> Unit,
    val onPlaylist: (Playlist) -> Unit,
    val onBack: () -> Unit,
)

@Immutable
data class FavoriteArtistsRouteState(
    val catalog: CatalogSnapshot,
    val libraryUi: LibraryUiPreferences,
    val searchQuery: String = "",
)

class FavoriteArtistsRouteActions(
    val onLibrarySortBy: (LibrarySortBy) -> Unit,
    val onLibraryAscending: (Boolean) -> Unit,
    val onArtist: (Artist) -> Unit,
    val onBack: () -> Unit,
    val onSearchQuery: (String) -> Unit = {},
    val onLibraryColumns: (LibraryColumnVisibility) -> Unit = {},
)

@Immutable
data class FavoriteAlbumsRouteState(
    val catalog: CatalogSnapshot,
    val libraryUi: LibraryUiPreferences,
    val searchQuery: String = "",
)

class FavoriteAlbumsRouteActions(
    val onLibrarySortBy: (LibrarySortBy) -> Unit,
    val onLibraryAscending: (Boolean) -> Unit,
    val onAlbum: (Album) -> Unit,
    val onBack: () -> Unit,
    val onSearchQuery: (String) -> Unit = {},
    val onLibraryColumns: (LibraryColumnVisibility) -> Unit = {},
)

@Composable
fun FavoritePlaylistsDesktopRoute(
    state: FavoritePlaylistsRouteState,
    actions: FavoritePlaylistsRouteActions,
    modifier: Modifier = Modifier,
) {
    FavoritePlaylistsDesktopView(
        playlists = state.playlists,
        searchQuery = state.searchQuery,
        onSearchQuery = actions.onSearchQuery,
        onPlaylist = actions.onPlaylist,
        onBack = actions.onBack,
        modifier = modifier,
    )
}

@Composable
fun FavoritePlaylistsMobileRoute(
    state: FavoritePlaylistsRouteState,
    actions: FavoritePlaylistsRouteActions,
    modifier: Modifier = Modifier,
) {
    FavoritePlaylistsMobileView(
        searchQuery = state.searchQuery,
        onSearchQuery = actions.onSearchQuery,
        onPlaylist = actions.onPlaylist,
        onBack = actions.onBack,
        modifier = modifier,
    )
}

@Composable
fun FavoriteArtistsDesktopRoute(
    state: FavoriteArtistsRouteState,
    actions: FavoriteArtistsRouteActions,
    modifier: Modifier = Modifier,
) {
    FavoriteArtistsDesktopView(
        catalog = state.catalog,
        libraryUi = state.libraryUi,
        searchQuery = state.searchQuery,
        onSearchQuery = actions.onSearchQuery,
        onLibrarySortBy = actions.onLibrarySortBy,
        onLibraryAscending = actions.onLibraryAscending,
        onArtist = actions.onArtist,
        onBack = actions.onBack,
        modifier = modifier,
    )
}

@Composable
fun FavoriteArtistsMobileRoute(
    state: FavoriteArtistsRouteState,
    actions: FavoriteArtistsRouteActions,
    modifier: Modifier = Modifier,
) {
    FavoriteArtistsMobileView(
        catalog = state.catalog,
        libraryUi = state.libraryUi,
        onLibrarySortBy = actions.onLibrarySortBy,
        onLibraryAscending = actions.onLibraryAscending,
        onLibraryColumns = actions.onLibraryColumns,
        onArtist = actions.onArtist,
        onBack = actions.onBack,
        modifier = modifier,
    )
}

@Composable
fun FavoriteAlbumsDesktopRoute(
    state: FavoriteAlbumsRouteState,
    actions: FavoriteAlbumsRouteActions,
    modifier: Modifier = Modifier,
) {
    FavoriteAlbumsDesktopView(
        catalog = state.catalog,
        libraryUi = state.libraryUi,
        searchQuery = state.searchQuery,
        onSearchQuery = actions.onSearchQuery,
        onLibrarySortBy = actions.onLibrarySortBy,
        onLibraryAscending = actions.onLibraryAscending,
        onAlbum = actions.onAlbum,
        onBack = actions.onBack,
        modifier = modifier,
    )
}

@Composable
fun FavoriteAlbumsMobileRoute(
    state: FavoriteAlbumsRouteState,
    actions: FavoriteAlbumsRouteActions,
    modifier: Modifier = Modifier,
) {
    FavoriteAlbumsMobileView(
        catalog = state.catalog,
        libraryUi = state.libraryUi,
        onLibrarySortBy = actions.onLibrarySortBy,
        onLibraryAscending = actions.onLibraryAscending,
        onLibraryColumns = actions.onLibraryColumns,
        onAlbum = actions.onAlbum,
        onBack = actions.onBack,
        modifier = modifier,
    )
}
