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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.domain.isPlexLibraryTrack
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.playlists.PlaylistExportFormat
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

@Composable
internal fun DetailBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(PhoebeIcon.Back, tint = PhoebeUi.primaryText, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun DetailSectionIntro(
    onBack: () -> Unit,
    label: String,
    labelColor: Color = PhoebeUi.accentLight,
    alignBackIconToContentStart: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailBackButton(
            onBack = onBack,
            modifier = if (alignBackIconToContentStart) Modifier.offset(x = (-10).dp) else Modifier,
            enabled = enabled,
        )
        SectionLabel(label, labelColor)
    }
}

@Composable
internal fun SongDetailPanel(
    track: Track,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val nowMs = LocalNowMs.current
    val playHistory = LocalPlayHistory.current
    val lastPlayed = playHistory.byTrack[track.id]
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 520.dp
        val horizontalPadding = if (compact) 20.dp else 28.dp
        val bottomContentPadding = if (compact) 88.dp else 0.dp
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 24.dp),
            contentPadding = PaddingValues(bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item("intro") {
                DetailSectionIntro(
                    onBack = onBack,
                    label = "Song",
                    alignBackIconToContentStart = compact,
                )
            }
            item("hero") {
                SongDetailHero(
                    track = track,
                    compact = compact,
                    onPlay = onPlay,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                )
            }
            item("metadata") {
                HomePanelLike { SongDetailMetadataRows(track, nowMs, lastPlayed, playHistory.playCountByTrack[track.id] ?: 0L) }
            }
        }
    }
}

@Composable
private fun SongDetailHero(
    track: Track,
    compact: Boolean,
    onPlay: () -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    if (compact) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FlippableSongArtwork(
                track = track,
                Modifier
                    .size(220.dp)
                    .align(Alignment.CenterHorizontally),
                artworkModifier = Modifier.sharedArtworkTransition("song:${track.id}"),
                radius = 14.dp,
            )
            SongDetailText(track, titleSize = 28.sp, titleLineHeight = 32.sp, titleMaxLines = 3, autoScroll = true)
            SongActionRow(
                track = track,
                scrollable = true,
                onPlay = onPlay,
                onAddToUpNext = onAddToUpNext,
                onDownload = onDownload,
            )
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FlippableSongArtwork(
                track = track,
                Modifier
                    .size(180.dp)
                    .sharedArtworkTransition("song:${track.id}"),
                radius = 14.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SongDetailText(track, titleSize = 30.sp, titleLineHeight = 34.sp, titleMaxLines = 2)
                SongActionRow(
                    track = track,
                    scrollable = false,
                    onPlay = onPlay,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                )
            }
        }
    }
}

@Composable
private fun SongDetailText(
    track: Track,
    titleSize: TextUnit,
    titleLineHeight: TextUnit,
    titleMaxLines: Int,
    autoScroll: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (autoScroll) {
            AutoScrollingText(
                track.title,
                color = PhoebeUi.primaryText,
                fontSize = titleSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.Black,
                modifier = Modifier.sharedBoundsTransition("song:${track.id}:title"),
            )
            AutoScrollingText(track.artist, color = PhoebeUi.secondaryText, fontSize = 17.sp)
            AutoScrollingText(track.album, color = PhoebeUi.mutedText, fontSize = 14.sp)
        } else {
            Text(
                track.title,
                color = PhoebeUi.primaryText,
                fontSize = titleSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.Black,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedBoundsTransition("song:${track.id}:title"),
            )
            Text(track.artist, color = PhoebeUi.secondaryText, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.album, color = PhoebeUi.mutedText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FlippableSongArtwork(
    track: Track,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    radius: Dp = 10.dp,
) {
    var showingDetails by remember(track.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (showingDetails) 180f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "songArtworkFlip",
    )
    val shape = RoundedCornerShape(radius)
    val density = LocalDensity.current

    Box(
        modifier
            .aspectRatio(1f)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density.density
            }
            .clip(shape),
    ) {
        if (rotation <= 90f) {
            ArtworkImage(
                track.album,
                track.thumbUrl,
                Modifier
                    .fillMaxSize()
                    .then(artworkModifier)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showingDetails = true },
                    ),
                radius = radius,
            )
        } else {
            SongArtworkDetailBack(
                track = track,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .clickable { showingDetails = false },
                shape = shape,
            )
        }
    }
}

@Composable
private fun SongArtworkDetailBack(
    track: Track,
    modifier: Modifier,
    shape: RoundedCornerShape,
) {
    val nowMs = LocalNowMs.current
    val playHistory = LocalPlayHistory.current
    val lastPlayed = playHistory.byTrack[track.id]

    Column(
        modifier
            .clip(shape)
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), shape)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            track.title,
            color = PhoebeUi.primaryText,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SongDetailMetadataRows(
            track = track,
            nowMs = nowMs,
            lastPlayed = lastPlayed,
            playCount = playHistory.playCountByTrack[track.id] ?: 0L,
            labelWidth = 72.dp,
            labelFontSize = 10.sp,
            valueFontSize = 11.sp,
        )
        Text("Tap to show artwork", color = PhoebeUi.mutedText, fontSize = 11.sp)
    }
}

@Composable
private fun SongActionRow(
    track: Track,
    scrollable: Boolean,
    onPlay: () -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    if (scrollable) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            item("play") { SongActionButton(PhoebeIcon.Play, "Play", onPlay) }
            item("up-next") { SongActionButton(PhoebeIcon.Queue, "Up Next") { onAddToUpNext(track) } }
            item("download") { SongActionButton(PhoebeIcon.Cast, "Download") { onDownload(track) } }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongActionButton(PhoebeIcon.Play, "Play", onPlay)
            SongActionButton(PhoebeIcon.Queue, "Up Next") { onAddToUpNext(track) }
            SongActionButton(PhoebeIcon.Cast, "Download") { onDownload(track) }
        }
    }
}

@Composable
private fun SongActionButton(icon: PhoebeIcon, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PhoebeIconView(icon, tint = PhoebeUi.accentLight, modifier = Modifier.size(15.dp))
        Text(label, color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomePanelLike(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SongDetailMetadataRows(
    track: Track,
    nowMs: Long,
    lastPlayed: Long?,
    playCount: Long,
    labelWidth: Dp = 108.dp,
    labelFontSize: TextUnit = 12.sp,
    valueFontSize: TextUnit = 13.sp,
) {
    DetailMetaRow("Artist", track.artist, labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Album", track.album, labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Duration", formatDuration(track.durationMs), labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Year", track.year?.toString() ?: "Unknown", labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Genre", track.genre ?: "Unknown", labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Date Added", track.dateAddedMs?.let { formatLastPlayed(it, nowMs) } ?: "Unknown", labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Last Played", lastPlayed?.let { formatLastPlayed(it, nowMs) } ?: "Never", labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Plays", playCount.toString(), labelWidth, labelFontSize, valueFontSize)
    track.audioCodec?.let { DetailMetaRow("Codec", it.uppercase(), labelWidth, labelFontSize, valueFontSize) }
    track.bitrateKbps?.let { DetailMetaRow("Bitrate", "$it kbps", labelWidth, labelFontSize, valueFontSize) }
    track.filepath?.let { DetailMetaRow("File", it, labelWidth, labelFontSize, valueFontSize) }
}

@Composable
private fun DetailMetaRow(
    label: String,
    value: String,
    labelWidth: Dp = 108.dp,
    labelFontSize: TextUnit = 12.sp,
    valueFontSize: TextUnit = 13.sp,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PhoebeUi.mutedText, fontSize = labelFontSize, modifier = Modifier.width(labelWidth))
        Text(value, color = PhoebeUi.primaryText, fontSize = valueFontSize, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistDetailPanel(
    artist: Artist,
    catalog: CatalogSnapshot,
    libraryUi: LibraryUiPreferences,
    catalogRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onBack: () -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
) {
    val albums = remember(catalog.albums, artist.title) { catalogAlbumsForArtist(catalog, artist.title) }
    val artistThumbUrl = remember(artist.thumbUrl, albums) {
        artist.thumbUrl ?: albums.firstNotNullOfOrNull { it.thumbUrl }
    }
    val tracks = remember(catalog.tracksByParent, artist.title) { catalogTracksForArtist(catalog, artist.title) }
    val albumWord = if (albums.size == 1) "album" else "albums"
    val songWord = if (tracks.size == 1) "song" else "songs"

    var albumSortBy by remember(artist.id) { mutableStateOf(LibrarySortBy.Name) }
    var albumAscending by remember(artist.id) { mutableStateOf(true) }
    var albumViewMode by remember(artist.id) { mutableStateOf(LibraryViewMode.List) }

    var songSortBy by remember(artist.id) { mutableStateOf(LibrarySortBy.Album) }
    var songAscending by remember(artist.id) { mutableStateOf(true) }

    val sortedAlbums = remember(albums, albumSortBy, albumAscending) {
        sortAlbumsForLibrary(albums, albumSortBy, albumAscending)
    }
    val sortedTracks = remember(tracks, songSortBy, songAscending) {
        sortTracksForLibrary(tracks, songSortBy, songAscending)
    }
    val visibleAlbums = remember(sortedAlbums, searchQuery) {
        filterAlbumsByQuery(sortedAlbums, searchQuery)
    }
    val visibleTracks = remember(sortedTracks, searchQuery) {
        filterTracksByQuery(sortedTracks, searchQuery)
    }
    val nowPlaying = LocalNowPlaying.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val useTable = maxWidth >= 640.dp
        val edgePadding = if (maxWidth < 640.dp) 20.dp else 36.dp
        val topPadding = if (maxWidth < 640.dp) 16.dp else 36.dp
        val albumGridColumns = remember(maxWidth) {
            val minCardWidth = 160.dp
            val gap = 14.dp
            ((maxWidth + gap) / (minCardWidth + gap)).toInt().coerceAtLeast(1)
        }
        val albumGridRows = remember(visibleAlbums, albumGridColumns) {
            visibleAlbums.chunked(albumGridColumns)
        }
        val listState = RetainedLazyListStates.remember("artist-detail:${artist.id}")
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(start = edgePadding, end = edgePadding, top = topPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(contentType = "artist-header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailSectionIntro(
                    onBack = onBack,
                    label = "Artist",
                    alignBackIconToContentStart = !useTable,
                )
                Text(
                    artist.title,
                    color = PhoebeUi.primaryText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedBoundsTransition("artist:${artist.id}:title"),
                )
                Text("${albums.size} $albumWord · ${tracks.size} $songWord", color = PhoebeUi.secondaryText, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                ArtworkImage(
                    artist.title,
                    artistThumbUrl,
                    Modifier.size(120.dp).sharedArtworkTransition("artist:${artist.id}"),
                    elevated = useTable,
                )
                Spacer(Modifier.height(10.dp))
                SectionLabel("Albums", PhoebeUi.primaryText)
            }
        }
        item(contentType = "artist-album-toolbar") {
            DetailSectionToolbar(
                sortBy = albumSortBy,
                sortKeys = listOf(LibrarySortBy.Name, LibrarySortBy.Year),
                sortLabel = { key ->
                    when (key) {
                        LibrarySortBy.Year -> "Release date"
                        else -> "Album name"
                    }
                },
                onSortBy = { albumSortBy = it },
                ascending = albumAscending,
                onAscending = { albumAscending = it },
                viewMode = albumViewMode,
                onViewMode = { albumViewMode = it },
            )
        }
        if (albumViewMode == LibraryViewMode.Grid) {
            items(
                albumGridRows.size,
                key = { rowIndex -> "album-grid-row:${albumGridRows[rowIndex].first().id}" },
                contentType = { "artist-album-grid-row" },
            ) { rowIndex ->
                val row = albumGridRows[rowIndex]
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    row.forEach { album ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAlbum(album) }
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                ArtworkImage(
                                    album.title,
                                    album.thumbUrl,
                                    Modifier.fillMaxSize().sharedArtworkTransition("album:${album.id}"),
                                    elevated = useTable,
                                )
                            }
                            Text(
                                album.title,
                                color = PhoebeUi.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.sharedBoundsTransition("album:${album.id}:title"),
                            )
                            Text(
                                album.year?.toString() ?: "Album",
                                color = PhoebeUi.mutedText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(albumGridColumns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            items(visibleAlbums, key = { it.id }, contentType = { "artist-album" }) { album ->
                LibraryRow(
                    title = album.title,
                    subtitle = "${album.artist} • ${album.year ?: "Album"}",
                    seed = album.title,
                    thumbUrl = album.thumbUrl,
                    elevatedArtwork = useTable,
                    sharedKey = "album:${album.id}",
                    onClick = { onAlbum(album) },
                )
            }
        }
        item(contentType = "artist-songs-label") {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Songs", PhoebeUi.primaryText)
        }
        item(contentType = "artist-song-toolbar") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailSectionToolbar(
                    sortBy = songSortBy,
                    sortKeys = listOf(LibrarySortBy.Name, LibrarySortBy.Album, LibrarySortBy.Year),
                    sortLabel = { key ->
                        when (key) {
                            LibrarySortBy.Album -> "Album name"
                            LibrarySortBy.Year -> "Release date"
                            else -> "Song name"
                        }
                    },
                    onSortBy = { songSortBy = it },
                    ascending = songAscending,
                    onAscending = { songAscending = it },
                    columns = libraryUi.columns,
                    onColumns = onLibraryColumns,
                )
                if (catalogRefreshing && searchQuery.isBlank()) {
                    CatalogLoadingStrip()
                }
            }
        }
        if (visibleTracks.isEmpty() && searchQuery.isBlank()) {
            item(contentType = "artist-song-empty") {
                Text(
                    if (catalogRefreshing) "Fetching songs…" else "No songs loaded yet.",
                    color = PhoebeUi.mutedText,
                    fontSize = 14.sp,
                )
            }
        } else if (visibleTracks.isEmpty() && searchQuery.isNotBlank()) {
            item(contentType = "artist-song-empty") {
                Text("No songs by ${artist.title} match \"$searchQuery\".", color = PhoebeUi.mutedText, fontSize = 14.sp)
            }
        } else if (useTable) {
            item(contentType = "artist-song-header") {
                SongsTableHeader(libraryUi.columns)
            }
            itemsIndexed(visibleTracks, key = { _, t -> t.id }, contentType = { _, _ -> "artist-song" }) { index, track ->
                SongRow(
                    track = track,
                    selected = false,
                    columns = libraryUi.columns,
                    onSelect = { onPlayTracks(visibleTracks, index) },
                    onPlay = { onPlayTracks(visibleTracks, index) },
                    onAddToUpNext = { onAddToUpNext(track) },
                    onDownload = { onDownload(track) },
                )
            }
        } else {
            itemsIndexed(visibleTracks, key = { _, t -> t.id }, contentType = { _, _ -> "artist-song" }) { index, track ->
                val isNowPlaying = track.id == nowPlaying.trackId
                ContentTrackRow(
                    track = track,
                    libraryColumns = libraryUi.columns,
                    onPlay = { onPlayTracks(visibleTracks, index) },
                    onAddToUpNext = { onAddToUpNext(track) },
                    onDownload = { onDownload(track) },
                    compactLayout = true,
                    isNowPlaying = isNowPlaying,
                    nowPlayingIsPlaying = nowPlaying.isPlaying,
                    nowPlayingIsBuffering = nowPlaying.isBuffering,
                )
            }
        }
    }
    }
}

@Composable
internal fun ArtistAlbumGrid(albums: List<Album>, onAlbum: (Album) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minCardWidth = 160.dp
        val gap = 14.dp
        val available = maxWidth
        val columns = ((available + gap) / (minCardWidth + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            albums.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { album ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAlbum(album) }
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                ArtworkImage(
                                    album.title,
                                    album.thumbUrl,
                                    Modifier.fillMaxSize().sharedArtworkTransition("album:${album.id}"),
                                )
                            }
                            Text(
                                album.title,
                                color = PhoebeUi.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.sharedBoundsTransition("album:${album.id}:title"),
                            )
                            Text(
                                album.year?.toString() ?: "Album",
                                color = PhoebeUi.mutedText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumDetailPanel(
    album: Album,
    catalog: CatalogSnapshot,
    libraryUi: LibraryUiPreferences,
    catalogRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
) {
    val tracks = remember(catalog.tracksByParent, album.id) {
        catalog.tracksByParent[album.id].orEmpty()
    }

    var sortBy by remember(album.id) { mutableStateOf(LibrarySortBy.Name) }
    var ascending by remember(album.id) { mutableStateOf(true) }

    val sortedTracks = remember(tracks, sortBy, ascending) {
        sortTracksForLibrary(tracks, sortBy, ascending)
    }
    val visibleTracks = remember(sortedTracks, searchQuery) {
        filterTracksByQuery(sortedTracks, searchQuery)
    }
    val nowPlaying = LocalNowPlaying.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val useTable = maxWidth >= 640.dp
        val edgePadding = if (maxWidth < 640.dp) 20.dp else 36.dp
        val topPadding = if (maxWidth < 640.dp) 16.dp else 36.dp
        val listState = RetainedLazyListStates.remember("album-detail:${album.id}")
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(start = edgePadding, end = edgePadding, top = topPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(contentType = "album-header") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                DetailSectionIntro(
                    onBack = onBack,
                    label = "Album",
                    alignBackIconToContentStart = !useTable,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtworkImage(
                        album.title,
                        album.thumbUrl,
                        Modifier.size(160.dp).sharedArtworkTransition("album:${album.id}"),
                        elevated = useTable,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            album.title,
                            color = PhoebeUi.primaryText,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.sharedBoundsTransition("album:${album.id}:title"),
                        )
                        Text(
                            album.artist.uppercase(),
                            color = PhoebeUi.secondaryText,
                            fontSize = 14.sp,
                            letterSpacing = 0.05.em,
                            modifier = Modifier.sharedBoundsTransition("album:${album.id}:subtitle"),
                        )
                        album.year?.let { y ->
                            Text("$y", color = PhoebeUi.mutedText, fontSize = 13.sp)
                        }
                    }
                }
                SectionLabel("Tracks", PhoebeUi.primaryText)
            }
        }
        item(contentType = "album-track-toolbar") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailSectionToolbar(
                    sortBy = sortBy,
                    sortKeys = listOf(LibrarySortBy.Name, LibrarySortBy.Year),
                    sortLabel = { key ->
                        when (key) {
                            LibrarySortBy.Year -> "Release date"
                            else -> "Song name"
                        }
                    },
                    onSortBy = { sortBy = it },
                    ascending = ascending,
                    onAscending = { ascending = it },
                    columns = libraryUi.columns,
                    onColumns = onLibraryColumns,
                )
                if (catalogRefreshing && searchQuery.isBlank()) {
                    CatalogLoadingStrip()
                }
            }
        }
        if (visibleTracks.isEmpty()) {
            item(contentType = "album-empty") {
                Text(
                    when {
                        searchQuery.isNotBlank() -> "No tracks on ${album.title} match \"$searchQuery\"."
                        catalogRefreshing -> "Fetching songs…"
                        else -> "No tracks loaded yet."
                    },
                    color = PhoebeUi.mutedText,
                    fontSize = 15.sp,
                )
            }
        } else if (useTable) {
            item(contentType = "album-track-header") {
                SongsTableHeader(libraryUi.columns)
            }
            itemsIndexed(visibleTracks, key = { _, t -> t.id }, contentType = { _, _ -> "album-track" }) { index, track ->
                SongRow(
                    track = track,
                    selected = false,
                    columns = libraryUi.columns,
                    onSelect = { onPlayTracks(visibleTracks, index) },
                    onPlay = { onPlayTracks(visibleTracks, index) },
                    onAddToUpNext = { onAddToUpNext(track) },
                    onDownload = { onDownload(track) },
                )
            }
        } else {
            itemsIndexed(visibleTracks, key = { _, t -> t.id }, contentType = { _, _ -> "album-track" }) { index, track ->
                val isNowPlaying = track.id == nowPlaying.trackId
                ContentTrackRow(
                    track = track,
                    libraryColumns = libraryUi.columns,
                    onPlay = { onPlayTracks(visibleTracks, index) },
                    onAddToUpNext = { onAddToUpNext(track) },
                    onDownload = { onDownload(track) },
                    compactLayout = true,
                    isNowPlaying = isNowPlaying,
                    nowPlayingIsPlaying = nowPlaying.isPlaying,
                    nowPlayingIsBuffering = nowPlaying.isBuffering,
                )
            }
        }
    }
    }
}

@Composable
internal fun PlaylistExportMenu(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    actions: PlaylistActions = LocalPlaylistActions.current,
) {
    if (!playlist.isLocalPlaylist()) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .background(Color.White.copy(alpha = 0.04f))
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhoebeIconView(PhoebeIcon.Library, tint = PhoebeUi.secondaryText, modifier = Modifier.size(13.dp))
            Text("Export", color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("M3U8") },
                onClick = {
                    expanded = false
                    actions.onExportLocalPlaylist(playlist, PlaylistExportFormat.M3U8)
                },
            )
            DropdownMenuItem(
                text = { Text("Text") },
                onClick = {
                    expanded = false
                    actions.onExportLocalPlaylist(playlist, PlaylistExportFormat.Text)
                },
            )
            DropdownMenuItem(
                text = { Text("CSV") },
                onClick = {
                    expanded = false
                    actions.onExportLocalPlaylist(playlist, PlaylistExportFormat.Csv)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistDetailPanel(
    playlist: Playlist,
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    libraryUi: LibraryUiPreferences,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchQuery: (String) -> Unit = {},
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
) {
    val tracks = remember(catalog.tracksByParent, playlist.id) {
        catalog.tracksByParent[playlist.id].orEmpty()
    }

    var sortBy by remember(playlist.id) { mutableStateOf(LibrarySortBy.PlaylistOrder) }
    var ascending by remember(playlist.id) { mutableStateOf(true) }
    val sortedTracks = remember(tracks, sortBy, ascending) {
        sortTracksForLibrary(tracks, sortBy, ascending)
    }
    val visibleTracks = remember(sortedTracks, searchQuery) {
        filterTracksByQuery(sortedTracks, searchQuery)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val useTable = maxWidth >= 640.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(contentType = "playlist-header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailSectionIntro(
                    onBack = onBack,
                    label = "Playlist",
                    alignBackIconToContentStart = !useTable,
                )
                Text(playlist.title, color = PhoebeUi.primaryText, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PlaylistTrackSummaryLine(
                    totalCount = sortedTracks.size,
                    visibleCount = visibleTracks.size,
                    searchQuery = searchQuery,
                )
                SearchPill(
                    query = searchQuery,
                    onQueryChange = onSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = "Search songs and artists",
                )
                PlaylistExportMenu(playlist = playlist, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(6.dp))
                SectionLabel("Tracks", PhoebeUi.primaryText)
            }
        }
        item(contentType = "playlist-track-toolbar") {
            DetailSectionToolbar(
                sortBy = sortBy,
                sortKeys = listOf(LibrarySortBy.PlaylistOrder, LibrarySortBy.Name, LibrarySortBy.Album, LibrarySortBy.Year),
                sortLabel = { key ->
                    when (key) {
                        LibrarySortBy.PlaylistOrder -> "Playlist order"
                        LibrarySortBy.Album -> "Album name"
                        LibrarySortBy.Year -> "Release date"
                        else -> "Song name"
                    }
                },
                onSortBy = { sortBy = it },
                ascending = ascending,
                onAscending = { ascending = it },
                columns = libraryUi.columns,
                onColumns = onLibraryColumns,
            )
        }
        if (visibleTracks.isEmpty()) {
            item(contentType = "playlist-empty") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (catalogRefreshing && searchQuery.isBlank()) CatalogLoadingStrip()
                    Text(
                        when {
                            searchQuery.isNotBlank() -> "No tracks / artists in this playlist match \"$searchQuery\"."
                            catalogRefreshing -> "Fetching songs…"
                            else -> "No tracks loaded for this playlist yet."
                        },
                        color = PhoebeUi.mutedText,
                        fontSize = 15.sp,
                    )
                }
            }
        } else if (useTable) {
            item(contentType = "playlist-track-header") {
                SongsTableHeader(libraryUi.columns)
            }
            itemsIndexed(visibleTracks, key = { _, t -> t.id }, contentType = { _, _ -> "playlist-track" }) { index, track ->
                SongRow(
                    track = track,
                    selected = false,
                    columns = libraryUi.columns,
                    onSelect = { onPlayTracks(visibleTracks, index) },
                    onPlay = { onPlayTracks(visibleTracks, index) },
                    onAddToUpNext = { onAddToUpNext(track) },
                    onDownload = { onDownload(track) },
                    modifier = Modifier.animateItem(),
                )
            }
        } else {
            itemsIndexed(visibleTracks, key = { _, t -> t.id }, contentType = { _, _ -> "playlist-track" }) { index, track ->
                ContentTrackRow(
                    track = track,
                    libraryColumns = libraryUi.columns,
                    onPlay = { onPlayTracks(visibleTracks, index) },
                    onAddToUpNext = { onAddToUpNext(track) },
                    onDownload = { onDownload(track) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    }
}
