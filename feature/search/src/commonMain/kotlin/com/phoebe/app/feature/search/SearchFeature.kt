package com.phoebe.app.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.phoebe.app.domain.RecentSearchItem

@Composable
fun rememberSearchHistoryState(
    recentItems: List<RecentSearchItem>,
    onPrependRecentSearch: (RecentSearchItem) -> Unit,
    onRemoveRecentSearch: (RecentSearchItem) -> Unit,
    onClearRecentSearches: () -> Unit,
): SearchHistoryState = remember(recentItems, onPrependRecentSearch, onRemoveRecentSearch, onClearRecentSearches) {
    SearchHistoryState(
        recentItems = recentItems,
        recordArtist = { artist -> onPrependRecentSearch(RecentSearchItem.ArtistHit(artist)) },
        recordAlbum = { album -> onPrependRecentSearch(RecentSearchItem.AlbumHit(album)) },
        recordTrack = { track -> onPrependRecentSearch(RecentSearchItem.TrackHit(track)) },
        removeItem = onRemoveRecentSearch,
        clearItems = onClearRecentSearches,
    )
}
