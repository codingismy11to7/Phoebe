package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import phoebe.composeapp.generated.resources.Res
import phoebe.composeapp.generated.resources.phoebe_bird
import phoebe.composeapp.generated.resources.phoebe_icon_rounded
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.phoebe.app.AppState
import com.phoebe.app.data.catalogAlbumsForArtist
import com.phoebe.app.data.catalogTracksForArtist
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.AppScreen
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isPlexLibraryTrack
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.platform.createPlatformHttpClient
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.platform.prefersReducedArtworkEffects
import kotlinx.coroutines.delay
import com.phoebe.app.sources.rememberPickLocalFolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlin.math.max

internal data class SearchUiResults(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val topArtist: Artist?,
    val topAlbum: Album?,
    val topTrack: Track?,
)

internal enum class SearchResultScope {
    Overview,
    Songs,
    Albums,
    Artists,
}

@Composable
internal fun rememberSearchUiResults(catalog: CatalogSnapshot, searchQuery: String): SearchUiResults {
    val query = searchQuery.trim()
    val allTracks = remember(catalog.tracksByParent) {
        catalog.tracksByParent.values.asSequence().flatten().distinctBy { it.id }.toList()
    }
    return remember(catalog.albums, catalog.artists, allTracks, query) {
        if (query.isBlank()) {
            return@remember SearchUiResults(
                tracks = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                topArtist = null,
                topAlbum = null,
                topTrack = null,
            )
        }
        val tracks = filterTracksByQuery(allTracks, query)
        val albums = filterAlbumsByQuery(catalog.albums, query)
        val artists = filterArtistsByQuery(catalog.artists, query)
        SearchUiResults(
            tracks = tracks,
            albums = albums,
            artists = artists,
            topArtist = bestSearchMatch(artists, query) { it.title },
            topAlbum = bestSearchMatch(albums, query) { it.title },
            topTrack = bestSearchMatch(tracks, query) { it.title },
        )
    }
}

internal fun <T> bestSearchMatch(items: List<T>, query: String, label: (T) -> String): T? {
    if (query.isBlank()) return null
    return items.minByOrNull { item ->
        val text = label(item).trim()
        when {
            text.equals(query, ignoreCase = true) -> 0
            text.startsWith(query, ignoreCase = true) -> 1
            text.contains(query, ignoreCase = true) -> 2
            else -> 3
        }
    }
}

@Composable
internal fun SearchDesktopView(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier,
    onSearchQuery: (String) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val results = rememberSearchUiResults(catalog, searchQuery)
    val searchHistory = LocalSearchHistory.current
    val hasQuery = searchQuery.isNotBlank()
    var resultScope by remember { mutableStateOf(SearchResultScope.Overview) }
    LaunchedEffect(searchQuery) {
        resultScope = SearchResultScope.Overview
    }
    Row(
        modifier = modifier
            .padding(start = 36.dp, end = 28.dp, top = 32.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Search", color = PhoebeUi.primaryText, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("Find your favorite music", color = PhoebeUi.mutedText, fontSize = 13.sp)
                }
                SearchPill(searchQuery, onSearchQuery, Modifier.width(380.dp))
                Spacer(Modifier.width(12.dp))
                GlassIcon(PhoebeIcon.Bell, "Notifications")
            }
            if (catalogRefreshing) {
                CatalogLoadingStrip()
            }
            if (hasQuery && resultScope != SearchResultScope.Overview) {
                SearchAllResultsPanel(
                    scope = resultScope,
                    results = results,
                    catalog = catalog,
                    compact = false,
                    onBack = { resultScope = SearchResultScope.Overview },
                    onArtist = onArtist,
                    onAlbum = onAlbum,
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                )
            } else {
                SearchTopResultSection(
                    results = results,
                    catalog = catalog,
                    onAlbum = onAlbum,
                    onArtist = onArtist,
                    onPlayTracks = onPlayTracks,
                )
            }
            if (hasQuery && resultScope == SearchResultScope.Overview) {
                SearchSongsSection(
                    tracks = results.tracks.take(5),
                    allTracks = results.tracks,
                    compact = false,
                    onSeeAll = if (results.tracks.size > 5) {
                        { resultScope = SearchResultScope.Songs }
                    } else {
                        null
                    },
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SearchAlbumsSection(
                        albums = results.albums.take(5),
                        catalog = catalog,
                        onAlbum = onAlbum,
                        modifier = Modifier.weight(1.15f),
                        compact = false,
                        onSeeAll = if (results.albums.size > 5) {
                            { resultScope = SearchResultScope.Albums }
                        } else {
                            null
                        },
                    )
                    SearchArtistsSection(
                        artists = results.artists.take(3),
                        catalog = catalog,
                        onArtist = onArtist,
                        modifier = Modifier.weight(0.85f),
                        compact = false,
                        onSeeAll = if (results.artists.size > 3) {
                            { resultScope = SearchResultScope.Artists }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        SearchRecentPanel(
            searches = searchHistory.recentSearches,
            onSearch = { search ->
                searchHistory.commitSearch(search)
                onSearchQuery(search)
            },
            onRemoveSearch = searchHistory.removeSearch,
            onClearSearches = searchHistory.clearSearches,
            modifier = Modifier.width(250.dp).padding(top = 78.dp),
        )
    }
}

@Composable
internal fun SearchMobileView(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    searchQuery: String,
    modifier: Modifier = Modifier,
    onSearchQuery: (String) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val results = rememberSearchUiResults(catalog, searchQuery)
    val hasQuery = searchQuery.isNotBlank()
    var resultScope by remember { mutableStateOf(SearchResultScope.Overview) }
    LaunchedEffect(searchQuery) {
        resultScope = SearchResultScope.Overview
    }
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        item(contentType = "search-field") {
            SearchPill(searchQuery, onSearchQuery, Modifier.fillMaxWidth())
        }
        if (catalogRefreshing) {
            item(contentType = "loading") { CatalogLoadingStrip() }
        }
        if (hasQuery && resultScope != SearchResultScope.Overview) {
            item(contentType = "all-results") {
                SearchAllResultsPanel(
                    scope = resultScope,
                    results = results,
                    catalog = catalog,
                    compact = true,
                    onBack = { resultScope = SearchResultScope.Overview },
                    onArtist = onArtist,
                    onAlbum = onAlbum,
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                )
            }
        } else {
            item(contentType = "top-result") {
                SearchTopResultSection(
                    results = results,
                    catalog = catalog,
                    onAlbum = onAlbum,
                    onArtist = onArtist,
                    onPlayTracks = onPlayTracks,
                    compact = true,
                )
            }
        }
        if (hasQuery && resultScope == SearchResultScope.Overview) {
            item(contentType = "songs") {
                SearchSongsSection(
                    tracks = results.tracks.take(5),
                    allTracks = results.tracks,
                    compact = true,
                    onSeeAll = if (results.tracks.size > 5) {
                        { resultScope = SearchResultScope.Songs }
                    } else {
                        null
                    },
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                )
            }
            item(contentType = "albums") {
                SearchAlbumsSection(
                    albums = results.albums.take(6),
                    catalog = catalog,
                    onAlbum = onAlbum,
                    compact = true,
                    onSeeAll = if (results.albums.size > 6) {
                        { resultScope = SearchResultScope.Albums }
                    } else {
                        null
                    },
                )
            }
            item(contentType = "artists") {
                SearchArtistsSection(
                    artists = results.artists.take(4),
                    catalog = catalog,
                    onArtist = onArtist,
                    compact = true,
                    onSeeAll = if (results.artists.size > 4) {
                        { resultScope = SearchResultScope.Artists }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
internal fun SearchTopResultSection(
    results: SearchUiResults,
    catalog: CatalogSnapshot,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Top Result", PhoebeUi.primaryText)
        when {
            results.topArtist != null -> SearchTopArtistCard(
                artist = results.topArtist,
                catalog = catalog,
                onArtist = onArtist,
                compact = compact,
            )
            results.topAlbum != null -> SearchTopAlbumCard(
                album = results.topAlbum,
                tracks = catalogTracksForAlbum(catalog, results.topAlbum),
                onAlbum = onAlbum,
                onPlayTracks = onPlayTracks,
                compact = compact,
            )
            results.topTrack != null -> SearchTopTrackCard(results.topTrack, results.tracks, onPlayTracks, compact)
            else -> SearchEmptyCard("Start typing to search songs, albums, and artists.")
        }
    }
}

@Composable
internal fun SearchTopAlbumCard(
    album: Album,
    tracks: List<Track>,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp))
            .clickable { onAlbum(album) }
            .padding(if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
    ) {
        ArtworkImage(album.title, album.thumbUrl, Modifier.size(if (compact) 76.dp else 170.dp), radius = 10.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
            Text(album.title, color = PhoebeUi.primaryText, fontSize = if (compact) 14.sp else 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(album.artist, color = PhoebeUi.secondaryText, fontSize = if (compact) 11.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Album • ${album.year ?: "Unknown year"}", color = PhoebeUi.mutedText, fontSize = if (compact) 11.sp else 12.sp)
            if (!compact) {
                SearchPlayChip(enabled = tracks.isNotEmpty()) {
                    if (tracks.isNotEmpty()) onPlayTracks(tracks, 0)
                }
            }
        }
        if (compact) {
            SearchRoundPlayButton(enabled = tracks.isNotEmpty()) {
                if (tracks.isNotEmpty()) onPlayTracks(tracks, 0)
            }
            PhoebeIconView(PhoebeIcon.More, tint = PhoebeUi.mutedText, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun SearchTopTrackCard(
    track: Track,
    tracks: List<Track>,
    onPlayTracks: (List<Track>, Int) -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp))
            .clickable { onPlayTracks(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)) }
            .padding(if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
    ) {
        ArtworkImage(track.album, track.thumbUrl, Modifier.size(if (compact) 76.dp else 170.dp), radius = 10.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
            Text(track.title, color = PhoebeUi.primaryText, fontSize = if (compact) 14.sp else 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = PhoebeUi.secondaryText, fontSize = if (compact) 11.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Song • ${formatDuration(track.durationMs)}", color = PhoebeUi.mutedText, fontSize = if (compact) 11.sp else 12.sp)
            if (!compact) {
                SearchPlayChip(enabled = true) { onPlayTracks(tracks, 0) }
            }
        }
        if (compact) {
            SearchRoundPlayButton(enabled = true) { onPlayTracks(tracks, 0) }
            PhoebeIconView(PhoebeIcon.More, tint = PhoebeUi.mutedText, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun SearchTopArtistCard(
    artist: Artist,
    catalog: CatalogSnapshot,
    onArtist: (Artist) -> Unit,
    compact: Boolean,
) {
    val songCount = catalogTrackCountForArtist(catalog, artist)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp))
            .clickable { onArtist(artist) }
            .padding(if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
    ) {
        ArtworkImage(artist.title, artist.thumbUrl, Modifier.size(if (compact) 76.dp else 148.dp), radius = 999.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(artist.title, color = PhoebeUi.primaryText, fontSize = if (compact) 14.sp else 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${artistAlbumCountSubtitle(artist)} • ${songCountLabel(songCount)}", color = PhoebeUi.mutedText, fontSize = if (compact) 11.sp else 12.sp)
        }
    }
}

@Composable
internal fun SearchAllResultsPanel(
    scope: SearchResultScope,
    results: SearchUiResults,
    catalog: CatalogSnapshot,
    compact: Boolean,
    onBack: () -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val (title, count) = when (scope) {
        SearchResultScope.Songs -> "Songs" to results.tracks.size
        SearchResultScope.Albums -> "Albums" to results.albums.size
        SearchResultScope.Artists -> "Artists" to results.artists.size
        SearchResultScope.Overview -> "Results" to 0
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchAllResultsHeader(title = title, count = count, onBack = onBack)
        when (scope) {
            SearchResultScope.Songs -> SearchSongsSection(
                tracks = results.tracks,
                allTracks = results.tracks,
                compact = compact,
                onPlayTracks = onPlayTracks,
                onAddToUpNext = onAddToUpNext,
                onDownload = onDownload,
            )
            SearchResultScope.Albums -> SearchAllAlbumsSection(
                albums = results.albums,
                catalog = catalog,
                compact = compact,
                onAlbum = onAlbum,
            )
            SearchResultScope.Artists -> SearchAllArtistsSection(
                artists = results.artists,
                catalog = catalog,
                onArtist = onArtist,
            )
            SearchResultScope.Overview -> Unit
        }
    }
}

@Composable
internal fun SearchAllResultsHeader(title: String, count: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Back to search overview" },
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(PhoebeIcon.Back, tint = PhoebeUi.primaryText, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            SectionLabel(title, PhoebeUi.primaryText)
            Text("$count results", color = PhoebeUi.mutedText, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun SearchSongsSection(
    tracks: List<Track>,
    allTracks: List<Track>,
    compact: Boolean,
    onSeeAll: (() -> Unit)? = null,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchSectionHeader("Songs", showSeeAll = onSeeAll != null, onSeeAll = onSeeAll)
        if (tracks.isEmpty()) {
            SearchEmptyCard("No songs found.")
        } else {
            if (!compact) {
                SearchSongsHeader()
            }
            tracks.forEachIndexed { index, track ->
                SearchSongResultRow(
                    track = track,
                    index = index,
                    tracks = allTracks,
                    compact = compact,
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                )
            }
        }
    }
}

@Composable
internal fun SearchSongsHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#", color = PhoebeUi.mutedText, fontSize = 10.sp, modifier = Modifier.width(24.dp))
        Text("Title", color = PhoebeUi.mutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.25f))
        Text("Artist", color = PhoebeUi.mutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.95f))
        Text("Album", color = PhoebeUi.mutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.95f))
        Text("Duration", color = PhoebeUi.mutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp), textAlign = TextAlign.End)
        Spacer(Modifier.width(36.dp))
    }
}

@Composable
internal fun SearchSongResultRow(
    track: Track,
    index: Int,
    tracks: List<Track>,
    compact: Boolean,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val trackIndex = tracks.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: index
    if (compact) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .openContextMenuOnSecondaryClick { menuExpanded = true }
                .combinedClickable(onClick = { onPlayTracks(tracks, trackIndex) }, onLongClick = { menuExpanded = true })
                .background(if (index == 0) PhoebeUi.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.035f))
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArtworkImage(track.album, track.thumbUrl, Modifier.size(38.dp), radius = 7.dp)
            Column(Modifier.weight(1f)) {
                Text(track.title, color = if (index == 0) PhoebeUi.accentLight else PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = PhoebeUi.secondaryText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatDuration(track.durationMs), color = PhoebeUi.mutedText, fontSize = 10.sp)
            SearchOverflowMenu(track, expanded = menuExpanded, onExpandedChange = { menuExpanded = it }, onAddToUpNext = onAddToUpNext, onDownload = onDownload)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .openContextMenuOnSecondaryClick { menuExpanded = true }
                .combinedClickable(onClick = { onPlayTracks(tracks, trackIndex) }, onLongClick = { menuExpanded = true })
                .background(if (index == 0) PhoebeUi.accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.032f))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text((index + 1).toString(), color = PhoebeUi.mutedText, fontSize = 11.sp, modifier = Modifier.width(18.dp))
            ArtworkImage(track.album, track.thumbUrl, Modifier.size(24.dp), radius = 5.dp)
            Text(track.title, color = if (index == 0) PhoebeUi.accentLight else PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.25f))
            Text(track.artist, color = PhoebeUi.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.95f))
            Text(track.album, color = PhoebeUi.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.95f))
            Text(formatDuration(track.durationMs), color = PhoebeUi.mutedText, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(66.dp))
            SearchOverflowMenu(track, expanded = menuExpanded, onExpandedChange = { menuExpanded = it }, onAddToUpNext = onAddToUpNext, onDownload = onDownload)
        }
    }
}

@Composable
internal fun SearchAlbumsSection(
    albums: List<Album>,
    catalog: CatalogSnapshot,
    onAlbum: (Album) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean,
    onSeeAll: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchSectionHeader("Albums", showSeeAll = onSeeAll != null, onSeeAll = onSeeAll)
        if (albums.isEmpty()) {
            SearchEmptyCard("No albums found.")
        } else if (compact) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(albums, key = { it.id }, contentType = { "search-album" }) { album ->
                    SearchAlbumTile(album, catalogTracksForAlbum(catalog, album).size, onAlbum, Modifier.width(104.dp), compact = true)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                albums.forEach { album ->
                    SearchAlbumTile(album, catalogTracksForAlbum(catalog, album).size, onAlbum, Modifier.weight(1f), compact = false)
                }
            }
        }
    }
}

@Composable
internal fun SearchAlbumTile(
    album: Album,
    trackCount: Int,
    onAlbum: (Album) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onAlbum(album) }
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ArtworkImage(album.title, album.thumbUrl, Modifier.fillMaxWidth().aspectRatio(1f), radius = 10.dp)
        Text(album.title, color = PhoebeUi.primaryText, fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, color = PhoebeUi.mutedText, fontSize = if (compact) 9.sp else 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!compact) {
            Text("${album.year ?: "Album"} • $trackCount tracks", color = PhoebeUi.mutedText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SearchAllAlbumsSection(
    albums: List<Album>,
    catalog: CatalogSnapshot,
    compact: Boolean,
    onAlbum: (Album) -> Unit,
) {
    if (albums.isEmpty()) {
        SearchEmptyCard("No albums found.")
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minCardWidth = if (compact) 132.dp else 148.dp
        val gap = 12.dp
        val columns = ((maxWidth + gap) / (minCardWidth + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            albums.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { album ->
                        SearchAlbumTile(
                            album = album,
                            trackCount = catalogTracksForAlbum(catalog, album).size,
                            onAlbum = onAlbum,
                            modifier = Modifier.weight(1f),
                            compact = compact,
                        )
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
internal fun SearchArtistsSection(
    artists: List<Artist>,
    catalog: CatalogSnapshot,
    onArtist: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean,
    onSeeAll: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchSectionHeader("Artists", showSeeAll = onSeeAll != null, onSeeAll = onSeeAll)
        if (artists.isEmpty()) {
            SearchEmptyCard("No artists found.")
        } else {
            if (compact) {
                artists.forEach { SearchArtistRow(it, catalog, onArtist, compact = true) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    artists.forEach { artist ->
                        SearchArtistTile(artist, catalog, onArtist, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchAllArtistsSection(
    artists: List<Artist>,
    catalog: CatalogSnapshot,
    onArtist: (Artist) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (artists.isEmpty()) {
            SearchEmptyCard("No artists found.")
        } else {
            artists.forEach { artist ->
                SearchArtistRow(artist, catalog, onArtist, compact = true)
            }
        }
    }
}

@Composable
internal fun SearchArtistTile(artist: Artist, catalog: CatalogSnapshot, onArtist: (Artist) -> Unit, modifier: Modifier = Modifier) {
    val songCount = catalogTrackCountForArtist(catalog, artist)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onArtist(artist) }
            .background(Color.White.copy(alpha = 0.035f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArtworkImage(artist.title, artist.thumbUrl, Modifier.size(74.dp), radius = 999.dp)
        Text(artist.title, color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(songCountLabel(songCount), color = PhoebeUi.mutedText, fontSize = 10.sp)
    }
}

@Composable
internal fun SearchArtistRow(artist: Artist, catalog: CatalogSnapshot, onArtist: (Artist) -> Unit, compact: Boolean) {
    val songCount = catalogTrackCountForArtist(catalog, artist)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onArtist(artist) }
            .background(Color.White.copy(alpha = 0.035f))
            .padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArtworkImage(artist.title, artist.thumbUrl, Modifier.size(42.dp), radius = 999.dp)
        Column(Modifier.weight(1f)) {
            Text(artist.title, color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${artistAlbumCountSubtitle(artist)} • ${songCountLabel(songCount)}", color = PhoebeUi.mutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.mutedText, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun SearchRecentPanel(
    searches: List<String>,
    onSearch: (String) -> Unit,
    onRemoveSearch: (String) -> Unit,
    onClearSearches: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionLabel("Recent Searches", PhoebeUi.primaryText)
        if (searches.isEmpty()) {
            Text("Searches will appear here.", color = PhoebeUi.mutedText, fontSize = 12.sp, lineHeight = 17.sp)
        } else {
            searches.forEach { search ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSearch(search) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    PhoebeIconView(PhoebeIcon.Search, tint = PhoebeUi.mutedText, modifier = Modifier.size(14.dp))
                    Text(search, color = PhoebeUi.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onRemoveSearch(search) },
                        contentAlignment = Alignment.Center,
                    ) {
                        PhoebeIconView(PhoebeIcon.Close, tint = PhoebeUi.mutedText.copy(alpha = 0.68f), modifier = Modifier.size(13.dp))
                    }
                }
            }
            Text(
                "Clear Recent",
                color = PhoebeUi.mutedText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.em,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClearSearches)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun SearchSectionHeader(label: String, showSeeAll: Boolean, onSeeAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(label, PhoebeUi.primaryText)
        Spacer(Modifier.weight(1f))
        if (showSeeAll) {
            Text(
                "See all",
                color = PhoebeUi.mutedText,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = onSeeAll != null) { onSeeAll?.invoke() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun SearchPlayChip(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Brush.linearGradient(listOf(PhoebeUi.accentLight, PhoebeUi.accent)) else Brush.linearGradient(listOf(PhoebeUi.mutedText.copy(alpha = 0.28f), PhoebeUi.mutedText.copy(alpha = 0.22f))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PhoebeIconView(PhoebeIcon.Play, tint = PhoebeUi.primaryText, modifier = Modifier.size(13.dp))
        Text("Play", color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SearchRoundPlayButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) Brush.linearGradient(listOf(PhoebeUi.accentLight, PhoebeUi.accent)) else Brush.linearGradient(listOf(PhoebeUi.mutedText.copy(alpha = 0.24f), PhoebeUi.mutedText.copy(alpha = 0.18f))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(PhoebeIcon.Play, tint = PhoebeUi.primaryText, modifier = Modifier.size(15.dp))
    }
}

@Composable
internal fun SearchEmptyCard(message: String) {
    Text(
        message,
        color = PhoebeUi.mutedText,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .padding(14.dp),
    )
}

@Composable
internal fun SearchOverflowMenu(
    track: Track,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    Box {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onExpandedChange(true) },
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(PhoebeIcon.More, tint = PhoebeUi.mutedText, modifier = Modifier.size(17.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("Add to Up Next") },
                onClick = {
                    onAddToUpNext(track)
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = { Text("Download") },
                onClick = {
                    onDownload(track)
                    onExpandedChange(false)
                },
            )
        }
    }
}

internal fun catalogTracksForAlbum(catalog: CatalogSnapshot, album: Album): List<Track> {
    val direct = catalog.tracksByParent[album.id].orEmpty()
    if (direct.isNotEmpty()) return direct
    return catalog.tracksByParent.values
        .asSequence()
        .flatten()
        .filter { it.album.equals(album.title, ignoreCase = true) && it.artist.equals(album.artist, ignoreCase = true) }
        .distinctBy { it.id }
        .toList()
}

internal fun catalogTrackCountForArtist(catalog: CatalogSnapshot, artist: Artist): Int =
    catalog.tracksByParent.values
        .asSequence()
        .flatten()
        .filter { it.artist.equals(artist.title, ignoreCase = true) }
        .distinctBy { it.id }
        .count()
        .takeIf { it > 0 }
        ?: artist.songCount

internal fun songCountLabel(count: Int): String {
    val word = if (count == 1) "song" else "songs"
    return "$count $word"
}
