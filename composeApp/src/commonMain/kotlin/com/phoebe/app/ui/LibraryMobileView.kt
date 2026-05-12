package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.phoebe.app.data.catalogAlbumCodec
import com.phoebe.app.data.catalogAlbumGenre
import com.phoebe.app.data.catalogAlbumsForArtist
import com.phoebe.app.data.catalogArtistGenre
import com.phoebe.app.data.catalogArtistTotalDurationMs
import com.phoebe.app.data.catalogTracksForAlbum
import com.phoebe.app.data.catalogTracksForArtist
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isPlexLibraryTrack

@Composable
internal fun LibraryMobileView(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    filter: LibraryFilterTab,
    libraryUi: LibraryUiPreferences,
    onFilter: (LibraryFilterTab) -> Unit,
    onLibrarySortBy: (LibrarySortBy) -> Unit,
    onLibraryAscending: (Boolean) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedArtistId by remember { mutableStateOf<String?>(null) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedTrackId by remember { mutableStateOf<String?>(null) }
    var libraryViewMode by remember { mutableStateOf(LibraryViewMode.Grid) }

    val ascending = libraryUi.ascending
    val sortBy = libraryUi.sortBy

    val sortedArtists = remember(catalog.artists, catalog.albums, sortBy, ascending) {
        sortArtistsForLibrary(catalog, sortBy, ascending)
    }
    val sortedAlbums = remember(catalog.albums, sortBy, ascending) {
        sortAlbumsForLibrary(catalog.albums, sortBy, ascending)
    }
    val allTracks = remember(catalog.tracksByParent) {
        catalog.tracksByParent.values.asSequence().flatten().distinctBy { it.id }.toList()
    }
    val sortedTracks = remember(allTracks, sortBy, ascending) {
        sortTracksForLibrary(allTracks, sortBy, ascending)
    }

    LaunchedEffect(filter) {
        // Clear stale selections when switching tabs.
        when (filter) {
            LibraryFilterTab.Artists -> { selectedAlbumId = null; selectedTrackId = null }
            LibraryFilterTab.Albums -> { selectedArtistId = null; selectedTrackId = null }
            LibraryFilterTab.Songs -> { selectedArtistId = null; selectedAlbumId = null }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MobileLibraryHeader()
        Spacer(Modifier.height(14.dp))
        MobileLibraryTabs(filter, onFilter)
        Spacer(Modifier.height(14.dp))
        MobileLibraryToolbar(
            prefs = libraryUi,
            filter = filter,
            onSortBy = onLibrarySortBy,
            onAscending = onLibraryAscending,
            libraryViewMode = libraryViewMode,
            onLibraryViewMode = { libraryViewMode = it },
            onColumns = onLibraryColumns,
        )
        Spacer(Modifier.height(10.dp))
        if (catalogRefreshing) {
            LibraryLoadingStrip(Modifier.padding(bottom = 6.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (filter) {
                LibraryFilterTab.Artists -> MobileArtistsContent(
                    catalog = catalog,
                    artists = sortedArtists,
                    selectedArtistId = selectedArtistId,
                    viewMode = libraryViewMode,
                    onSelect = { selectedArtistId = if (selectedArtistId == it.id) null else it.id },
                    onOpen = onArtist,
                )
                LibraryFilterTab.Albums -> MobileAlbumsContent(
                    catalog = catalog,
                    albums = sortedAlbums,
                    viewMode = libraryViewMode,
                    selectedAlbumId = selectedAlbumId,
                    onSelect = { selectedAlbumId = if (selectedAlbumId == it.id) null else it.id },
                    onOpen = onAlbum,
                )
                LibraryFilterTab.Songs -> MobileSongsList(
                    tracks = sortedTracks,
                    selectedTrackId = selectedTrackId,
                    onSelect = { selectedTrackId = if (selectedTrackId == it.id) null else it.id },
                    onPlay = { index -> onPlayTracks(sortedTracks, index) },
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                )
            }
        }
    }
}

@Composable
private fun MobileLibraryHeader() {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(PhoebeIcon.Bell, tint = PhoebeUi.secondaryText, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("Library", color = PhoebeUi.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(PhoebeIcon.Library, tint = PhoebeUi.secondaryText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MobileLibraryTabs(filter: LibraryFilterTab, onFilter: (LibraryFilterTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LibraryFilterTab.entries.forEach { tab ->
            val active = filter == tab
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onFilter(tab) }
                    .background(if (active) PhoebeUi.accent.copy(alpha = 0.22f) else Color.Transparent)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (tab) {
                        LibraryFilterTab.Artists -> "Artists"
                        LibraryFilterTab.Albums -> "Albums"
                        LibraryFilterTab.Songs -> "Songs"
                    },
                    color = if (active) PhoebeUi.primaryText else PhoebeUi.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MobileLibraryToolbar(
    prefs: LibraryUiPreferences,
    filter: LibraryFilterTab,
    onSortBy: (LibrarySortBy) -> Unit,
    onAscending: (Boolean) -> Unit,
    libraryViewMode: LibraryViewMode,
    onLibraryViewMode: (LibraryViewMode) -> Unit,
    onColumns: (LibraryColumnVisibility) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        var sortExpanded by remember { mutableStateOf(false) }
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { sortExpanded = true }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Sort: ", color = PhoebeUi.mutedText, fontSize = 12.sp)
                Text(
                    sortLabelFor(filter, prefs.sortBy),
                    color = PhoebeUi.primaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                PhoebeIconView(PhoebeIcon.ChevronDown, tint = PhoebeUi.mutedText, modifier = Modifier.size(12.dp))
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                when (filter) {
                    LibraryFilterTab.Artists -> {
                        DropdownMenuItem(
                            text = { Text("Artist name") },
                            onClick = { onSortBy(LibrarySortBy.Name); sortExpanded = false },
                        )
                    }
                    LibraryFilterTab.Albums -> {
                        DropdownMenuItem(
                            text = { Text("Album name") },
                            onClick = { onSortBy(LibrarySortBy.Name); sortExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Artist") },
                            onClick = { onSortBy(LibrarySortBy.Artist); sortExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Release date") },
                            onClick = { onSortBy(LibrarySortBy.Year); sortExpanded = false },
                        )
                    }
                    LibraryFilterTab.Songs -> {
                        DropdownMenuItem(
                            text = { Text("Song name") },
                            onClick = { onSortBy(LibrarySortBy.Name); sortExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Album name") },
                            onClick = { onSortBy(LibrarySortBy.Album); sortExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Artist") },
                            onClick = { onSortBy(LibrarySortBy.Artist); sortExpanded = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Release date") },
                            onClick = { onSortBy(LibrarySortBy.Year); sortExpanded = false },
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text(if (prefs.ascending) "Switch to Descending" else "Switch to Ascending") },
                    onClick = { onAscending(!prefs.ascending); sortExpanded = false },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        when (filter) {
            LibraryFilterTab.Artists, LibraryFilterTab.Albums -> Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(PhoebeUi.subtleFill).border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconToggle(PhoebeIcon.Grid, libraryViewMode == LibraryViewMode.Grid) { onLibraryViewMode(LibraryViewMode.Grid) }
                IconToggle(PhoebeIcon.Library, libraryViewMode == LibraryViewMode.List) { onLibraryViewMode(LibraryViewMode.List) }
            }
            LibraryFilterTab.Songs -> {
                var columnsExpanded by remember { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { columnsExpanded = true }
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PhoebeIconView(PhoebeIcon.Library, tint = PhoebeUi.secondaryText, modifier = Modifier.size(14.dp))
                    }
                    DropdownMenu(expanded = columnsExpanded, onDismissRequest = { columnsExpanded = false }) {
                        MobileColumnRow("Duration", prefs.columns.duration) { onColumns(prefs.columns.copy(duration = !prefs.columns.duration)) }
                        MobileColumnRow("Audio codec", prefs.columns.audioCodec) { onColumns(prefs.columns.copy(audioCodec = !prefs.columns.audioCodec)) }
                        MobileColumnRow("Bitrate", prefs.columns.bitrate) { onColumns(prefs.columns.copy(bitrate = !prefs.columns.bitrate)) }
                        MobileColumnRow("Sample rate", prefs.columns.sampleRate) { onColumns(prefs.columns.copy(sampleRate = !prefs.columns.sampleRate)) }
                        MobileColumnRow("File type", prefs.columns.fileType) { onColumns(prefs.columns.copy(fileType = !prefs.columns.fileType)) }
                        MobileColumnRow("Date added", prefs.columns.dateAdded) { onColumns(prefs.columns.copy(dateAdded = !prefs.columns.dateAdded)) }
                        MobileColumnRow("File path", prefs.columns.filepath) { onColumns(prefs.columns.copy(filepath = !prefs.columns.filepath)) }
                        MobileColumnRow("Year", prefs.columns.year) { onColumns(prefs.columns.copy(year = !prefs.columns.year)) }
                        MobileColumnRow("Genre", prefs.columns.genre) { onColumns(prefs.columns.copy(genre = !prefs.columns.genre)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileColumnRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LibraryCheckbox(checked = checked)
        Text(label, color = PhoebeUi.primaryText, fontSize = 13.sp)
    }
}

@Composable
private fun IconToggle(icon: PhoebeIcon, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .background(if (active) PhoebeUi.accent.copy(alpha = 0.22f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(icon, tint = if (active) PhoebeUi.primaryText else PhoebeUi.secondaryText, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MobileFavoriteIcon(favorited: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(
            PhoebeIcon.Heart,
            tint = if (favorited) PhoebeUi.accentLight else PhoebeUi.mutedText,
            modifier = Modifier.size(17.dp),
            filled = favorited,
        )
    }
}

// =====================================================================
// Artists (mobile grid/list)
// =====================================================================

@Composable
private fun MobileArtistsContent(
    catalog: CatalogSnapshot,
    artists: List<Artist>,
    selectedArtistId: String?,
    viewMode: LibraryViewMode,
    onSelect: (Artist) -> Unit,
    onOpen: (Artist) -> Unit,
) {
    if (artists.isEmpty()) {
        Text("No artists yet.", color = PhoebeUi.mutedText, fontSize = 13.sp)
        return
    }
    when (viewMode) {
        LibraryViewMode.Grid -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(artists, key = { it.id }) { artist ->
                MobileArtistCard(
                    catalog = catalog,
                    artist = artist,
                    selected = artist.id == selectedArtistId,
                    onSelect = { onSelect(artist) },
                    onOpen = { onOpen(artist) },
                )
            }
        }
        LibraryViewMode.List -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(artists, key = { it.id }) { artist ->
                MobileArtistRow(
                    catalog = catalog,
                    artist = artist,
                    selected = artist.id == selectedArtistId,
                    onSelect = { onSelect(artist) },
                    onOpen = { onOpen(artist) },
                )
            }
        }
    }
}

@Composable
private fun MobileArtistCard(
    catalog: CatalogSnapshot,
    artist: Artist,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val genre = remember(catalog, artist.title) { catalogArtistGenre(catalog, artist.title) }
    val albumCount = remember(catalog, artist.title) { catalogAlbumsForArtist(catalog, artist.title).size }
    val borderColor = if (selected) PhoebeUi.accent else Color.Transparent
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp))
            .background(if (selected) PhoebeUi.accent.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(112.dp).clip(CircleShape).clickable(onClick = onOpen)) {
            ArtworkImage(artist.title, artist.thumbUrl, Modifier.fillMaxSize(), radius = 56.dp)
        }
        Text(
            artist.title,
            color = PhoebeUi.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildString {
                genre?.let { append(it) }
                if (albumCount > 0) {
                    if (length > 0) append(" • ")
                    append("$albumCount ${if (albumCount == 1) "album" else "albums"}")
                }
            }.ifBlank { "Artist" },
            color = PhoebeUi.mutedText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MobileArtistRow(
    catalog: CatalogSnapshot,
    artist: Artist,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val genre = remember(catalog, artist.title) { catalogArtistGenre(catalog, artist.title) ?: "—" }
    val albumCount = remember(catalog, artist.title) { catalogAlbumsForArtist(catalog, artist.title).size }
    val songCount = remember(catalog, artist.title) { catalogTracksForArtist(catalog, artist.title).size }
    val durationMs = remember(catalog, artist.title) { catalogArtistTotalDurationMs(catalog, artist.title) }
    var favorited by remember(artist.id) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .background(if (selected) PhoebeUi.librarySelectedRow else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onOpen)) {
            ArtworkImage(artist.title, artist.thumbUrl, Modifier.fillMaxSize(), radius = 22.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(artist.title, color = PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$genre • $albumCount albums", color = PhoebeUi.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$songCount songs · ${formatHoursMinutes(durationMs)}", color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MobileFavoriteIcon(favorited = favorited) { favorited = !favorited }
        Box(
            Modifier.clip(CircleShape).clickable(onClick = onOpen).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.mutedText, modifier = Modifier.size(18.dp))
        }
    }
}

// =====================================================================
// Albums (mobile)
// =====================================================================

@Composable
private fun MobileAlbumsContent(
    catalog: CatalogSnapshot,
    albums: List<Album>,
    viewMode: LibraryViewMode,
    selectedAlbumId: String?,
    onSelect: (Album) -> Unit,
    onOpen: (Album) -> Unit,
) {
    if (albums.isEmpty()) {
        Text("No albums yet.", color = PhoebeUi.mutedText, fontSize = 13.sp)
        return
    }
    Column(Modifier.fillMaxSize()) {
        when (viewMode) {
            LibraryViewMode.Grid -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(albums, key = { it.id }) { album ->
                    MobileAlbumCard(
                        catalog = catalog,
                        album = album,
                        selected = album.id == selectedAlbumId,
                        onSelect = { onSelect(album) },
                        onOpen = { onOpen(album) },
                    )
                }
            }
            LibraryViewMode.List -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(albums, key = { it.id }) { album ->
                    MobileAlbumListRow(
                        catalog = catalog,
                        album = album,
                        selected = album.id == selectedAlbumId,
                        onSelect = { onSelect(album) },
                        onOpen = { onOpen(album) },
                    )
                }
            }
        }
        val selected = albums.firstOrNull { it.id == selectedAlbumId }
        if (selected != null) {
            MobileAlbumDetailSheet(catalog = catalog, album = selected, onClose = { onSelect(selected) }, onOpen = { onOpen(selected) })
        }
    }
}

@Composable
private fun MobileAlbumCard(
    catalog: CatalogSnapshot,
    album: Album,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val tracks = remember(catalog, album.id) { catalogTracksForAlbum(catalog, album.id) }
    val durationMs = remember(tracks) { tracks.sumOf { it.durationMs } }
    val borderColor = if (selected) PhoebeUi.accent else Color.Transparent
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
            .background(if (selected) PhoebeUi.accent.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onOpen)) {
            ArtworkImage(album.title, album.thumbUrl, Modifier.fillMaxSize(), radius = 10.dp)
            if (selected) {
                Box(
                    Modifier
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onSelect),
                    contentAlignment = Alignment.Center,
                ) {
                    PhoebeIconView(PhoebeIcon.Close, tint = PhoebeUi.primaryText, modifier = Modifier.size(11.dp))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(album.title, color = PhoebeUi.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artist, color = PhoebeUi.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    album.year?.let { append(it.toString()) }
                    if (length > 0) append(" • ")
                    if (durationMs > 0L) append(formatMinutesLabel(durationMs))
                },
                color = PhoebeUi.mutedText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MobileAlbumListRow(
    catalog: CatalogSnapshot,
    album: Album,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val tracks = remember(catalog, album.id) { catalogTracksForAlbum(catalog, album.id) }
    val durationMs = remember(tracks) { tracks.sumOf { it.durationMs } }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .background(if (selected) PhoebeUi.librarySelectedRow else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clickable(onClick = onOpen)) {
            ArtworkImage(album.title, album.thumbUrl, Modifier.fillMaxSize(), radius = 8.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(album.title, color = PhoebeUi.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artist, color = PhoebeUi.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    album.year?.let { append(it.toString()) }
                    if (tracks.isNotEmpty()) {
                        if (length > 0) append(" • ")
                        append("${tracks.size} tracks")
                    }
                    if (durationMs > 0L) {
                        if (length > 0) append(" • ")
                        append(formatMinutesLabel(durationMs))
                    }
                },
                color = PhoebeUi.mutedText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MobileAlbumDetailSheet(
    catalog: CatalogSnapshot,
    album: Album,
    onClose: () -> Unit,
    onOpen: () -> Unit,
) {
    val tracks = remember(catalog, album.id) { catalogTracksForAlbum(catalog, album.id) }
    val codec = remember(catalog, album.id) { catalogAlbumCodec(catalog, album.id) }
    val genre = remember(catalog, album.id) { catalogAlbumGenre(catalog, album.id) }
    val durationMs = remember(tracks) { tracks.sumOf { it.durationMs } }
    var favorited by remember(album.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 280.dp)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f)))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(58.dp).clickable(onClick = onOpen)) {
                ArtworkImage(album.title, album.thumbUrl, Modifier.fillMaxSize(), radius = 8.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(album.title, color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist.uppercase(), color = PhoebeUi.secondaryText, fontSize = 11.sp, letterSpacing = 0.06.em, maxLines = 1)
            }
            MobileFavoriteIcon(favorited = favorited) { favorited = !favorited }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MobileMetaCell("Genre", genre ?: "—", Modifier.weight(1f))
            MobileMetaCell("Tracks", tracks.size.toString(), Modifier.weight(1f))
            MobileMetaCell("Duration", formatMinutesLabel(durationMs), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MobileMetaCell("Codec", codec ?: "—", Modifier.weight(1f))
            MobileMetaCell("Quality", if (codec.equals("FLAC", true) || codec.equals("ALAC", true)) "Lossless" else "—", Modifier.weight(1f))
            MobileMetaCell("Sample Rate", tracks.firstOrNull()?.let { displaySampleRateLabel(it) } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MobileMetaCell("Location", "Local Library", Modifier.weight(1f))
            MobileMetaCell("Path", tracks.firstOrNull()?.filepath?.let(::shortenFilepath)?.let { "/$it" } ?: "—", Modifier.weight(2f))
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                "Close",
                color = PhoebeUi.accentLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun MobileMetaCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = PhoebeUi.mutedText, fontSize = 10.sp)
        Text(value, color = PhoebeUi.primaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =====================================================================
// Songs list (mobile)
// =====================================================================

@Composable
private fun MobileSongsList(
    tracks: List<Track>,
    selectedTrackId: String?,
    onSelect: (Track) -> Unit,
    onPlay: (Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        Text("No songs yet.", color = PhoebeUi.mutedText, fontSize = 13.sp)
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(tracks.size, key = { tracks[it].id }) { index ->
                val track = tracks[index]
                MobileSongRow(
                    track = track,
                    selected = track.id == selectedTrackId,
                    onSelect = { onSelect(track) },
                    onPlay = { onPlay(index) },
                    onAddToUpNext = { onAddToUpNext(track) },
                    onDownload = { onDownload(track) },
                )
            }
        }
        val selected = tracks.firstOrNull { it.id == selectedTrackId }
        if (selected != null) {
            MobileSongDetailSheet(
                track = selected,
                onClose = { onSelect(selected) },
                onPlay = {
                    val idx = tracks.indexOfFirst { it.id == selected.id }
                    if (idx >= 0) onPlay(idx)
                },
                onAddToPlaylist = { onAddToUpNext(selected) },
                onDownload = { onDownload(selected) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileSongRow(
    track: Track,
    selected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onAddToUpNext: () -> Unit,
    onDownload: () -> Unit,
) {
    var menuExpanded by remember(track.id) { mutableStateOf(false) }
    val metadataEditorActions = LocalMetadataEditorActions.current
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = nowPlaying.trackId == track.id
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { menuExpanded = true },
            )
            .background(
                when {
                    isCurrent -> PhoebeUi.accent.copy(alpha = 0.14f)
                    selected -> PhoebeUi.librarySelectedRow
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
            ArtworkImage(track.album, track.thumbUrl, Modifier.fillMaxSize(), radius = 8.dp)
            if (isCurrent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    NowPlayingIndicator(
                        isPlaying = nowPlaying.isPlaying,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track.title,
                color = if (isCurrent) PhoebeUi.accentLight else PhoebeUi.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(track.artist)
                    track.audioCodec?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it.uppercase()) }
                    if (isLossless(track)) append(" • 44.1 kHz • Lossless")
                },
                color = PhoebeUi.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatMinutesSeconds(track.durationMs), color = PhoebeUi.mutedText, fontSize = 11.sp)
        Box {
            Text(
                "⋯",
                color = PhoebeUi.secondaryText,
                fontSize = 17.sp,
                modifier = Modifier.clip(CircleShape).clickable(onClick = { menuExpanded = true }).padding(horizontal = 4.dp),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Metadata") },
                    onClick = {
                        metadataEditorActions.onRequestEdit(track)
                        menuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add to Up Next") },
                    onClick = {
                        onAddToUpNext()
                        menuExpanded = false
                    },
                )
                AddToPlaylistMenuItems(
                    track = track,
                    onAfter = { menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text("Download Song") },
                    onClick = {
                        onDownload()
                        menuExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MobileSongDetailSheet(
    track: Track,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
) {
    var favorited by remember(track.id) { mutableStateOf(false) }
    var playlistMenuExpanded by remember(track.id) { mutableStateOf(false) }
    val metadataEditorActions = LocalMetadataEditorActions.current
    val playlistActions = LocalPlaylistActions.current
    val showPlaylistPill = playlistActions.playlistsEnabled &&
        track.isPlexLibraryTrack() &&
        !track.isLocalMediaPlayback()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 320.dp)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(onClick = onPlay)) {
                ArtworkImage(track.album, track.thumbUrl, Modifier.fillMaxSize(), radius = 8.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = PhoebeUi.primaryText, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist.uppercase(), color = PhoebeUi.secondaryText, fontSize = 11.sp, letterSpacing = 0.06.em, maxLines = 1)
            }
            MobileFavoriteIcon(favorited = favorited) { favorited = !favorited }
            Text(
                "✕",
                color = PhoebeUi.mutedText,
                fontSize = 14.sp,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MobileMetaCell("Duration", formatMinutesSeconds(track.durationMs), Modifier.weight(1f))
            MobileMetaCell("Channels", "2 (Stereo)", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MobileMetaCell("Codec", track.audioCodec?.uppercase() ?: "—", Modifier.weight(1f))
            MobileMetaCell("File Size", "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MobileMetaCell("Bitrate", displayBitrateLabel(track), Modifier.weight(1f))
            MobileMetaCell("Date Added", "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MobileMetaCell("Sample Rate", displaySampleRateLabel(track), Modifier.weight(1f))
            MobileMetaCell("Play Count", "—", Modifier.weight(1f))
        }
        MobileMetaCell("File Path", track.filepath?.let(::shortenFilepath)?.let { "/$it" } ?: "—")
        Spacer(Modifier.height(2.dp))
        MobileActionPill(
            "Edit Metadata",
            { metadataEditorActions.onRequestEdit(track) },
            Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showPlaylistPill) {
                Box(Modifier.weight(1f)) {
                    MobileActionPill("Add to Playlist", { playlistMenuExpanded = true }, Modifier.fillMaxWidth())
                    DropdownMenu(
                        expanded = playlistMenuExpanded,
                        onDismissRequest = { playlistMenuExpanded = false },
                    ) {
                        AddToPlaylistMenuItems(
                            track = track,
                            onAfter = { playlistMenuExpanded = false },
                        )
                    }
                }
                MobileActionPill("Download", onDownload, Modifier.weight(1f))
            } else {
                MobileActionPill("Download", onDownload, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MobileActionPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .background(PhoebeUi.accent.copy(alpha = 0.18f))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
