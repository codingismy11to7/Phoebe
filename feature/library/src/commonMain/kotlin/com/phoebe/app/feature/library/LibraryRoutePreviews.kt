package com.phoebe.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.Playlist
import com.phoebe.app.ui.FavoriteActions
import com.phoebe.app.ui.LocalCatalogSyncInProgress
import com.phoebe.app.ui.LocalFavoriteActions
import com.phoebe.app.ui.LocalPlaylistActions
import com.phoebe.app.ui.PhoebeTheme
import com.phoebe.app.ui.PhoebeUi
import com.phoebe.app.ui.PlaylistActions
import com.phoebe.app.ui.preview.PhoebePreviewData

private object LibraryRoutePreviewData {
    val catalog = PhoebePreviewData.catalog
    val playlist = PhoebePreviewData.playlist
    val tracks = PhoebePreviewData.tracks
    val libraryUi = LibraryUiPreferences()
}

@Composable
private fun LibraryRoutePreviewScaffold(
    content: @Composable () -> Unit,
) {
    PhoebeTheme {
        CompositionLocalProvider(
            LocalCatalogSyncInProgress provides false,
            LocalFavoriteActions provides FavoriteActions(catalog = LibraryRoutePreviewData.catalog),
            LocalPlaylistActions provides PlaylistActions(
                playlists = LibraryRoutePreviewData.catalog.playlists,
                playlistsEnabled = true,
                onShufflePlaylist = {},
            ),
        ) {
            content()
        }
    }
}

@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun LibraryDesktopRoutePreview() {
    LibraryRoutePreviewScaffold {
        LibraryDesktopRoute(
            state = LibraryRouteState(
                catalog = LibraryRoutePreviewData.catalog,
                catalogRefreshing = false,
                filter = LibraryFilterTab.Albums,
                libraryUi = LibraryRoutePreviewData.libraryUi,
                searchQuery = "signals",
            ),
            actions = previewLibraryActions(),
            modifier = Modifier
                .fillMaxSize()
                .background(PhoebeUi.shellTop),
        )
    }
}

@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun LibraryMobileRoutePreview() {
    LibraryRoutePreviewScaffold {
        LibraryMobileRoute(
            state = LibraryRouteState(
                catalog = LibraryRoutePreviewData.catalog,
                catalogRefreshing = false,
                filter = LibraryFilterTab.Artists,
                libraryUi = LibraryRoutePreviewData.libraryUi,
            ),
            actions = previewLibraryActions(),
            modifier = Modifier
                .fillMaxSize()
                .background(PhoebeUi.shellTop),
        )
    }
}

@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun PlaylistDetailDesktopRoutePreview() {
    LibraryRoutePreviewScaffold {
        PlaylistDetailDesktopRoute(
            state = PlaylistDetailDesktopRouteState(
                playlist = LibraryRoutePreviewData.playlist,
                tracks = LibraryRoutePreviewData.tracks,
                catalogRefreshing = false,
                searchQuery = "",
                libraryUi = LibraryRoutePreviewData.libraryUi,
            ),
            actions = PlaylistDetailDesktopRouteActions(
                onSearchQuery = {},
                onPlayTracks = { _, _ -> },
                onAddToUpNext = {},
                onDownload = {},
                onLibraryColumns = {},
            ),
            modifier = Modifier
                .fillMaxSize()
                .background(PhoebeUi.shellTop),
        )
    }
}

@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun PlaylistsDesktopRoutePreview() {
    LibraryRoutePreviewScaffold {
        PlaylistsDesktopRoute(
            state = PlaylistsRouteState(
                catalogRefreshing = false,
                searchQuery = "",
            ),
            actions = PlaylistsRouteActions(
                onSearchQuery = {},
                onPlaylist = {},
            ),
            modifier = Modifier
                .fillMaxSize()
                .background(PhoebeUi.shellTop),
        )
    }
}

private fun previewLibraryActions(): LibraryRouteActions =
    LibraryRouteActions(
        onFilter = {},
        onLibrarySortBy = {},
        onLibraryAscending = {},
        onLibraryColumns = {},
        onArtist = {},
        onAlbum = {},
        onPlayTracks = { _, _ -> },
        onAddToUpNext = {},
        onDownload = {},
        onSearchQuery = {},
    )
