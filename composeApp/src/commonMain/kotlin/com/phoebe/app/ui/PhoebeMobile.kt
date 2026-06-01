package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.phoebe.app.data.ListenBrainzFeedbackScore
import com.phoebe.app.data.ListenBrainzFeedbackTarget
import com.phoebe.app.data.catalogAlbumsForArtist
import com.phoebe.app.data.catalogTracksForArtist
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.AppScreen
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.ArtistRadioAvailability
import com.phoebe.app.domain.HomeSection
import com.phoebe.app.domain.JellyfinLibraryPageKind
import com.phoebe.app.domain.JellyfinSyncMode
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PersonalMixPreferences
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.PlexRadioStation
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.defaultCollectionEntries
import com.phoebe.app.domain.canTogglePlexLike
import com.phoebe.app.player.CastState
import com.phoebe.app.domain.isEmbyFamily
import com.phoebe.app.domain.isJellyfin
import com.phoebe.app.domain.isNavidrome
import com.phoebe.app.domain.isRemoteLibraryTrack
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.platform.createPlatformHttpClient
import com.phoebe.app.platform.currentTimeMs
import com.phoebe.app.platform.isDesktopPlatform
import com.phoebe.app.platform.prefersReducedArtworkEffects
import com.phoebe.app.platform.SecureCredentialAvailability
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.phoebe.app.sources.rememberPickLocalFolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.abs
import kotlin.math.max

private const val MobilePlayerArtworkLoadDelayMs = 96L
private const val MobilePlayerContinuousMotionDelayMs = 240L
private val MobileToolbarChromeHeight = 56.dp
private val MobileBottomNavChromeHeight = 68.dp
private val MobileMiniPlayerChromeHeight = 66.dp
private val MobileChromeScrollGap = 12.dp
private val MobilePlayerMetadataReserveWithAlbum = 104.dp
private val MobilePlayerMetadataReserveWithoutAlbum = 84.dp
private val MobilePlayerRemoteTargetReserve = 18.dp

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
            TrackArtworkImage(track, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
            AutoScrollingText(track.title, color = PhoebeUi.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Black)
            AutoScrollingText(track.artist, color = PhoebeUi.secondaryText, fontSize = 15.sp)
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
    section: BrowseSection,
    onSection: (BrowseSection) -> Unit,
) {
    val tabs = listOf(
        BrowseSection.Home to (PhoebeIcon.Home to "Home"),
        BrowseSection.Search to (PhoebeIcon.Search to "Search"),
        BrowseSection.Library to (PhoebeIcon.Library to "Library"),
        BrowseSection.Playlists to (PhoebeIcon.PlaylistPlay to "Playlists"),
    )
    val topShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MobileBottomNavChromeHeight)
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

internal fun mobileSectionTitle(section: BrowseSection): String = when (section) {
    BrowseSection.Home -> "Home"
    BrowseSection.Search -> "Search"
    BrowseSection.Library -> "Library"
    BrowseSection.Lyrics -> "Lyrics"
    BrowseSection.Playlists -> "Playlists"
    BrowseSection.Settings -> "Settings"
}

@Composable
internal fun MobileBrowseShell(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    session: PlexSession?,
    section: BrowseSection,
    selectedPlaylistId: String?,
    searchQuery: String,
    libraryFilter: LibraryFilterTab,
    libraryUi: LibraryUiPreferences,
    currentTrack: Track?,
    homeUiState: HomeUiState,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    onNavigate: (BrowseSection) -> Unit,
    onSearchQuery: (String) -> Unit,
    onLibraryFilter: (LibraryFilterTab) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onSong: (Track) -> Unit,
    onRecentSongs: () -> Unit,
    onRecentArtists: () -> Unit,
    onRecentAlbums: () -> Unit,
    onFavoritePlaylists: () -> Unit,
    onFavoriteArtists: () -> Unit,
    onFavoriteAlbums: () -> Unit,
    onRecentlyPlayed: () -> Unit,
    onMostPlayed: () -> Unit,
    onCollections: (CollectionEntry) -> Unit,
    supportedCollectionEntries: Set<CollectionEntry> = defaultCollectionEntries.toSet(),
    onRefreshRandomArtists: () -> Unit,
    onRefreshRandomAlbums: () -> Unit,
    onPrefetchHomeArtist: (Artist) -> Unit = {},
    onPrefetchHomeAlbum: (Album) -> Unit = {},
    onPlayDecadeMix: (Int) -> Unit = {},
    decadeMixNotice: String? = null,
    onClearDecadeMixNotice: () -> Unit = {},
    radioStations: List<PlexRadioStation> = emptyList(),
    radioStartingIds: Set<String> = emptySet(),
    onPlayRadioStation: (PlexRadioStation) -> Unit = {},
    onPlayPersonalMix: () -> Unit = {},
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    artistRadioAvailability: Map<String, ArtistRadioAvailability> = emptyMap(),
    onProbeArtistRadio: (Artist) -> Unit = {},
    onPlayArtistRadio: (Artist) -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSignOut: () -> Unit,
    onAddLocalFolder: (String?) -> Unit,
    onRefreshLibrary: () -> Unit,
    onRefreshPlayHistory: () -> Unit = {},
    onJellyfinPage: (JellyfinLibraryPageKind, Int) -> Unit = { _, _ -> },
    onLibrarySortBy: (LibrarySortBy) -> Unit,
    onLibraryAscending: (Boolean) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
    onHomeSections: (List<HomeSection>) -> Unit,
    onPersonalMix: (PersonalMixPreferences) -> Unit,
    onGridColumns: (Int) -> Unit,
    onExportFavoritePlaylists: () -> Unit,
    onImportFavoritePlaylists: () -> Unit,
    appSettings: AppSettings,
    homeScreenLayoutMode: HomeScreenLayoutMode = HomeScreenLayoutMode.Default,
    onCrossfadeSeconds: (Int) -> Unit,
    onScanLibraryOnLaunch: (Boolean) -> Unit,
    onNotifyWhenDownloadFinishes: (Boolean) -> Unit,
    onPersistEqualizerSettings: (Boolean) -> Unit = {},
    downloadDirectory: String?,
    downloadCount: Int,
    defaultDownloadDirectoryLabel: String,
    onDownloadDirectory: (String?) -> Unit,
    onDeleteAllDownloads: () -> Unit,
    useLightAppearance: Boolean,
    onUseLightAppearanceChange: (Boolean) -> Unit,
    appearanceTintId: String,
    onAppearanceTintChange: (String) -> Unit,
    onHomeScreenLayoutModeChange: (HomeScreenLayoutMode) -> Unit = {},
    listenBrainzCredentialAvailability: SecureCredentialAvailability = SecureCredentialAvailability.Unavailable,
    onConnectListenBrainz: (String) -> Unit = {},
    onDisconnectListenBrainz: () -> Unit = {},
    onListenBrainzSubmitNowPlaying: (Boolean) -> Unit = {},
    onListenBrainzSubmitListens: (Boolean) -> Unit = {},
    onListenBrainzSubmitCurrentTrackFeedback: (Boolean) -> Unit = {},
    initialExpandedPhoneSection: PhoneHomeAccordionSection? = null,
    homeListState: LazyListState? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pickLocalFolder = rememberPickLocalFolder(onPicked = onAddLocalFolder)
    val toolbarTitle = when {
        section == BrowseSection.Settings -> "Settings"
        selectedPlaylistId != null -> "Playlist"
        else -> mobileSectionTitle(section)
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navigationBottomPadding = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val miniPlayerOpenDragThresholdPx = with(density) { 48.dp.toPx() }
    val miniPlayerSkipDragThresholdPx = with(density) { 56.dp.toPx() }
    val miniPlayerSkipPreviewMaxPx = miniPlayerSkipDragThresholdPx * 1.45f
    var miniPlayerDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDraggingMiniPlayer by remember { mutableStateOf(false) }
    val miniPlayerSettleOffsetPx = remember { Animatable(0f) }
    var miniPlayerSettleJob by remember { mutableStateOf<Job?>(null) }
    fun resistedMiniPlayerOffset(rawOffsetPx: Float): Float {
        val direction = if (rawOffsetPx < 0f) -1f else 1f
        val distance = abs(rawOffsetPx)
        val resistedDistance = when {
            distance <= miniPlayerSkipDragThresholdPx -> distance
            else -> miniPlayerSkipDragThresholdPx + (distance - miniPlayerSkipDragThresholdPx) * 0.38f
        }.coerceAtMost(miniPlayerSkipPreviewMaxPx)
        return direction * resistedDistance
    }
    fun settleMiniPlayer(fromOffsetPx: Float, targetOffsetPx: Float, onTargetReached: (() -> Unit)? = null) {
        miniPlayerSettleJob?.cancel()
        miniPlayerSettleJob = scope.launch {
            miniPlayerSettleOffsetPx.snapTo(fromOffsetPx)
            if (targetOffsetPx != 0f) {
                miniPlayerSettleOffsetPx.animateTo(
                    targetValue = targetOffsetPx,
                    animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                )
                onTargetReached?.invoke()
            }
            miniPlayerSettleOffsetPx.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                ),
            )
        }
    }
    val chromePadding = MobileChromePadding(
        top = MobileToolbarChromeHeight + MobileChromeScrollGap,
        bottom = MobileBottomNavChromeHeight +
            navigationBottomPadding +
            MobileChromeScrollGap +
            if (currentTrack != null) MobileMiniPlayerChromeHeight else 0.dp,
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(PhoebeUi.shellTop),
    ) {
        CompositionLocalProvider(LocalMobileChromePadding provides chromePadding) {
            Box(Modifier.fillMaxSize()) {
            when {
                section == BrowseSection.Settings && selectedPlaylistId == null -> SettingsMobileView(
                    isLightMode = useLightAppearance,
                    onLightModeChange = onUseLightAppearanceChange,
                    tintId = appearanceTintId,
                    onTintChange = onAppearanceTintChange,
                    homeScreenLayoutMode = homeScreenLayoutMode,
                    onHomeScreenLayoutModeChange = onHomeScreenLayoutModeChange,
                    downloadDirectory = downloadDirectory,
                    downloadCount = downloadCount,
                    appSettings = appSettings,
                    libraryUi = libraryUi,
                    defaultDownloadDirectoryLabel = defaultDownloadDirectoryLabel,
                    onDownloadDirectory = onDownloadDirectory,
                    onDeleteAllDownloads = onDeleteAllDownloads,
                    onCrossfadeSeconds = onCrossfadeSeconds,
                    onScanLibraryOnLaunch = onScanLibraryOnLaunch,
                    onNotifyWhenDownloadFinishes = onNotifyWhenDownloadFinishes,
                    onPersistEqualizerSettings = onPersistEqualizerSettings,
                    onHomeSections = onHomeSections,
                    onPersonalMix = onPersonalMix,
                    onGridColumns = onGridColumns,
                    onExportFavoritePlaylists = onExportFavoritePlaylists,
                    onImportFavoritePlaylists = onImportFavoritePlaylists,
                    session = session,
                    listenBrainzCredentialAvailability = listenBrainzCredentialAvailability,
                    onConnectListenBrainz = onConnectListenBrainz,
                    onDisconnectListenBrainz = onDisconnectListenBrainz,
                    onListenBrainzSubmitNowPlaying = onListenBrainzSubmitNowPlaying,
                    onListenBrainzSubmitListens = onListenBrainzSubmitListens,
                    onListenBrainzSubmitCurrentTrackFeedback = onListenBrainzSubmitCurrentTrackFeedback,
                    modifier = Modifier.fillMaxSize().padding(top = chromePadding.top, bottom = chromePadding.bottom),
                )
                section == BrowseSection.Home && selectedPlaylistId == null -> {
                    val homeListState = homeListState ?: RetainedLazyListStates.remember("mobile-home")
                    val mobileHomeRouteState = remember(
                        homeUiState.recentlyAddedTracks,
                        homeUiState.recentlyAddedArtists,
                        homeUiState.recentlyAddedAlbums,
                        homeUiState.favoritePlaylists,
                        homeUiState.favoriteArtists,
                        homeUiState.favoriteAlbums,
                        homeUiState.favoritePlaylistCount,
                        homeUiState.favoriteArtistCount,
                        homeUiState.favoriteAlbumCount,
                        homeUiState.randomArtists,
                        homeUiState.randomAlbums,
                        homeUiState.artistThumbs,
                        homeScreenLayoutMode,
                        catalogRefreshing,
                        libraryUi.homeSections,
                        supportedCollectionEntries,
                        radioStations,
                        radioStartingIds,
                        decadeMixNotice,
                    ) {
                        MobileHomeRouteState(
                            homeUiState = homeUiState,
                            catalogRefreshing = catalogRefreshing,
                            homeSections = libraryUi.homeSections,
                            supportedCollectionEntries = supportedCollectionEntries,
                            radioStations = radioStations,
                            radioStartingIds = radioStartingIds,
                            decadeMixNotice = decadeMixNotice,
                            homeScreenLayoutMode = homeScreenLayoutMode,
                        )
                    }
                    val mobileHomeCallbacks = remember(
                        onArtist,
                        onAlbum,
                        onPlaylist,
                        onRecentSongs,
                        onRecentArtists,
                        onRecentAlbums,
                        onFavoritePlaylists,
                        onFavoriteArtists,
                        onFavoriteAlbums,
                        onCollections,
                        onRecentlyPlayed,
                        onMostPlayed,
                        onRefreshRandomArtists,
                        onRefreshRandomAlbums,
                        onPlayDecadeMix,
                        onClearDecadeMixNotice,
                        onPlayRadioStation,
                        onPlayPersonalMix,
                        onPlayTracks,
                        onAddToUpNext,
                        onDownload,
                    ) {
                        MobileHomeCallbacks(
                            onArtist = onArtist,
                            onAlbum = onAlbum,
                            onPlaylist = onPlaylist,
                            onRecentSongs = onRecentSongs,
                            onRecentArtists = onRecentArtists,
                            onRecentAlbums = onRecentAlbums,
                            onFavoritePlaylists = onFavoritePlaylists,
                            onFavoriteArtists = onFavoriteArtists,
                            onFavoriteAlbums = onFavoriteAlbums,
                            onCollections = onCollections,
                            onRecentlyPlayed = onRecentlyPlayed,
                            onMostPlayed = onMostPlayed,
                            onRefreshArtists = onRefreshRandomArtists,
                            onRefreshAlbums = onRefreshRandomAlbums,
                            onPlayDecadeMix = onPlayDecadeMix,
                            onClearDecadeMixNotice = onClearDecadeMixNotice,
                            onPlayRadioStation = onPlayRadioStation,
                            onPlayPersonalMix = onPlayPersonalMix,
                            onPlayTracks = onPlayTracks,
                            onAddToUpNext = onAddToUpNext,
                            onDownload = onDownload,
                        )
                    }
                    MobileHomeRoute(
                        routeState = mobileHomeRouteState,
                        listState = homeListState,
                        callbacks = mobileHomeCallbacks,
                        modifier = Modifier.fillMaxSize(),
                        initialExpandedPhoneSection = initialExpandedPhoneSection,
                    )
                }
                section == BrowseSection.Library && selectedPlaylistId == null -> LibraryMobileView(
                    catalog = catalog,
                    catalogRefreshing = catalogRefreshing,
                    filter = libraryFilter,
                    libraryUi = libraryUi,
                    jellyfinPagination = (session.isEmbyFamily() || session.isNavidrome()) && session?.jellyfinSyncMode == JellyfinSyncMode.Quick,
                    onJellyfinPage = onJellyfinPage,
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
                section == BrowseSection.Search && selectedPlaylistId == null -> SearchMobileView(
                    catalog = catalog,
                    catalogRefreshing = catalogRefreshing,
                    searchQuery = searchQuery,
                    onSearchQuery = onSearchQuery,
                    onArtist = onArtist,
                    onAlbum = onAlbum,
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                    modifier = Modifier.fillMaxSize().padding(top = chromePadding.top, bottom = chromePadding.bottom),
                )
                section == BrowseSection.Playlists && selectedPlaylistId == null -> PlaylistsMobileView(
                    catalogRefreshing = catalogRefreshing,
                    searchQuery = searchQuery,
                    onSearchQuery = onSearchQuery,
                    onPlaylist = onPlaylist,
                    modifier = Modifier.fillMaxSize().padding(top = chromePadding.top, bottom = chromePadding.bottom),
                )
                else -> DesktopContent(
                    catalog = catalog,
                    catalogRefreshing = catalogRefreshing,
                    jellyfinPagination = (session.isEmbyFamily() || session.isNavidrome()) && session?.jellyfinSyncMode == JellyfinSyncMode.Quick,
                    onJellyfinPage = onJellyfinPage,
                    section = section,
                    selectedPlaylistId = selectedPlaylistId,
                    searchQuery = searchQuery,
                    libraryFilter = libraryFilter,
                    libraryUi = libraryUi,
                    modifier = Modifier.fillMaxSize().padding(top = chromePadding.top, bottom = chromePadding.bottom),
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
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(PhoebeUi.shellTop)
                .zIndex(2f),
        ) {
            MobileScreenToolbar(
                title = toolbarTitle,
                onBack = if (section == BrowseSection.Settings && selectedPlaylistId == null) {
                    { onNavigate(BrowseSection.Home) }
                } else {
                    null
                },
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { menuExpanded = it },
                showMenu = !(section == BrowseSection.Settings && selectedPlaylistId == null),
                menuContent = {
                    val userName = session?.userName
                    if (userName != null) {
                        DropdownMenuItem(
                            text = { Text(userName, color = PhoebeUi.mutedText, fontSize = 13.sp) },
                            onClick = {},
                            enabled = false,
                        )
                    }
                    if (LocalCatalogSyncState.current.isActive) {
                        DropdownMenuItem(
                            text = { CatalogMenuSyncIndicator() },
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
                            onNavigate(BrowseSection.Settings)
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
                        text = { Text("Sync play history") },
                        onClick = {
                            onRefreshPlayHistory()
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
        }

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .zIndex(2f),
    ) {
            if (currentTrack != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(
                            onOpenNowPlaying,
                            onPreviousTrack,
                            onNextTrack,
                            miniPlayerOpenDragThresholdPx,
                            miniPlayerSkipDragThresholdPx,
                            miniPlayerSkipPreviewMaxPx,
                        ) {
                            var upwardDragPx = 0f
                            var horizontalDragPx = 0f
                            fun resetMiniPlayerDrag(offsetPx: Float = 0f, horizontalOffsetPx: Float = 0f) {
                                upwardDragPx = 0f
                                horizontalDragPx = horizontalOffsetPx
                                miniPlayerDragOffsetPx = offsetPx
                            }
                            fun horizontalDragIsDominant(): Boolean =
                                abs(horizontalDragPx) > upwardDragPx * 1.2f

                            detectDragGestures(
                                onDragStart = {
                                    miniPlayerSettleJob?.cancel()
                                    isDraggingMiniPlayer = true
                                    resetMiniPlayerDrag(miniPlayerSettleOffsetPx.value)
                                    scope.launch { miniPlayerSettleOffsetPx.stop() }
                                },
                                onDragEnd = {
                                    isDraggingMiniPlayer = false
                                    val releaseOffsetPx = miniPlayerDragOffsetPx
                                    val horizontalDominant = horizontalDragIsDominant()
                                    val shouldSkip = abs(horizontalDragPx) > miniPlayerSkipDragThresholdPx && horizontalDominant
                                    val shouldOpen = upwardDragPx > miniPlayerOpenDragThresholdPx && !horizontalDominant
                                    when {
                                        shouldSkip && horizontalDragPx < 0f -> {
                                            settleMiniPlayer(releaseOffsetPx, -miniPlayerSkipPreviewMaxPx, onNextTrack)
                                        }
                                        shouldSkip -> {
                                            settleMiniPlayer(releaseOffsetPx, miniPlayerSkipPreviewMaxPx, onPreviousTrack)
                                        }
                                        else -> {
                                            settleMiniPlayer(releaseOffsetPx, 0f)
                                            if (shouldOpen) onOpenNowPlaying()
                                        }
                                    }
                                    resetMiniPlayerDrag()
                                },
                                onDragCancel = {
                                    isDraggingMiniPlayer = false
                                    val releaseOffsetPx = miniPlayerDragOffsetPx
                                    settleMiniPlayer(releaseOffsetPx, 0f)
                                    resetMiniPlayerDrag()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    upwardDragPx = (upwardDragPx - dragAmount.y).coerceAtLeast(0f)
                                    horizontalDragPx += dragAmount.x
                                    miniPlayerDragOffsetPx = if (horizontalDragIsDominant()) {
                                        val targetOffset = resistedMiniPlayerOffset(horizontalDragPx)
                                        miniPlayerDragOffsetPx + (targetOffset - miniPlayerDragOffsetPx) * 0.72f
                                    } else {
                                        0f
                                    }
                                },
                            )
                        }
                        .graphicsLayer {
                            val offsetPx = if (isDraggingMiniPlayer) {
                                miniPlayerDragOffsetPx
                            } else {
                                miniPlayerSettleOffsetPx.value
                            }
                            val swipeProgress = (abs(offsetPx) / miniPlayerSkipDragThresholdPx).coerceIn(0f, 1f)
                            translationX = offsetPx
                            alpha = 1f - swipeProgress * 0.14f
                            val scale = 1f - swipeProgress * 0.025f
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(PhoebeUi.navBar)
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
                        TrackArtworkImage(currentTrack, Modifier.size(44.dp))
                        Column(Modifier.weight(1f)) {
                            AutoScrollingText(
                                currentTrack.title,
                                color = PhoebeUi.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            AutoScrollingText(
                                currentTrack.artist,
                                color = PhoebeUi.secondaryText,
                                fontSize = 12.sp,
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
internal fun SwipeableMobileArtwork(
    track: Track,
    nextTrack: Track?,
    previousTrack: Track?,
    onSkipQueueBy: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxDecodeDimension: Int = ListArtworkMaxDecodeDimension,
    frontOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val settleOffset = remember(track.id) { Animatable(0f) }
    var dragOffset by remember(track.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(track.id) { mutableStateOf(false) }
    var settleJob by remember(track.id) { mutableStateOf<Job?>(null) }
    var swipePreviewDirection by remember(track.id) { mutableStateOf(0) }
    val latestTrackId by rememberUpdatedState(track.id)

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .semantics {
                contentDescription = "Album artwork. Swipe left for next track, swipe right for previous track."
            },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val swipeThresholdPx = with(density) { 56.dp.toPx() }
        fun artworkOffsetPx(): Float = if (isDragging) dragOffset else settleOffset.value
        fun previewDirectionFor(offsetPx: Float): Int = when {
            offsetPx < 0f && nextTrack != null -> -1
            offsetPx > 0f && previousTrack != null -> 1
            else -> 0
        }

        fun settleToCenter(fromOffset: Float) {
            settleJob?.cancel()
            settleJob = scope.launch {
                settleOffset.snapTo(fromOffset)
                settleOffset.animateTo(
                    0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessLow,
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                    ),
                )
                swipePreviewDirection = 0
            }
        }

        fun animateSwipeCommit(releaseOffset: Float) {
            settleJob?.cancel()
            settleJob = scope.launch {
                val startingTrackId = latestTrackId
                settleOffset.snapTo(releaseOffset)
                when {
                    releaseOffset < -swipeThresholdPx && nextTrack != null -> {
                        settleOffset.animateTo(
                            targetValue = -widthPx,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        )
                        val steps = (abs(releaseOffset) / widthPx).toInt().coerceIn(1, 5)
                        onSkipQueueBy(steps)
                        delay(120L)
                        if (latestTrackId == startingTrackId) {
                            settleOffset.animateTo(
                                0f,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                ),
                            )
                        }
                    }
                    releaseOffset > swipeThresholdPx && previousTrack != null -> {
                        settleOffset.animateTo(
                            targetValue = widthPx,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        )
                        val steps = -(abs(releaseOffset) / widthPx).toInt().coerceIn(1, 5)
                        onSkipQueueBy(steps)
                        delay(120L)
                        if (latestTrackId == startingTrackId) {
                            settleOffset.animateTo(
                                0f,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                ),
                            )
                        }
                    }
                    else -> {
                        settleOffset.animateTo(
                            0f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                            ),
                        )
                    }
                }
                if (latestTrackId == startingTrackId) {
                    swipePreviewDirection = 0
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(track.id, widthPx, swipeThresholdPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            dragOffset = settleOffset.value
                            swipePreviewDirection = previewDirectionFor(dragOffset)
                            isDragging = true
                            scope.launch { settleOffset.stop() }
                        },
                        onDragEnd = {
                            val releaseOffset = dragOffset
                            swipePreviewDirection = previewDirectionFor(releaseOffset)
                            isDragging = false
                            dragOffset = 0f
                            animateSwipeCommit(releaseOffset)
                        },
                        onDragCancel = {
                            val releaseOffset = dragOffset
                            swipePreviewDirection = previewDirectionFor(releaseOffset)
                            isDragging = false
                            dragOffset = 0f
                            settleToCenter(releaseOffset)
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffset += dragAmount
                            swipePreviewDirection = previewDirectionFor(dragOffset)
                        },
                    )
                },
        ) {
            if (nextTrack != null && swipePreviewDirection < 0) {
                TrackArtworkImage(
                    nextTrack,
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset((widthPx + artworkOffsetPx()).roundToInt(), 0) },
                    radius = 10.dp,
                    maxDecodeDimension = maxDecodeDimension,
                )
            }
            if (previousTrack != null && swipePreviewDirection > 0) {
                TrackArtworkImage(
                    previousTrack,
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset((artworkOffsetPx() - widthPx).roundToInt(), 0) },
                    radius = 10.dp,
                    maxDecodeDimension = maxDecodeDimension,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(artworkOffsetPx().roundToInt(), 0) }
                    .graphicsLayer {
                        val dragProgress = (abs(artworkOffsetPx()) / widthPx).coerceIn(0f, 1f)
                        val scale = 1f - dragProgress * 0.03f
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                key(track.id) {
                    FlippableSongArtwork(
                        track = track,
                        modifier = Modifier.fillMaxSize(),
                        radius = 10.dp,
                        maxDecodeDimension = maxDecodeDimension,
                        frontOverlay = frontOverlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberRetainedMobilePlayerUpNextSheetState(
    key: String,
    initiallyExpanded: Boolean,
): MobilePlayerUpNextSheetState =
    remember(key) {
        RetainedMobilePlayerUpNextSheetStates.getOrPut(
            key = key,
            initiallyExpanded = initiallyExpanded,
        )
    }

private object RetainedMobilePlayerUpNextSheetStates {
    private val cache = mutableMapOf<String, MobilePlayerUpNextSheetState>()

    fun getOrPut(key: String, initiallyExpanded: Boolean): MobilePlayerUpNextSheetState =
        cache.getOrPut(key) { MobilePlayerUpNextSheetState(if (initiallyExpanded) 1f else 0f) }
}

private class MobilePlayerUpNextSheetState(initialProgress: Float) {
    var progress by mutableFloatStateOf(initialProgress.coerceIn(0f, 1f))
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
    bufferedPositionMs: Long,
    @Suppress("UNUSED_PARAMETER") currentIndex: Int,
    castState: CastState = CastState(),
    remotePlaybackTarget: String? = null,
    listenBrainzFeedbackTarget: ListenBrainzFeedbackTarget = ListenBrainzFeedbackTarget(),
    equalizerProfile: EqualizerProfile = EqualizerProfile.Default,
    persistEqualizerSettings: Boolean = false,
    equalizerRemoteUnavailable: Boolean = false,
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
    onOpenSongDetail: (Track) -> Unit = {},
    onCast: () -> Unit = {},
    onLyrics: () -> Unit = {},
    onEqualizerEnabled: (Boolean) -> Unit = {},
    onEqualizerBandCount: (Int) -> Unit = {},
    onEqualizerGain: (Int, Float) -> Unit = { _, _ -> },
    onEqualizerReset: () -> Unit = {},
    onPersistEqualizerSettings: (Boolean) -> Unit = {},
    onListenBrainzFeedback: (ListenBrainzFeedbackScore) -> Unit = {},
    onBack: () -> Unit,
    onSwipeDismiss: () -> Unit,
    handleSystemBack: Boolean = true,
    initialUpNextExpanded: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val timelineBufferedPositionMs = rememberTimelineBufferedPositionMs(
        track = track,
        positionMs = positionMs,
        bufferedPositionMs = bufferedPositionMs,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
    )
    val retainedSheetState = rememberRetainedMobilePlayerUpNextSheetState(
        key = "mobile-player-up-next-sheet",
        initiallyExpanded = initialUpNextExpanded,
    )
    val upNextListState = RetainedLazyListStates.remember("mobile-player-up-next-list")
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDraggingDismiss by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
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
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    val displayOffset = when {
        predictiveBackProgress > 0f -> offScreenPx * predictiveBackProgress.coerceIn(0f, 1f)
        isDraggingDismiss -> dragOffset.coerceAtLeast(0f)
        else -> animatedOffset
    }
    val hasTrack = track != null
    val inheritedArtworkLoadingEnabled = LocalArtworkLoadingEnabled.current
    val inheritedContinuousMotionEnabled = LocalContinuousMotionEnabled.current
    var playerArtworkLoadingEnabled by remember(track?.id) { mutableStateOf(false) }
    var playerContinuousMotionEnabled by remember(track?.id) { mutableStateOf(false) }
    val playerMotionEnabled = inheritedContinuousMotionEnabled && playerContinuousMotionEnabled
    LaunchedEffect(track?.id, inheritedArtworkLoadingEnabled) {
        playerArtworkLoadingEnabled = false
        if (track != null && inheritedArtworkLoadingEnabled) {
            delay(MobilePlayerArtworkLoadDelayMs)
            playerArtworkLoadingEnabled = true
        }
    }
    LaunchedEffect(track?.id, inheritedContinuousMotionEnabled) {
        playerContinuousMotionEnabled = false
        if (track != null && inheritedContinuousMotionEnabled) {
            delay(MobilePlayerContinuousMotionDelayMs)
            playerContinuousMotionEnabled = true
        }
    }
    var equalizerOpen by remember { mutableStateOf(false) }
    val trackNavigationActions = LocalTrackNavigationActions.current
    val likeActions = LocalLikeActions.current
    if (equalizerOpen) {
        EqualizerDialog(
            profile = equalizerProfile,
            persistEnabled = persistEqualizerSettings,
            remoteUnavailable = equalizerRemoteUnavailable,
            onEnabledChange = onEqualizerEnabled,
            onBandCountChange = onEqualizerBandCount,
            onGainChange = onEqualizerGain,
            onReset = onEqualizerReset,
            onPersistChange = onPersistEqualizerSettings,
            onDismiss = { equalizerOpen = false },
        )
    }
    PlatformBackHandler(
        enabled = handleSystemBack,
        onBack = {
            predictiveBackProgress = 0f
            onBack()
        },
        onBackProgress = { progress ->
            predictiveBackProgress = progress.coerceIn(0f, 1f)
        },
        onBackCancel = {
            predictiveBackProgress = 0f
        },
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, displayOffset.roundToInt()) }
            .background(
                Brush.radialGradient(
                    listOf(PhoebeUi.shellRadialTint, Color.Transparent),
                    center = Offset(210f, 50f),
                    radius = 380f,
                ),
            )
            .background(Brush.verticalGradient(listOf(PhoebeUi.shellTop, PhoebeUi.canvasBackground))),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val collapsedSheetHeightPx = with(density) {
                val navBarBottom = WindowInsets.navigationBars.getBottom(this).toDp()
                (88.dp + navBarBottom).toPx()
            }
            val expandedSheetHeightPx = with(density) {
                val controlsPx = 130.dp.toPx()
                val headerPx = 56.dp.toPx()
                (maxHeight.toPx() - controlsPx - headerPx)
                    .coerceAtLeast(collapsedSheetHeightPx + 80.dp.toPx())
            }
            val sheetRangePx = (expandedSheetHeightPx - collapsedSheetHeightPx).coerceAtLeast(1f)
            fun progressForHeight(heightPx: Float): Float =
                ((heightPx - collapsedSheetHeightPx) / sheetRangePx).coerceIn(0f, 1f)

            fun heightForProgress(progress: Float): Float =
                collapsedSheetHeightPx + sheetRangePx * progress.coerceIn(0f, 1f)

            val sheetHeight = remember(expandedSheetHeightPx, collapsedSheetHeightPx) {
                Animatable(heightForProgress(retainedSheetState.progress))
            }
            LaunchedEffect(expandedSheetHeightPx, collapsedSheetHeightPx) {
                sheetHeight.snapTo(heightForProgress(retainedSheetState.progress))
            }
            var isDraggingSheet by remember { mutableStateOf(false) }
            var dragSheetHeightPx by remember { mutableFloatStateOf(collapsedSheetHeightPx) }
            val displayedSheetHeightPx = if (isDraggingSheet) dragSheetHeightPx else sheetHeight.value
            val sheetProgress = progressForHeight(displayedSheetHeightPx)
            val sheetExpanded = sheetProgress > 0.35f

            fun snapSheetHeight(currentPx: Float, velocityPxPerSec: Float) {
                val progress = progressForHeight(currentPx)
                val target = when {
                    velocityPxPerSec < -250f -> expandedSheetHeightPx
                    velocityPxPerSec > 250f -> collapsedSheetHeightPx
                    progress >= 0.35f -> expandedSheetHeightPx
                    else -> collapsedSheetHeightPx
                }
                retainedSheetState.progress = progressForHeight(target)
                scope.launch {
                    sheetHeight.snapTo(currentPx)
                    isDraggingSheet = false
                    sheetHeight.animateTo(
                        target,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioNoBouncy,
                        ),
                    )
                }
            }

            fun snapSheet(expanded: Boolean) {
                val target = if (expanded) expandedSheetHeightPx else collapsedSheetHeightPx
                retainedSheetState.progress = if (expanded) 1f else 0f
                scope.launch {
                    if (isDraggingSheet) {
                        sheetHeight.snapTo(dragSheetHeightPx)
                        isDraggingSheet = false
                    }
                    sheetHeight.animateTo(
                        target,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioNoBouncy,
                        ),
                    )
                }
            }

            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.width(132.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clickable(onClick = onBack).semantics { contentDescription = "Back" },
                            contentAlignment = Alignment.Center,
                        ) {
                            PhoebeIconView(PhoebeIcon.ChevronDown, tint = PhoebeUi.primaryText, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    SectionLabel("Now Playing", PhoebeUi.secondaryText)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.width(132.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TransportIcon(PhoebeIcon.Lyrics, "Lyrics", onLyrics)
                        TransportIcon(PhoebeIcon.Equalizer, "Equalizer", { equalizerOpen = true }, active = equalizerProfile.enabled)
                        if (!isDesktopPlatform() || castState.isAvailable || castState.isConnected) {
                            CastIcon(
                                active = castState.isConnected,
                                loading = castState.isBuffering,
                                enabled = castState.isAvailable || castState.isConnected,
                                onClick = onCast,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .pointerInput(onBack, dismissThresholdPx, offScreenPx) {
                            detectVerticalDragGestures(
                                onDragStart = { isDraggingDismiss = true },
                                onDragEnd = {
                                    isDraggingDismiss = false
                                    if (dragOffset > dismissThresholdPx) {
                                        dismissing = true
                                    } else {
                                        dragOffset = 0f
                                    }
                                },
                                onDragCancel = {
                                    isDraggingDismiss = false
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    if (!dismissing && (dragAmount > 0f || dragOffset > 0f)) {
                                        dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                    }
                                },
                            )
                        },
                ) {
                    Spacer(Modifier.height(24.dp))
                    if (track != null) {
                        BoxWithConstraints(
                            Modifier
                                .weight(1f, fill = true)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(0.dp)),
                        ) {
                            val baseMetadataReserve = if (track.album.isNotBlank()) {
                                MobilePlayerMetadataReserveWithAlbum
                            } else {
                                MobilePlayerMetadataReserveWithoutAlbum
                            }
                            val showListenBrainzFeedback =
                                listenBrainzFeedbackTarget.available &&
                                    listenBrainzFeedbackTarget.trackId == track.id
                            val showLikeControl = likeActions.likesEnabled && track.canTogglePlexLike()
                            val showFeedbackActions = showLikeControl || showListenBrainzFeedback
                            val metadataReserve = baseMetadataReserve +
                                (if (remotePlaybackTarget != null) MobilePlayerRemoteTargetReserve else 0.dp)
                            val artworkSize = minOf(
                                maxWidth,
                                (maxHeight - metadataReserve).coerceAtLeast(180.dp),
                            )
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        translationY = -size.height * sheetProgress
                                        alpha = 1f - sheetProgress
                                    },
                            ) {
                                Box(
                                    Modifier
                                        .size(artworkSize)
                                        .align(Alignment.Start),
                                ) {
                                    val artworkLoadsEnabled = inheritedArtworkLoadingEnabled &&
                                        playerArtworkLoadingEnabled
                                    CompositionLocalProvider(
                                        LocalArtworkLoadingEnabled provides artworkLoadsEnabled,
                                    ) {
                                        SwipeableMobileArtwork(
                                            track = track,
                                            nextTrack = upNext.firstOrNull(),
                                            previousTrack = previousTrack,
                                            onSkipQueueBy = onSkipQueueBy,
                                            modifier = Modifier.fillMaxSize(),
                                            maxDecodeDimension = HeroArtworkMaxDecodeDimension,
                                        ) {
                                            AudioQualityBadge(
                                                track = track,
                                                onArtwork = true,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(12.dp),
                                            )
                                            if (showFeedbackActions) {
                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(12.dp)
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(PhoebeUi.canvasBackground.copy(alpha = 0.72f))
                                                        .padding(horizontal = 6.dp, vertical = 5.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    if (showLikeControl) {
                                                        LikeButton(
                                                            liked = likeActions.isLiked(track),
                                                            enabled = true,
                                                            onClick = { likeActions.onToggleLiked(track) },
                                                        )
                                                    }
                                                    if (showListenBrainzFeedback) {
                                                        ListenBrainzFeedbackControls(
                                                            target = listenBrainzFeedbackTarget,
                                                            onFeedback = onListenBrainzFeedback,
                                                            horizontalVotes = true,
                                                            showVoteBorders = false,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                                CompositionLocalProvider(
                                    LocalContinuousMotionEnabled provides playerMotionEnabled,
                                ) {
                                    AutoScrollingText(
                                        track.title,
                                        color = PhoebeUi.primaryText,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                    AutoScrollingText(
                                        track.artist,
                                        color = PhoebeUi.secondaryText,
                                        fontSize = 15.sp,
                                        modifier = Modifier.clickable(enabled = track.artist.isNotBlank()) {
                                            trackNavigationActions.onOpenArtistForTrack(track)
                                        },
                                    )
                                    if (track.album.isNotBlank()) {
                                        AutoScrollingText(
                                            track.album,
                                            color = PhoebeUi.mutedText,
                                            fontSize = 13.sp,
                                            modifier = Modifier.clickable {
                                                trackNavigationActions.onOpenAlbumForTrack(track)
                                            },
                                        )
                                    }
                                }
                                if (remotePlaybackTarget != null) {
                                    Text(
                                        "Music Assistant: $remotePlaybackTarget",
                                        color = PhoebeUi.accentLight,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            EmptyNowPlayingArtworkSlot(Modifier.fillMaxSize(), glyphSp = 64.sp)
                        }
                        Spacer(Modifier.height(20.dp))
                        Column {
                            Text("Nothing playing", color = PhoebeUi.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Choose a song from your library or search.",
                                color = PhoebeUi.secondaryText,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                ProgressLine(
                    positionMs = positionMs,
                    bufferedPositionMs = timelineBufferedPositionMs,
                    durationMs = track?.durationMs ?: 0L,
                    waveformSeed = track?.let(::trackWaveformSeed) ?: "",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    onSeek = if (hasTrack) onSeek else null,
                )
                Spacer(Modifier.height(22.dp))
                CompositionLocalProvider(LocalContinuousMotionEnabled provides playerMotionEnabled) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShuffleIcon(active = shuffle, onClick = onShuffle)
                        TransportIcon(PhoebeIcon.Previous, "Previous Track", onPrevious, iconSize = 16.dp)
                        PlayButton(isPlaying, isBuffering, 58.dp, onToggle, enabled = hasTrack)
                        TransportIcon(PhoebeIcon.Next, "Next Track", onNext, iconSize = 16.dp)
                        RepeatIcon(mode = repeat, onClick = onRepeat)
                    }
                }
                Spacer(Modifier.height(12.dp))

                MobileQueueSheet(
                    currentTrack = track,
                    upNext = upNext,
                    repeat = repeat,
                    sheetProgress = sheetProgress,
                    expanded = sheetExpanded,
                    isDragging = isDraggingSheet,
                    onToggleExpanded = { snapSheet(!sheetExpanded) },
                    onSheetDrag = { dragAmountPx ->
                        dragSheetHeightPx = (dragSheetHeightPx - dragAmountPx)
                            .coerceIn(collapsedSheetHeightPx, expandedSheetHeightPx)
                        retainedSheetState.progress = progressForHeight(dragSheetHeightPx)
                    },
                    onSheetDragStart = {
                        isDraggingSheet = true
                        dragSheetHeightPx = sheetHeight.value
                        scope.launch { sheetHeight.stop() }
                    },
                    onSheetDragEnd = { velocityPxPerSec ->
                        snapSheetHeight(dragSheetHeightPx, velocityPxPerSec)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { displayedSheetHeightPx.toDp() }),
                    onPlayQueue = onPlayQueue,
                    onMoveUpNext = onMoveUpNext,
                    onRemoveUpNext = onRemoveUpNext,
                    onOpenTrackDetail = onOpenSongDetail,
                    listState = upNextListState,
                )
            }
        }
    }
}

@Composable
internal fun MobileQueueSheet(
    currentTrack: Track?,
    upNext: List<Track>,
    repeat: RepeatMode,
    sheetProgress: Float,
    expanded: Boolean,
    isDragging: Boolean,
    onToggleExpanded: () -> Unit,
    onSheetDrag: (Float) -> Unit,
    onSheetDragStart: () -> Unit,
    onSheetDragEnd: (velocityPxPerSec: Float) -> Unit,
    modifier: Modifier,
    onPlayQueue: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onOpenTrackDetail: (Track) -> Unit = {},
    listState: LazyListState = RetainedLazyListStates.remember("mobile-player-up-next-list"),
) {
    val handleWidth by animateFloatAsState(
        targetValue = when {
            isDragging -> 52f
            expanded -> 44f
            else -> 36f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "queue-sheet-handle-width",
    )
    val sheetElevation by animateFloatAsState(
        targetValue = 8f + sheetProgress * 18f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "queue-sheet-elevation",
    )
    val sheetCorner by animateFloatAsState(
        targetValue = 22f + sheetProgress * 4f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "queue-sheet-corner",
    )
    val sheetShape = RoundedCornerShape(topStart = sheetCorner.dp, topEnd = sheetCorner.dp)
    val onSheetDragUpdated = rememberUpdatedState(onSheetDrag)
    val onSheetDragStartUpdated = rememberUpdatedState(onSheetDragStart)
    val onSheetDragEndUpdated = rememberUpdatedState(onSheetDragEnd)
    val draggableState = rememberDraggableState { delta ->
        onSheetDragUpdated.value(delta)
    }

    Column(
        modifier = modifier
            .shadow(sheetElevation.dp, sheetShape, clip = false)
            .clip(sheetShape)
            .background(PhoebeUi.glass.copy(alpha = 0.94f + sheetProgress * 0.04f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { onSheetDragStartUpdated.value() },
                    onDragStopped = { velocity -> onSheetDragEndUpdated.value(velocity) },
                )
                .padding(horizontal = 18.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(handleWidth.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(PhoebeUi.primaryText.copy(alpha = 0.14f + sheetProgress * 0.12f)),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Up Next", PhoebeUi.primaryText)
                if (repeat != RepeatMode.Off) {
                    Spacer(Modifier.width(8.dp))
                    RepeatBadge(mode = repeat)
                }
                Spacer(Modifier.weight(1f))
                val queueCount = upNext.size + if (currentTrack != null) 1 else 0
                Text(
                    when (queueCount) {
                        0 -> "Empty"
                        1 -> "1 track"
                        else -> "$queueCount tracks"
                    },
                    color = PhoebeUi.mutedText.copy(alpha = 0.75f + sheetProgress * 0.25f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleExpanded)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .semantics {
                            contentDescription = if (expanded) "Collapse Up Next" else "Expand Up Next"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PhoebeIconView(
                        PhoebeIcon.ChevronUp,
                        tint = PhoebeUi.mutedText.copy(alpha = 0.65f + sheetProgress * 0.35f),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = sheetProgress * 180f },
                    )
                }
            }
        }

        val showQueueContent = sheetProgress > 0.06f || isDragging
        if (showQueueContent) {
            if (currentTrack == null && upNext.isEmpty()) {
                Text(
                    "Pick a song to start a queue.",
                    color = PhoebeUi.mutedText,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 14.dp)
                        .graphicsLayer {
                            alpha = ((sheetProgress - 0.06f) / 0.2f).coerceIn(0f, 1f)
                        },
                )
            } else {
                UpNextList(
                    currentTrack = currentTrack,
                    upNext = upNext,
                    repeat = repeat,
                    onPlayQueue = onPlayQueue,
                    onMoveUpNext = onMoveUpNext,
                    onRemoveUpNext = onRemoveUpNext,
                    onOpenTrackDetail = onOpenTrackDetail,
                    listState = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 12.dp)
                        .graphicsLayer {
                            alpha = ((sheetProgress - 0.06f) / 0.2f).coerceIn(0f, 1f)
                        },
                    thumbnail = 40.dp,
                    rowHeight = 56.dp,
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
