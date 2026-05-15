package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phoebe.app.data.catalogAlbumGenre
import com.phoebe.app.data.catalogAlbumTrackStatsLoading
import com.phoebe.app.data.catalogAlbumsForArtist
import com.phoebe.app.data.catalogArtistAlbumCountLoading
import com.phoebe.app.data.catalogArtistGenre
import com.phoebe.app.data.catalogArtistTrackStatsLoading
import com.phoebe.app.data.catalogTracksForAlbum
import com.phoebe.app.data.catalogTracksForArtist
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.CollectionFacet
import com.phoebe.app.domain.CollectionTarget
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.canTogglePlexLike

@Composable
internal fun DesktopHomeScreen(
    state: HomeUiState,
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    onTrack: (Track) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onRecentSongs: () -> Unit,
    onRecentArtists: () -> Unit,
    onRecentAlbums: () -> Unit,
    onCollections: (CollectionEntry) -> Unit,
    onRecentlyPlayed: () -> Unit,
    onMostPlayed: () -> Unit,
    onRefreshArtists: () -> Unit,
    onRefreshAlbums: () -> Unit,
    onPrefetchArtist: (Artist) -> Unit = {},
    onPrefetchAlbum: (Album) -> Unit = {},
    onPlayDecadeMix: (Int) -> Unit = {},
    decadeMixNotice: String? = null,
    onClearDecadeMixNotice: () -> Unit = {},
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    var showDecadeMix by remember { mutableStateOf(false) }
    if (showDecadeMix) {
        DecadeMixDialog(
            decades = defaultMixDecades(),
            notice = decadeMixNotice,
            onDismiss = {
                showDecadeMix = false
                onClearDecadeMixNotice()
            },
            onSelect = { decade ->
                onPlayDecadeMix(decade)
            },
        )
    }
    val playedPanelMaxRows = 3
    val sharedPlayedTrackKeys = remember(state.mostPlayedTracks, state.recentlyPlayedTracks) {
        val visibleTrackIds = state.mostPlayedTracks.take(playedPanelMaxRows).map { it.track.id } +
            state.recentlyPlayedTracks.take(playedPanelMaxRows).map { it.track.id }
        visibleTrackIds.groupingBy { it }.eachCount()
    }
    val sharedKeyForPlayedTrack: (Track) -> String? = { track ->
        if (sharedPlayedTrackKeys[track.id] == 1) "song:${track.id}" else null
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Home", color = PhoebeUi.primaryText, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
                Text("Pick up where you left off", color = PhoebeUi.secondaryText, fontSize = 13.sp)
            }
        }
        if (catalogRefreshing) {
            item(contentType = "loading") { CatalogLoadingStrip() }
        }
        item("top") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomePanel(Modifier.weight(1f).height(156.dp)) {
                        SectionLabel("CREATE A MIX", PhoebeUi.mutedText)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeActionCard(
                                "Personal Mix",
                                "Recent favorites, familiar anchors, and a little discovery",
                                PhoebeIcon.Music,
                                Modifier.weight(1f),
                            ) {
                                val tracks = personalMix(catalog, state)
                                if (tracks.isNotEmpty()) onPlayTracks(tracks, 0)
                            }
                            HomeActionCard("Decade Mix", "Queue a shuffled era from your library", PhoebeIcon.Grid, Modifier.weight(1f)) {
                                onClearDecadeMixNotice()
                                showDecadeMix = true
                            }
                        }
                    }
                    Column(Modifier.width(330.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard(PhoebeIcon.Music, "Recently Added Songs", onRecentSongs)
                        SummaryCard(PhoebeIcon.Library, "Recently Added Artists", onRecentArtists)
                        SummaryCard(PhoebeIcon.Play, "Recently Added Albums", onRecentAlbums)
                    }
                }
                HomePanel(Modifier.fillMaxWidth()) {
                    SectionLabel("COLLECTIONS", PhoebeUi.mutedText)
                    DesktopCollectionsGrid(onCollections)
                }
            }
        }
        item("middle") {
            val panelHeight = 320.dp
            val randomPanelHeight = 276.dp
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 820.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        MostPlayedPanel(
                            state.mostPlayedTracks,
                            onPlayTracks,
                            onAddToUpNext,
                            onDownload,
                            onMostPlayed,
                            Modifier.fillMaxWidth(),
                            maxRows = playedPanelMaxRows,
                            sharedKeyForTrack = sharedKeyForPlayedTrack,
                        )
                        RecentPlayedPanel(
                            state,
                            onPlayTracks,
                            onAddToUpNext,
                            onDownload,
                            onRecentlyPlayed,
                            Modifier.fillMaxWidth(),
                            maxRows = playedPanelMaxRows,
                            sharedKeyForTrack = sharedKeyForPlayedTrack,
                        )
                        RandomArtistPanel(
                            artist = state.randomArtists.firstOrNull(),
                            catalog = catalog,
                            catalogRefreshing = catalogRefreshing,
                            onArtist = onArtist,
                            onRefresh = onRefreshArtists,
                            onPrefetch = onPrefetchArtist,
                            modifier = Modifier.fillMaxWidth().height(randomPanelHeight),
                        )
                        RandomAlbumPanel(
                            album = state.randomAlbums.firstOrNull(),
                            catalog = catalog,
                            catalogRefreshing = catalogRefreshing,
                            onAlbum = onAlbum,
                            onRefresh = onRefreshAlbums,
                            onPrefetch = onPrefetchAlbum,
                            modifier = Modifier.fillMaxWidth().height(randomPanelHeight),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth().height(panelHeight), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            MostPlayedPanel(
                                state.mostPlayedTracks,
                                onPlayTracks,
                                onAddToUpNext,
                                onDownload,
                                onMostPlayed,
                                Modifier.weight(1f).fillMaxHeight(),
                                maxRows = playedPanelMaxRows,
                                sharedKeyForTrack = sharedKeyForPlayedTrack,
                                rowHeight = 78.dp,
                            )
                            RecentPlayedPanel(
                                state,
                                onPlayTracks,
                                onAddToUpNext,
                                onDownload,
                                onRecentlyPlayed,
                                Modifier.weight(1f).fillMaxHeight(),
                                maxRows = playedPanelMaxRows,
                                sharedKeyForTrack = sharedKeyForPlayedTrack,
                                rowHeight = 78.dp,
                            )
                        }
                        Row(Modifier.fillMaxWidth().height(randomPanelHeight), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            RandomArtistPanel(
                                artist = state.randomArtists.firstOrNull(),
                                catalog = catalog,
                                catalogRefreshing = catalogRefreshing,
                                onArtist = onArtist,
                                onRefresh = onRefreshArtists,
                                onPrefetch = onPrefetchArtist,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            RandomAlbumPanel(
                                album = state.randomAlbums.firstOrNull(),
                                catalog = catalog,
                                catalogRefreshing = catalogRefreshing,
                                onAlbum = onAlbum,
                                onRefresh = onRefreshAlbums,
                                onPrefetch = onPrefetchAlbum,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MobileHomeScreen(
    state: HomeUiState,
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    onTrack: (Track) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onRecentSongs: () -> Unit,
    onRecentArtists: () -> Unit,
    onRecentAlbums: () -> Unit,
    onCollections: (CollectionEntry) -> Unit,
    onRecentlyPlayed: () -> Unit,
    onMostPlayed: () -> Unit,
    onRefreshArtists: () -> Unit,
    onRefreshAlbums: () -> Unit,
    onPrefetchArtist: (Artist) -> Unit = {},
    onPrefetchAlbum: (Album) -> Unit = {},
    onPlayDecadeMix: (Int) -> Unit = {},
    decadeMixNotice: String? = null,
    onClearDecadeMixNotice: () -> Unit = {},
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    var showDecadeMix by remember { mutableStateOf(false) }
    if (showDecadeMix) {
        DecadeMixDialog(
            decades = defaultMixDecades(),
            notice = decadeMixNotice,
            onDismiss = {
                showDecadeMix = false
                onClearDecadeMixNotice()
            },
            onSelect = { decade ->
                onPlayDecadeMix(decade)
            },
        )
    }
    val recentTracks = remember(state.recentlyAddedTracks) { state.recentlyAddedTracks.take(10) }
    val recentArtists = remember(state.recentlyAddedArtists) { state.recentlyAddedArtists.take(10) }
    val recentAlbums = remember(state.recentlyAddedAlbums) { state.recentlyAddedAlbums.take(10) }
    val randomArtists = remember(state.randomArtists) { state.randomArtists.take(10) }
    val randomAlbums = remember(state.randomAlbums) { state.randomAlbums.take(10) }
    val visibleArtists = remember(recentArtists, randomArtists) {
        (recentArtists + randomArtists).distinctBy { it.id }
    }
    val artistThumbs = remember(visibleArtists, catalog.albums) {
        val visibleArtistNames = visibleArtists.map { it.title.lowercase() }.toSet()
        val albumThumbByArtist = buildMap {
            catalog.albums.asSequence()
                .filter { album -> album.thumbUrl != null && album.artist.lowercase() in visibleArtistNames }
                .forEach { album ->
                    val artistName = album.artist.lowercase()
                    if (artistName !in this) put(artistName, album.thumbUrl!!)
                }
        }
        buildMap {
            visibleArtists.forEach { artist ->
                val thumb = artist.thumbUrl ?: albumThumbByArtist[artist.title.lowercase()]
                if (thumb != null) put(artist.id, thumb)
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (catalogRefreshing) {
            item(contentType = "loading") { CatalogLoadingStrip() }
        }
        item("mix") {
            SectionLabel("CREATE A MIX", PhoebeUi.mutedText)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MobileActionCard("Personal", PhoebeIcon.Music, Modifier.weight(1f)) {
                    val tracks = personalMix(catalog, state)
                    if (tracks.isNotEmpty()) onPlayTracks(tracks, 0)
                }
                MobileActionCard("Decade", PhoebeIcon.Grid, Modifier.weight(1f)) {
                    onClearDecadeMixNotice()
                    showDecadeMix = true
                }
            }
        }
        item("collections") {
            SectionLabel("COLLECTIONS", PhoebeUi.mutedText)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                collectionEntryRows().forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { entry ->
                            MobileActionCard(entry.mobileTitle, entry.icon, Modifier.weight(1f)) {
                                onCollections(entry.collectionEntry)
                            }
                        }
                    }
                }
            }
        }
        item("recent-songs") {
            SectionHeader("RECENTLY ADDED SONGS", "See all", onRecentSongs)
            if (recentTracks.isEmpty()) {
                HomeEmptyState("New songs from Plex and local folders will appear here.")
            } else {
                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentTracks, key = { it.id }, contentType = { "recent-song" }) { track ->
                        HomeArtworkTile(
                            title = track.title,
                            subtitle = track.artist,
                            thumbUrl = track.localArtworkUri,
                            fallbackThumbUrl = track.thumbUrl,
                            modifier = Modifier.width(78.dp),
                            maxDecodeDimension = 160,
                            sharedKey = "song:${track.id}",
                            onClick = { onTrack(track) },
                        )
                    }
                }
            }
        }
        item("recent-artists") {
            SectionHeader("RECENTLY ADDED ARTISTS", "See all", onRecentArtists)
            if (recentArtists.isEmpty()) {
                HomeEmptyState("New artists from Plex and local folders will appear here.")
            } else {
                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentArtists, key = { it.id }, contentType = { "recent-artist" }) { artist ->
                        MobileArtistTile(
                            artist = artist,
                            thumbUrl = artistThumbs[artist.id],
                            sharedKey = "artist:${artist.id}",
                            onClick = { onArtist(artist) },
                        )
                    }
                }
            }
        }
        item("recent-albums") {
            SectionHeader("RECENTLY ADDED ALBUMS", "See all", onRecentAlbums)
            if (recentAlbums.isEmpty()) {
                HomeEmptyState("New albums from Plex and local folders will appear here.")
            } else {
                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentAlbums, key = { it.id }, contentType = { "recent-album" }) { album ->
                        HomeArtworkTile(
                            title = album.title,
                            subtitle = album.artist,
                            thumbUrl = album.thumbUrl,
                            modifier = Modifier.width(92.dp),
                            maxDecodeDimension = 180,
                            sharedKey = "album:${album.id}",
                            onClick = { onAlbum(album) },
                        )
                    }
                }
            }
        }
        item("played") {
            HomePanel(Modifier.fillMaxWidth()) {
                SectionHeader("RECENTLY PLAYED", "See all", onRecentlyPlayed)
                if (state.recentlyPlayedTracks.isEmpty()) {
                    HomeEmptyState("Play a song and your recent listening history will show up here.")
                } else {
                    val tracks = state.recentlyPlayedTracks.map { it.track }
                    state.recentlyPlayedTracks.take(4).forEachIndexed { index, row ->
                        HomePlayedTrackRow(
                            track = row.track,
                            onPlay = { onPlayTracks(tracks, index) },
                            onAddToUpNext = { onAddToUpNext(row.track) },
                            onDownload = { onDownload(row.track) },
                            showFavoriteAction = false,
                        )
                    }
                }
            }
        }
        item("most-played") {
            MostPlayedPanel(
                rows = state.mostPlayedTracks,
                onPlayTracks = onPlayTracks,
                onAddToUpNext = onAddToUpNext,
                onDownload = onDownload,
                onViewAll = onMostPlayed,
                modifier = Modifier.fillMaxWidth(),
                showFavoriteAction = false,
            )
        }
        item("artists") {
            MobileRandomArtistsPanel(
                artists = randomArtists,
                artistThumbs = artistThumbs,
                onArtist = onArtist,
                onRefresh = onRefreshArtists,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item("albums") {
            MobileRandomAlbumsPanel(
                albums = randomAlbums,
                onAlbum = onAlbum,
                onRefresh = onRefreshAlbums,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HomePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun HomeEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            color = PhoebeUi.mutedText,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.92f),
        )
    }
}

private data class HomeCollectionEntry(
    val collectionEntry: CollectionEntry,
    val homeTitle: String,
    val homeSubtitle: String,
    val mobileTitle: String,
    val icon: PhoebeIcon,
)

private fun collectionEntryRows(): List<List<HomeCollectionEntry>> =
    collectionEntries().chunked(2)

private fun collectionEntries(): List<HomeCollectionEntry> =
    listOf(
        HomeCollectionEntry(
            collectionEntry = CollectionEntry(CollectionTarget.Artists, CollectionFacet.Mood),
            homeTitle = "Artist Mood",
            homeSubtitle = "Browse artist mood tags",
            mobileTitle = "Artist Mood",
            icon = PhoebeIcon.Library,
        ),
        HomeCollectionEntry(
            collectionEntry = CollectionEntry(CollectionTarget.Albums, CollectionFacet.Mood),
            homeTitle = "Album Mood",
            homeSubtitle = "Browse album mood tags",
            mobileTitle = "Album Mood",
            icon = PhoebeIcon.Grid,
        ),
        HomeCollectionEntry(
            collectionEntry = CollectionEntry(CollectionTarget.Artists, CollectionFacet.Style),
            homeTitle = "Artist Style",
            homeSubtitle = "Browse artist style tags",
            mobileTitle = "Artist Style",
            icon = PhoebeIcon.Library,
        ),
        HomeCollectionEntry(
            collectionEntry = CollectionEntry(CollectionTarget.Albums, CollectionFacet.Style),
            homeTitle = "Album Style",
            homeSubtitle = "Browse album style tags",
            mobileTitle = "Album Style",
            icon = PhoebeIcon.Grid,
        ),
        HomeCollectionEntry(
            collectionEntry = CollectionEntry(CollectionTarget.Artists, CollectionFacet.Genre),
            homeTitle = "Artist Genre",
            homeSubtitle = "Browse artist genres",
            mobileTitle = "Artist Genre",
            icon = PhoebeIcon.Library,
        ),
        HomeCollectionEntry(
            collectionEntry = CollectionEntry(CollectionTarget.Albums, CollectionFacet.Genre),
            homeTitle = "Album Genre",
            homeSubtitle = "Browse album genres",
            mobileTitle = "Album Genre",
            icon = PhoebeIcon.Grid,
        ),
    )

@Composable
private fun DesktopCollectionsGrid(onCollections: (CollectionEntry) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 760.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            collectionEntries().chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(82.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { entry ->
                        HomeActionCard(
                            title = entry.homeTitle,
                            subtitle = entry.homeSubtitle,
                            icon = entry.icon,
                            modifier = Modifier.weight(1f),
                        ) {
                            onCollections(entry.collectionEntry)
                        }
                    }
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionCard(title: String, subtitle: String, icon: PhoebeIcon, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(PhoebeUi.subtleFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeActionIcon(icon, 46.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = PhoebeUi.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = PhoebeUi.secondaryText, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.secondaryText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MobileActionCard(label: String, icon: PhoebeIcon, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(PhoebeUi.subtleFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HomeActionIcon(icon, 38.dp)
        Spacer(Modifier.height(7.dp))
        Text(label, color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun DecadeMixDialog(
    decades: List<Int>,
    notice: String?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(min = 300.dp, max = 420.dp)
                    .heightIn(max = maxHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PhoebeUi.modalSurface)
                    .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Decade Mix", color = PhoebeUi.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (!notice.isNullOrBlank()) {
                    Text(notice, color = PhoebeUi.accentLight, fontSize = 13.sp, lineHeight = 18.sp)
                }
                if (decades.isEmpty()) {
                    Text("No decade choices are available.", color = PhoebeUi.secondaryText, fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(decades, key = { it }) { decade ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelect(decade) }
                                    .background(PhoebeUi.elevatedFill)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${decade}s", color = PhoebeUi.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.secondaryText, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionIcon(icon: PhoebeIcon, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(PhoebeUi.accentLight.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, PhoebeUi.accentLight.copy(alpha = 0.22f)), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(icon, tint = PhoebeUi.accentLight, modifier = Modifier.size(size * 0.44f))
    }
}

@Composable
private fun SummaryCard(icon: PhoebeIcon, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)).background(PhoebeUi.accentLight.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            PhoebeIconView(icon, tint = PhoebeUi.accentLight, modifier = Modifier.size(17.dp))
        }
        Text(title, color = PhoebeUi.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.secondaryText, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RecentPlayedPanel(
    state: HomeUiState,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
    maxRows: Int = 4,
    sharedKeyForTrack: (Track) -> String? = { track -> "song:${track.id}" },
    showFavoriteAction: Boolean = true,
    rowHeight: Dp = 88.dp,
) {
    HomePanel(modifier) {
        SectionHeader("RECENTLY PLAYED", "View all", onViewAll)
        if (state.recentlyPlayedTracks.isEmpty()) {
            HomeEmptyState("Nothing here yet. Play something and your recent listening history will appear.")
        } else {
            val tracks = state.recentlyPlayedTracks.map { it.track }
            state.recentlyPlayedTracks.take(maxRows).forEachIndexed { index, row ->
                HomePlayedTrackRow(
                    track = row.track,
                    onPlay = { onPlayTracks(tracks, index) },
                    onAddToUpNext = { onAddToUpNext(row.track) },
                    onDownload = { onDownload(row.track) },
                    sharedKey = sharedKeyForTrack(row.track),
                    showFavoriteAction = showFavoriteAction,
                    rowHeight = rowHeight,
                )
            }
        }
    }
}

@Composable
private fun MobileRandomArtistsPanel(
    artists: List<Artist>,
    artistThumbs: Map<String, String>,
    onArtist: (Artist) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomePanel(modifier) {
        SectionHeader("RANDOM ARTISTS", "Refresh", onRefresh)
        if (artists.isEmpty()) {
            HomeEmptyState("Add music to your library to discover artists here.")
        } else {
            ShuffleAnimatedRow(targetKey = artists.joinToString("|") { it.id }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(artists, key = { it.id }, contentType = { "random-artist" }) { artist ->
                        MobileArtistTile(
                            artist = artist,
                            thumbUrl = artistThumbs[artist.id],
                            sharedKey = "artist:${artist.id}",
                            onClick = { onArtist(artist) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileRandomAlbumsPanel(
    albums: List<Album>,
    onAlbum: (Album) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomePanel(modifier) {
        SectionHeader("RANDOM ALBUMS", "Refresh", onRefresh)
        if (albums.isEmpty()) {
            HomeEmptyState("Add music to your library to discover albums here.")
        } else {
            ShuffleAnimatedRow(targetKey = albums.joinToString("|") { it.id }) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(albums, key = { it.id }, contentType = { "random-album" }) { album ->
                        HomeArtworkTile(
                            title = album.title,
                            subtitle = album.artist,
                            thumbUrl = album.thumbUrl,
                            modifier = Modifier.width(92.dp),
                            maxDecodeDimension = 180,
                            sharedKey = "album:${album.id}",
                            onClick = { onAlbum(album) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShuffleAnimatedRow(targetKey: String, content: @Composable () -> Unit) {
    AnimatedContent(
        targetState = targetKey,
        transitionSpec = {
            (slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(160))) togetherWith
                (slideOutHorizontally(tween(180)) { -it / 6 } + fadeOut(tween(120)))
        },
        label = "shuffle-row",
    ) {
        content()
    }
}

@Composable
private fun MobileArtistTile(
    artist: Artist,
    thumbUrl: String?,
    sharedKey: String? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(82.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtworkImage(
            artist.title,
            thumbUrl,
            Modifier
                .size(66.dp)
                .sharedArtworkTransition(sharedKey)
                .clip(CircleShape),
            radius = 33.dp,
            elevated = false,
            maxDecodeDimension = 160,
        )
        Text(
            artist.title,
            color = PhoebeUi.primaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().sharedBoundsTransition(sharedKey?.let { "$it:title" }),
        )
    }
}

@Composable
private fun RandomArtistPanel(
    artist: Artist?,
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    onArtist: (Artist) -> Unit,
    onRefresh: () -> Unit,
    onPrefetch: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(artist?.id) {
        artist?.let(onPrefetch)
    }
    HomePanel(modifier) {
        SectionHeader("RANDOM ARTIST", "Refresh", onRefresh)
        if (artist == null) {
            HomeEmptyState("Add music to your library to discover artists here.")
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                FeaturedArtistCard(
                    artist = artist,
                    catalog = catalog,
                    catalogRefreshing = catalogRefreshing,
                    modifier = Modifier.fillMaxWidth(0.92f),
                    onClick = { onArtist(artist) },
                )
            }
        }
    }
}

@Composable
private fun RandomAlbumPanel(
    album: Album?,
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    onAlbum: (Album) -> Unit,
    onRefresh: () -> Unit,
    onPrefetch: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(album?.id) {
        album?.let(onPrefetch)
    }
    HomePanel(modifier) {
        SectionHeader("RANDOM ALBUM", "Refresh", onRefresh)
        if (album == null) {
            HomeEmptyState("Add music to your library to discover albums here.")
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                FeaturedAlbumCard(
                    album = album,
                    catalog = catalog,
                    catalogRefreshing = catalogRefreshing,
                    modifier = Modifier.fillMaxWidth(0.92f),
                    onClick = { onAlbum(album) },
                )
            }
        }
    }
}

@Composable
private fun FeaturedArtistCard(
    artist: Artist,
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val syncState = LocalCatalogSyncState.current
    val albums = remember(catalog, artist.title) { catalogAlbumsForArtist(catalog, artist.title) }
    val artistThumbUrl = remember(artist.thumbUrl, albums) {
        artist.thumbUrl ?: albums.firstNotNullOfOrNull { it.thumbUrl }
    }
    val tracks = remember(catalog, artist.title) { catalogTracksForArtist(catalog, artist.title) }
    val albumsLoading = remember(catalog, artist, syncState) { catalogArtistAlbumCountLoading(catalog, artist, syncState) }
    val trackStatsLoading = remember(catalog, artist, syncState, catalogRefreshing) {
        catalogArtistTrackStatsLoading(catalog, artist, syncState, catalogRefreshing)
    }
    val genre = remember(catalog, artist.title, trackStatsLoading) {
        if (trackStatsLoading) null else catalogArtistGenre(catalog, artist.title)
    }
    val totalDuration = remember(tracks) { tracks.sumOf { it.durationMs } }
    val playHistory = LocalPlayHistory.current
    val nowMs = LocalNowMs.current
    val lastPlayed = remember(tracks, playHistory.byTrack, playHistory.byArtist, artist.title) {
        resolveArtistLastPlayed(artist.title, tracks, playHistory)
    }
    val lastPlayedLabel = remember(lastPlayed, nowMs) { formatLastPlayed(lastPlayed, nowMs) }
    val albumWord = if (albums.size == 1) "album" else "albums"
    val songWord = if (tracks.size == 1) "song" else "songs"

    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            Modifier.width(136.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArtworkImage(
                artist.title,
                artistThumbUrl,
                Modifier
                    .size(112.dp)
                    .sharedArtworkTransition("artist:${artist.id}")
                    .clip(CircleShape),
                radius = 56.dp,
                elevated = false,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    artist.title,
                    color = PhoebeUi.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.sharedBoundsTransition("artist:${artist.id}:title"),
                )
                if (trackStatsLoading) {
                    HomeStatLoadingBar(Modifier.width(96.dp))
                } else if (!genre.isNullOrBlank()) {
                    Text(genre, color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeArtistStat(
                value = "${albums.size} $albumWord",
                label = "Albums",
                icon = PhoebeIcon.Library,
                loading = albumsLoading,
            )
            HomeArtistStat(
                value = "${tracks.size} $songWord",
                label = "Songs",
                icon = PhoebeIcon.Music,
                loading = trackStatsLoading,
            )
            HomeArtistStat(
                value = formatHoursMinutes(totalDuration),
                label = "Total duration",
                icon = PhoebeIcon.ActiveDot,
                loading = trackStatsLoading,
            )
            HomeArtistStat(lastPlayedLabel, "Last played", PhoebeIcon.Bell)
        }
    }
}

@Composable
private fun HomeArtistStat(
    value: String,
    label: String,
    icon: PhoebeIcon,
    loading: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PhoebeUi.elevatedFill),
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(icon, tint = PhoebeUi.accentLight, modifier = Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (loading) {
                HomeStatLoadingBar(Modifier.width(88.dp))
            } else {
                Text(value, color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(label, color = PhoebeUi.mutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HomeStatLoadingBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "home-stat-loading")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "home-stat-loading-alpha",
    )
    Box(
        modifier
            .height(10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PhoebeUi.elevatedFill.copy(alpha = alpha)),
    )
}

@Composable
private fun FeaturedAlbumCard(
    album: Album,
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val syncState = LocalCatalogSyncState.current
    val tracks = remember(catalog, album.id) { catalogTracksForAlbum(catalog, album.id) }
    val trackStatsLoading = remember(catalog, album, syncState, catalogRefreshing) {
        catalogAlbumTrackStatsLoading(catalog, album, syncState, catalogRefreshing)
    }
    val genre = remember(catalog, album.id, trackStatsLoading) {
        if (trackStatsLoading) null else catalogAlbumGenre(catalog, album.id)
    }
    val duration = remember(tracks) { tracks.sumOf { it.durationMs } }
    val songWord = if (tracks.size == 1) "song" else "songs"

    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.width(136.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ArtworkImage(
                album.title,
                album.thumbUrl,
                Modifier.size(112.dp).sharedArtworkTransition("album:${album.id}"),
                radius = 10.dp,
                elevated = false,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    album.title,
                    color = PhoebeUi.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.sharedBoundsTransition("album:${album.id}:title"),
                )
                Text(
                    album.artist,
                    color = PhoebeUi.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.sharedBoundsTransition("album:${album.id}:subtitle"),
                )
                if (trackStatsLoading) {
                    HomeStatLoadingBar(Modifier.width(96.dp))
                } else if (!genre.isNullOrBlank()) {
                    Text(genre, color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            album.year?.let { year ->
                HomeArtistStat(year.toString(), "Release year", PhoebeIcon.Grid)
            }
            HomeArtistStat(
                value = "${tracks.size} $songWord",
                label = "Tracks",
                icon = PhoebeIcon.Music,
                loading = trackStatsLoading,
            )
            HomeArtistStat(
                value = formatHoursMinutes(duration),
                label = "Total duration",
                icon = PhoebeIcon.ActiveDot,
                loading = trackStatsLoading,
            )
        }
    }
}

@Composable
private fun MostPlayedPanel(
    rows: List<HomePlayedTrack>,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
    maxRows: Int = 4,
    sharedKeyForTrack: (Track) -> String? = { track -> "song:${track.id}" },
    showFavoriteAction: Boolean = true,
    rowHeight: Dp = 88.dp,
) {
    HomePanel(modifier) {
        SectionHeader("MOST PLAYED", "View all", onViewAll)
        if (rows.isEmpty()) {
            HomeEmptyState("Your most-played tracks will appear here after you've listened for a while.")
        } else {
            val tracks = rows.map { it.track }
            rows.take(maxRows).forEachIndexed { index, row ->
                HomePlayedTrackRow(
                    track = row.track,
                    playCount = row.playCount,
                    onPlay = { onPlayTracks(tracks, index) },
                    onAddToUpNext = { onAddToUpNext(row.track) },
                    onDownload = { onDownload(row.track) },
                    sharedKey = sharedKeyForTrack(row.track),
                    showFavoriteAction = showFavoriteAction,
                    rowHeight = rowHeight,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, PhoebeUi.mutedText)
        Spacer(Modifier.weight(1f))
        Text(
            action,
            color = PhoebeUi.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun HomePlayedTrackRow(
    track: Track,
    playCount: Long? = null,
    sharedKey: String? = "song:${track.id}",
    onPlay: () -> Unit,
    onAddToUpNext: () -> Unit,
    onDownload: () -> Unit,
    showFavoriteAction: Boolean = true,
    rowHeight: Dp = 88.dp,
) {
    var menuExpanded by remember(track.id) { mutableStateOf(false) }
    val nowPlaying = LocalNowPlaying.current
    val likeActions = LocalLikeActions.current
    val downloads = LocalDownloadStatus.current
    val isNowPlaying = track.id == nowPlaying.trackId
    val canLike = likeActions.likesEnabled && track.canTogglePlexLike()
    val liked = likeActions.isLiked(track)
    val downloaded = downloads.isComplete(track)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onPlay, onLongClick = { menuExpanded = true })
            .background(
                if (isNowPlaying) PhoebeUi.accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.045f),
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val artworkSize = if (rowHeight < 84.dp) 44.dp else 48.dp
        Box(Modifier.size(artworkSize), contentAlignment = Alignment.Center) {
            TrackArtworkImage(
                track,
                Modifier.fillMaxSize().sharedArtworkTransition(sharedKey),
                elevated = false,
            )
            if (isNowPlaying) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    NowPlayingIndicator(
                        isPlaying = nowPlaying.isPlaying,
                        isBuffering = nowPlaying.isBuffering,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track.title,
                color = if (isNowPlaying) PhoebeUi.accentLight else PhoebeUi.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedBoundsTransition(sharedKey?.let { "$it:title" }),
            )
            Text(
                track.artist,
                color = PhoebeUi.secondaryText,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val showInlineLiked = !showFavoriteAction && liked
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    track.album.takeIf { it.isNotBlank() } ?: "Unknown album",
                    color = PhoebeUi.mutedText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TrackStateBadges(
                    liked = showInlineLiked,
                    downloaded = downloaded,
                    iconSize = 10.dp,
                )
            }
        }
        playCount?.let { count ->
            Text(
                formatHomePlayCount(count),
                color = PhoebeUi.secondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
        }
        if (showFavoriteAction) {
            LikeButton(
                liked = liked,
                enabled = canLike,
                onClick = { likeActions.onToggleLiked(track) },
                modifier = Modifier.size(34.dp),
            )
        }
        Box {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { menuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                PhoebeIconView(PhoebeIcon.More, tint = PhoebeUi.secondaryText, modifier = Modifier.size(17.dp))
            }
            TrackActionMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onAddToUpNext = onAddToUpNext,
                onDownload = onDownload,
                track = track,
            )
        }
    }
}

private fun formatHomePlayCount(playCount: Long): String {
    val playWord = if (playCount == 1L) "play" else "plays"
    return "$playCount $playWord"
}

@Composable
private fun HomeArtworkTile(
    title: String,
    subtitle: String,
    thumbUrl: String?,
    fallbackThumbUrl: String? = null,
    modifier: Modifier = Modifier,
    maxDecodeDimension: Int = 256,
    sharedKey: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ArtworkImage(
            title,
            thumbUrl,
            Modifier.fillMaxWidth().aspectRatio(1f).sharedArtworkTransition(sharedKey),
            radius = 7.dp,
            elevated = false,
            maxDecodeDimension = maxDecodeDimension,
            fallbackThumbUrl = fallbackThumbUrl,
        )
        Text(
            title,
            color = PhoebeUi.primaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedBoundsTransition(sharedKey?.let { "$it:title" }),
        )
        Text(
            subtitle,
            color = PhoebeUi.secondaryText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedBoundsTransition(sharedKey?.let { "$it:subtitle" }),
        )
    }
}
