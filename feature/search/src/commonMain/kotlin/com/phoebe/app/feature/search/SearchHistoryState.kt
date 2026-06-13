package com.phoebe.app.feature.search

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Immutable
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.RecentSearchItem
import com.phoebe.app.domain.Track

@Immutable
data class SearchHistoryState(
    val recentItems: List<RecentSearchItem>,
    val recordArtist: (Artist) -> Unit,
    val recordAlbum: (Album) -> Unit,
    val recordTrack: (Track) -> Unit,
    val removeItem: (RecentSearchItem) -> Unit,
    val clearItems: () -> Unit,
)

val LocalSearchHistory = compositionLocalOf {
    SearchHistoryState(
        recentItems = emptyList(),
        recordArtist = {},
        recordAlbum = {},
        recordTrack = {},
        removeItem = {},
        clearItems = {},
    )
}
