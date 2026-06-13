package com.phoebe.app.feature.collections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CollectionEntry

@Immutable
data class CollectionsRouteState(
    val entry: CollectionEntry,
    val catalog: CatalogSnapshot,
    val searchQuery: String = "",
    val supportedCollectionEntries: Set<CollectionEntry>? = null,
    val bottomContentPadding: Dp = 0.dp,
)

class CollectionsRouteActions(
    val onBack: () -> Unit,
    val onCollectionValue: (CollectionEntry, String) -> Unit,
    val onEnsureValuesLoaded: () -> Unit = {},
)

@Immutable
data class CollectionItemsRouteState(
    val entry: CollectionEntry,
    val value: String,
    val catalog: CatalogSnapshot,
    val searchQuery: String = "",
    val supportedCollectionEntries: Set<CollectionEntry>? = null,
    val bottomContentPadding: Dp = 0.dp,
)

class CollectionItemsRouteActions(
    val onBack: () -> Unit,
    val onArtist: (Artist) -> Unit,
    val onAlbum: (Album) -> Unit,
    val onEnsureItemsLoaded: () -> Unit = {},
)

@Composable
fun CollectionsRoute(
    state: CollectionsRouteState,
    actions: CollectionsRouteActions,
    modifier: Modifier = Modifier,
) {
    val supportedEntries = state.supportedCollectionEntries
    if (supportedEntries == null) {
        CollectionsScreen(
            entry = state.entry,
            catalog = state.catalog,
            modifier = modifier,
            searchQuery = state.searchQuery,
            bottomContentPadding = state.bottomContentPadding,
            onBack = actions.onBack,
            onCollectionValue = actions.onCollectionValue,
            onEnsureValuesLoaded = actions.onEnsureValuesLoaded,
        )
    } else {
        CollectionsScreen(
            entry = state.entry,
            catalog = state.catalog,
            modifier = modifier,
            searchQuery = state.searchQuery,
            supportedCollectionEntries = supportedEntries,
            bottomContentPadding = state.bottomContentPadding,
            onBack = actions.onBack,
            onCollectionValue = actions.onCollectionValue,
            onEnsureValuesLoaded = actions.onEnsureValuesLoaded,
        )
    }
}

@Composable
fun CollectionItemsRoute(
    state: CollectionItemsRouteState,
    actions: CollectionItemsRouteActions,
    modifier: Modifier = Modifier,
) {
    val supportedEntries = state.supportedCollectionEntries
    if (supportedEntries == null) {
        CollectionItemsScreen(
            entry = state.entry,
            value = state.value,
            catalog = state.catalog,
            modifier = modifier,
            searchQuery = state.searchQuery,
            bottomContentPadding = state.bottomContentPadding,
            onBack = actions.onBack,
            onArtist = actions.onArtist,
            onAlbum = actions.onAlbum,
            onEnsureItemsLoaded = actions.onEnsureItemsLoaded,
        )
    } else {
        CollectionItemsScreen(
            entry = state.entry,
            value = state.value,
            catalog = state.catalog,
            modifier = modifier,
            searchQuery = state.searchQuery,
            supportedCollectionEntries = supportedEntries,
            bottomContentPadding = state.bottomContentPadding,
            onBack = actions.onBack,
            onArtist = actions.onArtist,
            onAlbum = actions.onAlbum,
            onEnsureItemsLoaded = actions.onEnsureItemsLoaded,
        )
    }
}
