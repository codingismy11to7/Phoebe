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
import com.phoebe.app.player.CastState
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

@Composable
internal fun MobileCompactMainFeature(
    track: Track?,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MobileHomeHero(track, onOpenFullPlayer)
    }
}

@Composable
internal fun MobileHomeHero(track: Track?, onOpenFullPlayer: () -> Unit) {
    if (track == null) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EmptyNowPlayingArtworkSlot(Modifier.size(168.dp), glyphSp = 48.sp)
            SectionLabel("Now Playing", PhoebeUi.accentLight)
            Text("Nothing playing", color = PhoebeUi.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(
                "Use Search or Library below to pick a track.",
                color = PhoebeUi.secondaryText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Now Playing", PhoebeUi.accentLight)
            ArtworkImage(track.album, track.thumbUrl, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
            Text(track.title, color = PhoebeUi.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = PhoebeUi.secondaryText, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "Open full player",
                color = PhoebeUi.accentLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenFullPlayer)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
internal fun MobileBottomNavigation(
    section: DesktopSection,
    onSection: (DesktopSection) -> Unit,
) {
    val tabs = listOf(
        DesktopSection.Home to (PhoebeIcon.Home to "Home"),
        DesktopSection.Search to (PhoebeIcon.Search to "Search"),
        DesktopSection.Library to (PhoebeIcon.Library to "Library"),
        DesktopSection.Playlists to (PhoebeIcon.Queue to "Playlists"),
    )
    val topShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(topShape)
            .background(PhoebeUi.navBar, topShape)
            .border(BorderStroke(1.dp, PhoebeUi.border), topShape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { (target, iconLabel) ->
                val (icon, label) = iconLabel
                val active = section == target
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSection(target) }
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                        .semantics { contentDescription = label },
                ) {
                    PhoebeIconView(icon, tint = if (active) PhoebeUi.accentLight else PhoebeUi.secondaryText, modifier = Modifier.size(19.dp))
                    Text(
                        label.uppercase(),
                        color = if (active) PhoebeUi.primaryText else PhoebeUi.mutedText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.06.em,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
internal fun MobileScreenToolbar(
    title: String,
    onBack: (() -> Unit)? = null,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable () -> Unit,
    showMenu: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                PhoebeIconView(
                    PhoebeIcon.Back,
                    tint = PhoebeUi.primaryText,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Spacer(Modifier.size(44.dp))
        }
        Text(
            title,
            color = PhoebeUi.primaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (showMenu) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onMenuExpandedChange(true) },
                contentAlignment = Alignment.Center,
            ) {
                PhoebeIconView(
                    PhoebeIcon.More,
                    tint = PhoebeUi.primaryText,
                    modifier = Modifier.size(22.dp),
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                ) {
                    menuContent()
                }
            }
        } else {
            Spacer(Modifier.size(44.dp))
        }
    }
}

internal fun mobileSectionTitle(section: DesktopSection): String = when (section) {
    DesktopSection.Home -> "Home"
    DesktopSection.Search -> "Search"
    DesktopSection.Library -> "Library"
    DesktopSection.Playlists -> "Playlists"
    DesktopSection.Settings -> "Settings"
}

@Composable
internal fun MobileBrowseShell(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    session: PlexSession?,
    section: DesktopSection,
    selectedPlaylistId: String?,
    searchQuery: String,
    libraryFilter: LibraryFilterTab,
    libraryUi: LibraryUiPreferences,
    currentTrack: Track?,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    onNavigate: (DesktopSection) -> Unit,
    onSearchQuery: (String) -> Unit,
    onLibraryFilter: (LibraryFilterTab) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSignOut: () -> Unit,
    onAddLocalFolder: (String?) -> Unit,
    onRefreshLibrary: () -> Unit,
    onLibrarySortBy: (LibrarySortBy) -> Unit,
    onLibraryAscending: (Boolean) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
    useLightAppearance: Boolean,
    onUseLightAppearanceChange: (Boolean) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pickLocalFolder = rememberPickLocalFolder(onPicked = onAddLocalFolder)
    val toolbarTitle = when {
        section == DesktopSection.Settings -> "Settings"
        selectedPlaylistId != null -> "Playlist"
        else -> mobileSectionTitle(section)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(PhoebeUi.shellTop),
    ) {
        Column(
            Modifier
                .fillMaxSize(),
        ) {
        MobileScreenToolbar(
            title = toolbarTitle,
            onBack = if (section == DesktopSection.Settings && selectedPlaylistId == null) {
                { onNavigate(DesktopSection.Home) }
            } else {
                null
            },
            menuExpanded = menuExpanded,
            onMenuExpandedChange = { menuExpanded = it },
            showMenu = !(section == DesktopSection.Settings && selectedPlaylistId == null),
            menuContent = {
            val userName = session?.userName
            if (userName != null) {
                DropdownMenuItem(
                    text = { Text(userName, color = PhoebeUi.mutedText, fontSize = 13.sp) },
                    onClick = {},
                    enabled = false,
                )
            }
            DropdownMenuItem(
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PhoebeIconView(PhoebeIcon.Settings, tint = PhoebeUi.secondaryText, modifier = Modifier.size(18.dp))
                        Text("Settings")
                    }
                },
                onClick = {
                    onNavigate(DesktopSection.Settings)
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Refresh library") },
                onClick = {
                    onRefreshLibrary()
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Add music folder") },
                onClick = {
                    pickLocalFolder()
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Sign out") },
                onClick = {
                    onSignOut()
                    menuExpanded = false
                },
            )
            },
        )

        Column(Modifier.weight(1f).fillMaxWidth()) {
            when {
                section == DesktopSection.Settings && selectedPlaylistId == null -> SettingsMobileView(
                    isLightMode = useLightAppearance,
                    onLightModeChange = onUseLightAppearanceChange,
                    modifier = Modifier.fillMaxSize(),
                )
                section == DesktopSection.Home && selectedPlaylistId == null -> MobileCompactMainFeature(
                    track = currentTrack,
                    onOpenFullPlayer = onOpenNowPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
                section == DesktopSection.Library && selectedPlaylistId == null -> LibraryMobileView(
                    catalog = catalog,
                    catalogRefreshing = catalogRefreshing,
                    filter = libraryFilter,
                    libraryUi = libraryUi,
                    onFilter = onLibraryFilter,
                    onLibrarySortBy = onLibrarySortBy,
                    onLibraryAscending = onLibraryAscending,
                    onLibraryColumns = onLibraryColumns,
                    onArtist = onArtist,
                    onAlbum = onAlbum,
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                    modifier = Modifier.fillMaxSize(),
                )
                section == DesktopSection.Search && selectedPlaylistId == null -> SearchMobileView(
                    catalog = catalog,
                    catalogRefreshing = catalogRefreshing,
                    searchQuery = searchQuery,
                    onSearchQuery = onSearchQuery,
                    onArtist = onArtist,
                    onAlbum = onAlbum,
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                    modifier = Modifier.fillMaxSize(),
                )
                section == DesktopSection.Playlists && selectedPlaylistId == null -> PlaylistsMobileView(
                    catalogRefreshing = catalogRefreshing,
                    searchQuery = searchQuery,
                    onSearchQuery = onSearchQuery,
                    onPlaylist = onPlaylist,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> DesktopContent(
                    catalog = catalog,
                    catalogRefreshing = catalogRefreshing,
                    section = section,
                    selectedPlaylistId = selectedPlaylistId,
                    searchQuery = searchQuery,
                    libraryFilter = libraryFilter,
                    libraryUi = libraryUi,
                    modifier = Modifier.fillMaxSize(),
                    onSearchQuery = onSearchQuery,
                    onLibraryFilter = onLibraryFilter,
                    onPlaylist = onPlaylist,
                    onArtist = onArtist,
                    onAlbum = onAlbum,
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                    onLibrarySortBy = onLibrarySortBy,
                    onLibraryAscending = onLibraryAscending,
                    onLibraryColumns = onLibraryColumns,
                    edgePadding = 20.dp,
                    headlineFontSize = 22.sp,
                    headlineLineHeight = 26.sp,
                    searchPillModifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (currentTrack != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(PhoebeUi.panel)
                    .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenNowPlaying)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ArtworkImage(currentTrack.album, currentTrack.thumbUrl, Modifier.size(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            currentTrack.title,
                            color = PhoebeUi.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            currentTrack.artist,
                            color = PhoebeUi.secondaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                PlayButton(isPlaying, isBuffering, 40.dp, onTogglePlayPause, enabled = true)
            }
        }

        MobileBottomNavigation(section = section, onSection = onNavigate)
        }
    }
}

@Composable
internal fun MobilePlayer(
    track: Track?,
    upNext: List<Track>,
    previousTrack: Track? = null,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    shuffle: Boolean,
    repeat: RepeatMode,
    positionMs: Long,
    @Suppress("UNUSED_PARAMETER") currentIndex: Int,
    castState: CastState = CastState(),
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkipQueueBy: (Int) -> Unit = {},
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayQueue: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onCast: () -> Unit = {},
    onBack: () -> Unit,
    onSwipeDismiss: () -> Unit,
    initialUpNextExpanded: Boolean = true,
) {
    var mobileUpNextExpanded by remember { mutableStateOf(initialUpNextExpanded) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    val offScreenPx = with(density) { 1200.dp.toPx() }
    val animatedOffset by animateFloatAsState(
        targetValue = when {
            dismissing -> offScreenPx
            else -> dragOffset.coerceAtLeast(0f)
        },
        animationSpec = if (dismissing) {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        } else {
            spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioLowBouncy)
        },
        finishedListener = { value ->
            if (dismissing && value >= offScreenPx * 0.9f) {
                onSwipeDismiss()
            }
        },
        label = "player-swipe-settle",
    )
    val displayOffset = if (isDragging) dragOffset.coerceAtLeast(0f) else animatedOffset
    val hasTrack = track != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, displayOffset.roundToInt()) }
            .pointerInput(onBack, dismissThresholdPx, offScreenPx) {
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        if (dragOffset > dismissThresholdPx) {
                            dismissing = true
                        } else {
                            dragOffset = 0f
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (!dismissing && (dragAmount > 0f || dragOffset > 0f)) {
                            dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                        }
                    },
                )
            }
            .background(
                Brush.radialGradient(
                    listOf(PhoebeUi.shellRadialTint, Color.Transparent),
                    center = Offset(210f, 50f),
                    radius = 380f,
                ),
            )
            .background(Brush.verticalGradient(listOf(PhoebeUi.shellTop, PhoebeUi.canvasBackground)))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clickable(onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                PhoebeIconView(PhoebeIcon.ChevronDown, tint = PhoebeUi.primaryText, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
            SectionLabel("Now Playing", PhoebeUi.secondaryText)
            Spacer(Modifier.weight(1f))
            CastIcon(
                active = castState.isConnected,
                loading = castState.isBuffering,
                enabled = castState.isAvailable || castState.isConnected,
                onClick = onCast,
            )
        }

        Spacer(Modifier.height(24.dp))
        if (track != null) {
            ArtworkImage(track.album, track.thumbUrl, Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = PhoebeUi.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = PhoebeUi.secondaryText, fontSize = 15.sp)
                }
                PhoebeIconView(PhoebeIcon.Heart, tint = PhoebeUi.accentLight, modifier = Modifier.size(31.dp), filled = true)
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                EmptyNowPlayingArtworkSlot(Modifier.fillMaxSize(), glyphSp = 64.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nothing playing", color = PhoebeUi.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Choose a song from your library or search.",
                        color = PhoebeUi.secondaryText,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )
                }
                PhoebeIconView(PhoebeIcon.Heart, tint = PhoebeUi.mutedText.copy(alpha = 0.35f), modifier = Modifier.size(31.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        ProgressLine(
            positionMs,
            track?.durationMs ?: 0L,
            waveformSeed = track?.let(::trackWaveformSeed) ?: "",
            Modifier.fillMaxWidth(),
            onSeek = if (hasTrack) onSeek else null,
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ShuffleIcon(active = shuffle, onClick = onShuffle)
            TransportIcon(PhoebeIcon.Previous, "Previous Track", onPrevious)
            PlayButton(isPlaying, isBuffering, 58.dp, onToggle, enabled = hasTrack)
            TransportIcon(PhoebeIcon.Next, "Next Track", onNext)
            RepeatIcon(mode = repeat, onClick = onRepeat)
        }
        Spacer(Modifier.height(18.dp))
        val queueModifier = if (mobileUpNextExpanded) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
        MobileQueueSheet(
            currentTrack = track,
            upNext = upNext,
            repeat = repeat,
            expanded = mobileUpNextExpanded,
            onExpandedChange = { mobileUpNextExpanded = it },
            modifier = queueModifier,
            onPlayQueue = onPlayQueue,
            onMoveUpNext = onMoveUpNext,
            onRemoveUpNext = onRemoveUpNext,
        )
    }
}

@Composable
internal fun MobileQueueSheet(
    currentTrack: Track?,
    upNext: List<Track>,
    repeat: RepeatMode,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
    onPlayQueue: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(PhoebeUi.glass)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Up Next", PhoebeUi.primaryText)
            if (repeat != RepeatMode.Off) {
                Spacer(Modifier.width(8.dp))
                RepeatBadge(mode = repeat)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .semantics {
                        contentDescription = if (expanded) "Collapse Up Next" else "Expand Up Next"
                    },
                contentAlignment = Alignment.Center,
            ) {
                PhoebeIconView(
                    if (expanded) PhoebeIcon.ChevronUp else PhoebeIcon.ChevronDown,
                    tint = PhoebeUi.mutedText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (expanded) {
            if (currentTrack == null && upNext.isEmpty()) {
                Text("Pick a song to start a queue.", color = PhoebeUi.mutedText, fontSize = 12.sp)
            } else {
                UpNextList(
                    currentTrack = currentTrack,
                    upNext = upNext,
                    repeat = repeat,
                    onPlayQueue = onPlayQueue,
                    onMoveUpNext = onMoveUpNext,
                    onRemoveUpNext = onRemoveUpNext,
                    modifier = Modifier.fillMaxWidth(),
                    thumbnail = 40.dp,
                    rowHeight = 56.dp,
                )
            }
        } else {
            val peek = upNext.firstOrNull()
            if (peek == null) {
                Text(
                    if (currentTrack == null) "Pick a song to start a queue." else "Nothing queued after this track.",
                    color = PhoebeUi.mutedText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPlayQueue(0) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ArtworkImage(peek.album, peek.thumbUrl, Modifier.size(40.dp), radius = 6.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Next: ${peek.title}",
                            color = PhoebeUi.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            peek.artist,
                            color = PhoebeUi.secondaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

