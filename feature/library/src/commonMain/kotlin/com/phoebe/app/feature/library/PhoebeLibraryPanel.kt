package com.phoebe.app.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoebe.app.data.artistAlbumCountSubtitle
import com.phoebe.app.data.sortAlbumsForLibrary
import com.phoebe.app.data.sortArtistsForLibrary
import com.phoebe.app.data.sortTracksForLibrary
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.JellyfinLibraryPageKind
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.ui.*

@Composable
internal fun LibraryColumnDropdownRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = false,
            colors = CheckboxDefaults.colors(
                checkedColor = PhoebeUi.accentLight,
                uncheckedColor = PhoebeUi.mutedText,
                disabledCheckedColor = PhoebeUi.accentLight,
                disabledUncheckedColor = PhoebeUi.mutedText,
            ),
        )
        Text(label, color = PhoebeUi.primaryText, fontSize = 14.sp)
    }
}

@Composable
internal fun LibrarySortAndDisplayBar(
    prefs: LibraryUiPreferences,
    onSortBy: (LibrarySortBy) -> Unit,
    onAscending: (Boolean) -> Unit,
    onColumns: (LibraryColumnVisibility) -> Unit,
    showSortControls: Boolean = true,
    showColumns: Boolean = true,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var columnsExpanded by remember { mutableStateOf(false) }
    if (!showSortControls && !showColumns) return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSortControls) {
            Box {
                TextButton(onClick = { sortExpanded = true }) {
                    Text(
                        buildString {
                            append("Sort: ")
                            append(when (prefs.sortBy) {
                                LibrarySortBy.AlbumOrder -> "Album order"
                                LibrarySortBy.Name -> "Name"
                                LibrarySortBy.Artist -> "Artist"
                                LibrarySortBy.Album -> "Album"
                                LibrarySortBy.Year -> "Year"
                                LibrarySortBy.PlaylistOrder -> "Playlist order"
                                LibrarySortBy.DateAdded -> "Date added"
                            })
                            append(" ")
                            append(if (prefs.ascending) "↑" else "↓")
                        },
                        color = PhoebeUi.secondaryText,
                        fontSize = 13.sp,
                    )
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Name") },
                        onClick = {
                            onSortBy(LibrarySortBy.Name)
                            sortExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Year") },
                        onClick = {
                            onSortBy(LibrarySortBy.Year)
                            sortExpanded = false
                        },
                    )
                }
            }
            TextButton(onClick = { onAscending(!prefs.ascending) }) {
                Text(
                    if (prefs.ascending) "Ascending" else "Descending",
                    color = PhoebeUi.mutedText,
                    fontSize = 13.sp,
                )
            }
        }
        if (showSortControls || showColumns) {
            Spacer(Modifier.weight(1f))
        }
        if (showColumns) {
            Box {
                TextButton(onClick = { columnsExpanded = true }) {
                    Text("Columns", color = PhoebeUi.accentLight, fontSize = 13.sp)
                }
                DropdownMenu(expanded = columnsExpanded, onDismissRequest = { columnsExpanded = false }) {
                    LibraryColumnDropdownRow("Duration", prefs.columns.duration) {
                        onColumns(prefs.columns.copy(duration = !prefs.columns.duration))
                    }
                    LibraryColumnDropdownRow("Audio codec", prefs.columns.audioCodec) {
                        onColumns(prefs.columns.copy(audioCodec = !prefs.columns.audioCodec))
                    }
                    LibraryColumnDropdownRow("Bitrate", prefs.columns.bitrate) {
                        onColumns(prefs.columns.copy(bitrate = !prefs.columns.bitrate))
                    }
                    LibraryColumnDropdownRow("Sample rate", prefs.columns.sampleRate) {
                        onColumns(prefs.columns.copy(sampleRate = !prefs.columns.sampleRate))
                    }
                    LibraryColumnDropdownRow("File type", prefs.columns.fileType) {
                        onColumns(prefs.columns.copy(fileType = !prefs.columns.fileType))
                    }
                    LibraryColumnDropdownRow("Date added", prefs.columns.dateAdded) {
                        onColumns(prefs.columns.copy(dateAdded = !prefs.columns.dateAdded))
                    }
                    LibraryColumnDropdownRow("Rating", prefs.columns.rating) {
                        onColumns(prefs.columns.copy(rating = !prefs.columns.rating))
                    }
                    LibraryColumnDropdownRow("Favorite", prefs.columns.favorite) {
                        onColumns(prefs.columns.copy(favorite = !prefs.columns.favorite))
                    }
                    LibraryColumnDropdownRow("File path", prefs.columns.filepath) {
                        onColumns(prefs.columns.copy(filepath = !prefs.columns.filepath))
                    }
                    LibraryColumnDropdownRow("Year", prefs.columns.year) {
                        onColumns(prefs.columns.copy(year = !prefs.columns.year))
                    }
                    LibraryColumnDropdownRow("Genre", prefs.columns.genre) {
                        onColumns(prefs.columns.copy(genre = !prefs.columns.genre))
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryPanel(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    jellyfinPagination: Boolean,
    filter: LibraryFilterTab,
    libraryUi: LibraryUiPreferences,
    onFilter: (LibraryFilterTab) -> Unit,
    onLibrarySortBy: (LibrarySortBy) -> Unit,
    onLibraryAscending: (Boolean) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onJellyfinPage: (JellyfinLibraryPageKind, Int) -> Unit = { _, _ -> },
) {
    var pageIndex by remember(filter) { mutableStateOf(0) }
    val allTracksRaw = remember(catalog.tracksByParent) {
        catalog.tracksByParent.values.asSequence().flatten().distinctBy { it.id }.toList()
    }
    val sortBy = libraryUi.sortBy
    val ascending = libraryUi.ascending
    val sortedArtists = remember(catalog.artists, sortBy, ascending) {
        sortArtistsForLibrary(catalog, sortBy, ascending)
    }
    val sortedAlbums = remember(catalog.albums, sortBy, ascending) {
        sortAlbumsForLibrary(catalog.albums, sortBy, ascending)
    }
    val sortedTracks = remember(allTracksRaw, sortBy, ascending) {
        sortTracksForLibrary(allTracksRaw, sortBy, ascending)
    }
    val artistTotal = catalog.remotePageInfo.artistTotal
    val albumTotal = catalog.remotePageInfo.albumTotal
    val trackTotal = catalog.remotePageInfo.trackTotal
    val artistPage = remember(sortedArtists, jellyfinPagination, pageIndex, artistTotal) { libraryPage(sortedArtists, jellyfinPagination, pageIndex, artistTotal) }
    val albumPage = remember(sortedAlbums, jellyfinPagination, pageIndex, albumTotal) { libraryPage(sortedAlbums, jellyfinPagination, pageIndex, albumTotal) }
    val trackPage = remember(sortedTracks, jellyfinPagination, pageIndex, trackTotal) { libraryPage(sortedTracks, jellyfinPagination, pageIndex, trackTotal) }
    LaunchedEffect(filter, sortedArtists.size, sortedAlbums.size, sortedTracks.size) {
        val pageCount = when (filter) {
            LibraryFilterTab.Artists -> artistPage.pageCount
            LibraryFilterTab.Albums -> albumPage.pageCount
            LibraryFilterTab.Songs -> trackPage.pageCount
        }
        if (pageIndex > pageCount - 1) pageIndex = (pageCount - 1).coerceAtLeast(0)
    }
    val listState = RetainedLazyListStates.remember("library-panel-${filter.name}")
    val revealIndex by remember(listState) { derivedStateOf { listState.isScrollInProgress } }
    val scrollbarState by remember(listState) {
        derivedStateOf {
            LibraryScrollbarState(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                visibleItemsCount = listState.layoutInfo.visibleItemsInfo.size,
                totalItemsCount = listState.layoutInfo.totalItemsCount,
            )
        }
    }
    val scope = rememberCoroutineScope()
    val indexScrollDispatcher = rememberLibrarySectionIndexSelectionDispatcher()
    val sectionIndexEntries = remember(filter, artistPage.items, albumPage.items, trackPage.items, sortBy, ascending) {
        when (filter) {
            LibraryFilterTab.Artists -> libraryArtistScrollIndex(artistPage.items, sortBy, ascending)
            LibraryFilterTab.Albums -> libraryAlbumScrollIndex(albumPage.items, sortBy, ascending)
            LibraryFilterTab.Songs -> libraryTrackScrollIndex(trackPage.items, sortBy, ascending)
        }
    }
    val libraryItemsStartIndex = 3 + if (catalogRefreshing) 1 else 0
    var sectionIndexScrubbing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(contentType = "filter") {
                LibraryFilterToggle(filter, onFilter)
            }
            item(contentType = "library-sort") {
                LibrarySortAndDisplayBar(
                    prefs = libraryUi,
                    onSortBy = onLibrarySortBy,
                    onAscending = onLibraryAscending,
                    onColumns = onLibraryColumns,
                    showColumns = filter == LibraryFilterTab.Songs,
                )
            }
            if (catalogRefreshing) {
                item(contentType = "loading") { CatalogLoadingStrip() }
            }
            item(contentType = "pagination") {
                when (filter) {
                    LibraryFilterTab.Artists -> LibraryPaginationControls(artistPage, onPage = {
                        if (jellyfinPagination) onJellyfinPage(filter.toJellyfinPageKind(), it)
                        pageIndex = it
                    })
                    LibraryFilterTab.Albums -> LibraryPaginationControls(albumPage, onPage = {
                        if (jellyfinPagination) onJellyfinPage(filter.toJellyfinPageKind(), it)
                        pageIndex = it
                    })
                    LibraryFilterTab.Songs -> LibraryPaginationControls(trackPage, onPage = {
                        if (jellyfinPagination) onJellyfinPage(filter.toJellyfinPageKind(), it)
                        pageIndex = it
                    })
                }
            }
            when (filter) {
                LibraryFilterTab.Artists -> {
                    items(artistPage.items, key = { it.id }, contentType = { "artist" }) { artist ->
                        val onArtistClick = remember(artist, onArtist) { { onArtist(artist) } }
                        LibraryRow(artist.title, artistAlbumCountSubtitle(artist), artist.title, artist.thumbUrl, onClick = onArtistClick)
                    }
                }
                LibraryFilterTab.Albums -> {
                    items(albumPage.items, key = { it.id }, contentType = { "album" }) { album ->
                        val onAlbumClick = remember(album, onAlbum) { { onAlbum(album) } }
                        LibraryRow(album.title, "${album.artist} • ${album.year ?: "Album"}", album.title, album.thumbUrl, onClick = onAlbumClick)
                    }
                }
                LibraryFilterTab.Songs -> {
                    itemsIndexed(trackPage.items, key = { _, track -> track.id }, contentType = { _, _ -> "song" }) { index, track ->
                        val currentTracks by rememberUpdatedState(trackPage.items)
                        val onPlayClick = remember(index, onPlayTracks) { { onPlayTracks(currentTracks, index) } }
                        val onAddClick = remember(track, onAddToUpNext) { { onAddToUpNext(track) } }
                        val onDownloadClick = remember(track, onDownload) { { onDownload(track) } }
                        SongRow(
                            track = track,
                            selected = false,
                            columns = libraryUi.columns,
                            onSelect = onPlayClick,
                            onPlay = onPlayClick,
                            onAddToUpNext = onAddClick,
                            onDownload = onDownloadClick,
                            showPlaylistDragHandle = true,
                        )
                    }
                }
            }
            if (filter != LibraryFilterTab.Songs && filter != LibraryFilterTab.Artists) {
                item(contentType = "playlist-header") {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("Playlists", PhoebeUi.primaryText)
                }
                items(catalog.playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                    val onPlaylistClick = remember(playlist, onPlaylist) { { onPlaylist(playlist) } }
                    LibraryRow(
                        title = playlist.title,
                        subtitle = "${playlist.trackCount} songs",
                        seed = playlist.title,
                        thumbUrl = playlist.thumbUrl,
                        onClick = onPlaylistClick,
                    )
                }
            }
        }

        LibrarySectionIndex(
            entries = sectionIndexEntries,
            onEntrySelected = { entry ->
                indexScrollDispatcher.launch(scope, key = entry.itemIndex) { listState.scrollToItem(libraryItemsStartIndex + entry.itemIndex) }
            },
            onScrubbingChanged = { sectionIndexScrubbing = it },
            mode = LibrarySectionIndexMode.DesktopScrollbar,
            revealSignal = revealIndex,
            scrollbarState = scrollbarState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
internal fun LibraryFilterToggle(selected: LibraryFilterTab, onSelected: (LibraryFilterTab) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LibraryFilterTab.entries.forEach { filter ->
            val active = filter == selected
            Text(
                text = when (filter) {
                    LibraryFilterTab.Artists -> "Artists"
                    LibraryFilterTab.Albums -> "Albums"
                    LibraryFilterTab.Songs -> "All Songs"
                },
                color = if (active) PhoebeUi.primaryText else PhoebeUi.secondaryText,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelected(filter) }
                    .background(if (active) PhoebeUi.accent.copy(alpha = 0.42f) else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
internal fun LibraryRow(
    title: String,
    subtitle: String,
    seed: String,
    thumbUrl: String? = null,
    modifier: Modifier = Modifier,
    elevatedArtwork: Boolean = true,
    sharedKey: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PhoebeUi.shapes.controlRadius))
            .clickable(onClick = onClick)
            .background(PhoebeUi.subtleFill)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(seed, thumbUrl, Modifier.size(46.dp).sharedArtworkTransition(sharedKey), elevated = elevatedArtwork)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = PhoebeUi.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedBoundsTransition(sharedKey?.let { "$it:title" }),
            )
            Text(
                subtitle,
                color = PhoebeUi.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedBoundsTransition(sharedKey?.let { "$it:subtitle" }),
            )
        }
        PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.mutedText, modifier = Modifier.size(12.dp))
    }
}
