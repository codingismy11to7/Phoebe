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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
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
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
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
import com.phoebe.app.domain.AudioAnalysisFrame
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
import com.phoebe.app.domain.NowPlayingVisualizerPreset
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
import com.phoebe.app.updates.AppUpdateState
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
internal val MobileToolbarChromeHeight = 56.dp
internal val MobileBottomNavChromeHeight = 68.dp
internal val MobileMiniPlayerChromeHeight = 72.dp
internal val PhoebeUpdateBlue = Color(0xFF3B82F6)
internal val MobileChromeScrollGap = 12.dp
private val MobilePlayerMetadataReserveWithAlbum = 104.dp
private val MobilePlayerMetadataReserveWithoutAlbum = 84.dp
private val MobilePlayerRemoteTargetReserve = 18.dp
private val MobilePlayerReflectionOverlap = 112.dp

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
    attachedToMiniPlayer: Boolean = false,
) {
    val tabs = listOf(
        BrowseSection.Home to (PhoebeIcon.Home to "Home"),
        BrowseSection.Search to (PhoebeIcon.Search to "Search"),
        BrowseSection.Library to (PhoebeIcon.Library to "Library"),
        BrowseSection.Playlists to (PhoebeIcon.PlaylistPlay to "Playlists"),
    )
    val topShape = if (attachedToMiniPlayer) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MobileBottomNavChromeHeight)
            .clip(topShape)
            .background(PhoebeUi.navBar, topShape)
            .then(if (attachedToMiniPlayer) Modifier else Modifier.border(BorderStroke(1.dp, PhoebeUi.border), topShape)),
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
    menuTint: Color = PhoebeUi.primaryText,
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
                    tint = menuTint,
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
    onJellyfinPage: (JellyfinLibraryPageKind, Int) -> Unit = { _, _ -> },
    onLibrarySortBy: (LibrarySortBy) -> Unit,
    onLibraryAscending: (Boolean) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
    onHomeSections: (List<HomeSection>) -> Unit,
    onPersonalMix: (PersonalMixPreferences) -> Unit,
    onAlbumGridItemSize: (Int) -> Unit,
    onArtistGridItemSize: (Int) -> Unit,
    onExportFavoritePlaylists: () -> Unit,
    onImportFavoritePlaylists: () -> Unit,
    appSettings: AppSettings,
    homeScreenLayoutMode: HomeScreenLayoutMode = HomeScreenLayoutMode.Default,
    onCrossfadeSeconds: (Int) -> Unit,
    onScanLibraryOnLaunch: (Boolean) -> Unit,
    onNotifyWhenDownloadFinishes: (Boolean) -> Unit,
    onPersistEqualizerSettings: (Boolean) -> Unit = {},
    onVisualizerPreset: (NowPlayingVisualizerPreset) -> Unit = {},
    onBlurredArtworkAppearance: (Boolean) -> Unit = {},
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
    appUpdateState: AppUpdateState = AppUpdateState.Idle,
    onInstallUpdate: () -> Unit = {},
    initialExpandedPhoneSection: PhoneHomeAccordionSection? = null,
    homeListState: LazyListState? = null,
    showBottomChrome: Boolean = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pickLocalFolder = rememberPickLocalFolder(onPicked = onAddLocalFolder)
    val availableUpdate = when (val updateState = appUpdateState) {
        is AppUpdateState.Available -> updateState.update
        is AppUpdateState.Installing -> updateState.update
        is AppUpdateState.Failed -> updateState.lastKnownUpdate
        else -> null
    }
    val installingUpdateState = appUpdateState as? AppUpdateState.Installing
    val updateInstalling = installingUpdateState != null
    val toolbarTitle = when {
        section == BrowseSection.Settings -> "Settings"
        selectedPlaylistId != null -> "Playlist"
        else -> mobileSectionTitle(section)
    }
    val density = LocalDensity.current
    val navigationBottomPadding = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val inheritedChromePadding = LocalMobileChromePadding.current
    val chromePadding = MobileChromePadding(
        top = MobileToolbarChromeHeight + MobileChromeScrollGap,
        bottom = if (showBottomChrome) {
            MobileBottomNavChromeHeight +
                navigationBottomPadding +
                MobileChromeScrollGap +
                if (currentTrack != null) MobileMiniPlayerChromeHeight else 0.dp
        } else {
            inheritedChromePadding.bottom
        },
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
                    onVisualizerPreset = onVisualizerPreset,
                    onBlurredArtworkAppearance = onBlurredArtworkAppearance,
                    onHomeSections = onHomeSections,
                    onPersonalMix = onPersonalMix,
                    onAlbumGridItemSize = onAlbumGridItemSize,
                    onArtistGridItemSize = onArtistGridItemSize,
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
                showMenu = availableUpdate != null || !(section == BrowseSection.Settings && selectedPlaylistId == null),
                menuTint = if (availableUpdate != null) PhoebeUpdateBlue else PhoebeUi.primaryText,
                menuContent = {
                    if (availableUpdate != null) {
                        DropdownMenuItem(
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (installingUpdateState != null) {
                                        MobileUpdateProgressRing(
                                            progress = installingUpdateState.progress,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    } else {
                                        PhoebeIconView(PhoebeIcon.Update, tint = PhoebeUpdateBlue, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        if (installingUpdateState != null) {
                                            updateMenuProgressLabel(installingUpdateState)
                                        } else {
                                            "Update to ${availableUpdate.versionName}"
                                        },
                                    )
                                }
                            },
                            onClick = {
                                if (!updateInstalling) onInstallUpdate()
                                menuExpanded = false
                            },
                            enabled = !updateInstalling,
                        )
                    }
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

    }
}

@Composable
private fun MobileUpdateProgressRing(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    if (progress == null) {
        CircularProgressIndicator(
            modifier = modifier,
            color = PhoebeUpdateBlue,
            strokeWidth = 2.dp,
            trackColor = PhoebeUpdateBlue.copy(alpha = 0.16f),
        )
    } else {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = PhoebeUpdateBlue,
            strokeWidth = 2.dp,
            trackColor = PhoebeUpdateBlue.copy(alpha = 0.16f),
        )
    }
}

private fun updateMenuProgressLabel(state: AppUpdateState.Installing): String {
    val progress = state.progress
    return if (progress != null && state.message.contains("Downloading", ignoreCase = true)) {
        "Downloading ${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
    } else {
        state.message
    }
}



@Composable
internal fun SwipeableMobileArtwork(
    track: Track,
    nextTrack: Track?,
    previousTrack: Track?,
    swipeOffset: Float,
    swipePreviewDirection: Int,
    modifier: Modifier = Modifier,
    trackContent: @Composable (Track) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .semantics {
                contentDescription = "Album artwork. Swipe left for next track, swipe right for previous track."
            },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (nextTrack != null && swipePreviewDirection < 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset((widthPx + swipeOffset).roundToInt(), 0) }
                ) {
                    trackContent(nextTrack)
                }
            }
            if (previousTrack != null && swipePreviewDirection > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset((swipeOffset - widthPx).roundToInt(), 0) }
                ) {
                    trackContent(previousTrack)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                    .graphicsLayer {
                        val dragProgress = (abs(swipeOffset) / widthPx).coerceIn(0f, 1f)
                        val scale = 1f - dragProgress * 0.03f
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                key(track.id) {
                    trackContent(track)
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
    visualizerPreset: NowPlayingVisualizerPreset = NowPlayingVisualizerPreset.Default,
    blurredArtworkAppearance: Boolean = true,
    audioAnalysis: AudioAnalysisFrame = AudioAnalysisFrame.Empty,
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
    onVisualizerPreset: (NowPlayingVisualizerPreset) -> Unit = {},
    onListenBrainzFeedback: (ListenBrainzFeedbackScore) -> Unit = {},
    onBack: () -> Unit,
    onSwipeDismiss: () -> Unit,
    handleSystemBack: Boolean = true,
    initialUpNextExpanded: Boolean = false,
    expansionFraction: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
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
    val horizontalSettleOffset = remember(track?.id) { Animatable(0f) }
    var horizontalDragOffset by remember(track?.id) { mutableFloatStateOf(0f) }
    var horizontalIsDragging by remember(track?.id) { mutableStateOf(false) }
    var horizontalSettleJob by remember(track?.id) { mutableStateOf<Job?>(null) }
    var horizontalSwipePreviewDirection by remember(track?.id) { mutableStateOf(0) }

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
        onBack = { onBack() }
    )

    val navBarColor = PhoebeUi.navBar
    val shellRadialTint = PhoebeUi.shellRadialTint
    val shellTop = PhoebeUi.shellTop
    val canvasBackground = PhoebeUi.canvasBackground
    val borderColor = PhoebeUi.border

    val cornerRadius = lerp(14.dp, 0.dp, expansionFraction)
    val containerShape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)

    BoxWithConstraints(
        modifier = modifier
            .playerDragGestures(
                expansionFraction = expansionFraction,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            )
            .clip(containerShape)
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = borderColor.alpha * (1f - expansionFraction)),
                shape = containerShape,
            )
            .drawBehind {
                drawRect(color = navBarColor.copy(alpha = 1f - expansionFraction))
                if (expansionFraction > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(shellRadialTint.copy(alpha = expansionFraction), Color.Transparent),
                            center = Offset(210f * this.density, 50f * this.density),
                            radius = 380f * this.density,
                        )
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                shellTop.copy(alpha = expansionFraction),
                                canvasBackground.copy(alpha = expansionFraction),
                            ),
                        )
                    )
                }
            }
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        val nextTrack = upNext.firstOrNull()
        val currentSwipeOffset = if (horizontalIsDragging) horizontalDragOffset else horizontalSettleOffset.value
        val swipeThresholdPx = with(density) { 56.dp.toPx() }

        val useBlurredArtworkChrome = track != null && visualizerPreset == NowPlayingVisualizerPreset.Artwork && blurredArtworkAppearance
        val bottomCorner = lerp(10.dp, 0.dp, expansionFraction)
        val artworkContentShape = if (visualizerPreset == NowPlayingVisualizerPreset.Artwork && !blurredArtworkAppearance) {
            RoundedCornerShape(10.dp)
        } else {
            RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = bottomCorner, bottomEnd = bottomCorner)
        }
        val metadataOverlap = if (useBlurredArtworkChrome) MobilePlayerReflectionOverlap else 0.dp

        fun previewDirectionFor(offsetPx: Float): Int = when {
            offsetPx < 0f && nextTrack != null -> -1
            offsetPx > 0f && previousTrack != null -> 1
            else -> 0
        }

        fun settleToCenter(fromOffset: Float) {
            horizontalSettleJob?.cancel()
            horizontalSettleJob = scope.launch {
                horizontalSettleOffset.snapTo(fromOffset)
                horizontalSettleOffset.animateTo(
                    0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessLow,
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                    ),
                )
                horizontalSwipePreviewDirection = 0
            }
        }

        fun animateSwipeCommit(releaseOffset: Float) {
            horizontalSettleJob?.cancel()
            horizontalSettleJob = scope.launch {
                val startingTrackId = track?.id
                horizontalSettleOffset.snapTo(releaseOffset)
                val screenWidthPx = with(density) { screenWidth.toPx() }
                val swipeThresholdPx = with(density) { 56.dp.toPx() }
                when {
                    releaseOffset < -swipeThresholdPx && nextTrack != null -> {
                        horizontalSettleOffset.animateTo(
                            targetValue = -screenWidthPx,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        )
                        val steps = (abs(releaseOffset) / screenWidthPx).toInt().coerceIn(1, 5)
                        onSkipQueueBy(steps)
                        delay(120L)
                        if (track?.id == startingTrackId) {
                            horizontalSettleOffset.animateTo(
                                0f,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                ),
                            )
                        }
                    }
                    releaseOffset > swipeThresholdPx && previousTrack != null -> {
                        horizontalSettleOffset.animateTo(
                            targetValue = screenWidthPx,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        )
                        val steps = -(abs(releaseOffset) / screenWidthPx).toInt().coerceIn(1, 5)
                        onSkipQueueBy(steps)
                        delay(120L)
                        if (track?.id == startingTrackId) {
                            horizontalSettleOffset.animateTo(
                                0f,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                ),
                            )
                        }
                    }
                    else -> {
                        horizontalSettleOffset.animateTo(
                            0f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                            ),
                        )
                    }
                }
                if (track?.id == startingTrackId) {
                    horizontalSwipePreviewDirection = 0
                }
            }
        }

        val horizontalDragModifier = if (track != null) {
            val screenWidthPx = with(density) { screenWidth.toPx() }
            Modifier.pointerInput(track.id, screenWidthPx, swipeThresholdPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        horizontalSettleJob?.cancel()
                        horizontalDragOffset = horizontalSettleOffset.value
                        horizontalSwipePreviewDirection = previewDirectionFor(horizontalDragOffset)
                        horizontalIsDragging = true
                        scope.launch { horizontalSettleOffset.stop() }
                    },
                    onDragEnd = {
                        val releaseOffset = horizontalDragOffset
                        horizontalSwipePreviewDirection = previewDirectionFor(releaseOffset)
                        horizontalIsDragging = false
                        horizontalDragOffset = 0f
                        animateSwipeCommit(releaseOffset)
                    },
                    onDragCancel = {
                        val releaseOffset = horizontalDragOffset
                        horizontalSwipePreviewDirection = previewDirectionFor(releaseOffset)
                        horizontalIsDragging = false
                        horizontalDragOffset = 0f
                        settleToCenter(releaseOffset)
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        horizontalDragOffset += dragAmount
                        horizontalSwipePreviewDirection = previewDirectionFor(horizontalDragOffset)
                    }
                )
            }
        } else {
            Modifier
        }

        val baseMetadataReserve = if (track != null && track.album.isNotBlank()) {
            MobilePlayerMetadataReserveWithAlbum
        } else {
            MobilePlayerMetadataReserveWithoutAlbum
        }
        val metadataReserve = baseMetadataReserve +
            (if (remotePlaybackTarget != null) MobilePlayerRemoteTargetReserve else 0.dp)
        val fullArtworkSize = minOf(
            screenWidth - 40.dp,
            (screenHeight - 56.dp - 24.dp - metadataReserve - 130.dp - 72.dp).coerceAtLeast(180.dp),
        )

        val currentArtworkSize = lerp(44.dp, fullArtworkSize, expansionFraction)
        val currentArtworkX = lerp(12.dp, 20.dp, expansionFraction)
        val currentArtworkY = lerp(14.dp, 80.dp, expansionFraction)

        val miniPlayerAlpha = (1f - expansionFraction * 3f).coerceAtLeast(0f)
        val fullPlayerAlpha = ((expansionFraction - 0.2f) * 1.25f).coerceIn(0f, 1f)
        val overlayActionsAlpha = ((expansionFraction - 0.7f) / 0.2f).coerceIn(0f, 1f)
        val fullPlayerElementsAlpha = ((expansionFraction - 0.8f) / 0.2f).coerceIn(0f, 1f)

        if (miniPlayerAlpha > 0f && track != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MobileMiniPlayerChromeHeight)
                    .graphicsLayer {
                        alpha = miniPlayerAlpha
                        if (expansionFraction < 0.1f) {
                            translationX = currentSwipeOffset
                            val swipeProgress = (abs(currentSwipeOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                            alpha = miniPlayerAlpha * (1f - swipeProgress * 0.14f)
                            val scale = 1f - swipeProgress * 0.025f
                            scaleX = scale
                            scaleY = scale
                        }
                    }
                    .then(if (expansionFraction < 0.1f) horizontalDragModifier else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = expansionFraction < 0.1f) {
                            onDragEnd(-1000f)
                        }
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    AutoScrollingText(
                        track.title,
                        color = PhoebeUi.primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    AutoScrollingText(
                        track.artist,
                        color = PhoebeUi.secondaryText,
                        fontSize = 12.sp,
                    )
                }
                PlayButton(isPlaying, isBuffering, 40.dp, onToggle, enabled = true)
            }
        }

        if (useBlurredArtworkChrome) {
            val scale = currentArtworkSize.value / fullArtworkSize.value
            val currentMetadataOverlap = lerp(0.dp, metadataOverlap, expansionFraction)
            val currentReflectionHeight = (metadataReserve + metadataOverlap) * scale
            val reflectionY = currentArtworkY + currentArtworkSize - currentMetadataOverlap

            if (currentReflectionHeight > 0.dp && fullPlayerElementsAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .offset(x = currentArtworkX, y = reflectionY)
                        .width(currentArtworkSize)
                        .height(currentReflectionHeight)
                        .graphicsLayer { alpha = fullPlayerElementsAlpha }
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = fullArtworkSize, height = metadataReserve + metadataOverlap)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    ) {
                        MobileArtworkReflection(
                            track = track,
                            artworkSize = fullArtworkSize,
                            blendOverlap = metadataOverlap,
                            rotationY = 0f,
                            backColor = PhoebeUi.panel,
                            modifier = Modifier.matchParentSize(),
                        )
                        MobileArtworkMetadataScrim(
                            blendOverlap = metadataOverlap,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }

        if (fullPlayerAlpha > 0f) {
            val collapsedSheetHeight = with(density) {
                val navBarBottom = WindowInsets.navigationBars.getBottom(this).toDp()
                88.dp + navBarBottom
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fullPlayerAlpha }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 20.dp)
                        .graphicsLayer { alpha = fullPlayerElementsAlpha },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(44.dp)
                            .clickable(onClick = { onBack() })
                            .semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.Center,
                    ) {
                        PhoebeIconView(PhoebeIcon.ChevronDown, tint = PhoebeUi.primaryText, modifier = Modifier.size(24.dp))
                    }

                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VisualizerPresetButton(
                            selected = visualizerPreset,
                            onSelected = onVisualizerPreset,
                        )
                        SectionLabel("Now Playing", PhoebeUi.secondaryText)
                        Spacer(Modifier.width(44.dp))
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                ) {
                    Spacer(Modifier.height(24.dp))
                    if (track != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(fullArtworkSize + metadataReserve)
                        ) {
                            val metadataOverlap = if (visualizerPreset == NowPlayingVisualizerPreset.Artwork && blurredArtworkAppearance) {
                                MobilePlayerReflectionOverlap
                            } else {
                                0.dp
                            }
                            val metadataUsesArtworkChrome = visualizerPreset == NowPlayingVisualizerPreset.Artwork && blurredArtworkAppearance
                            val metadataTitleColor = if (metadataUsesArtworkChrome) Color.White else PhoebeUi.primaryText
                            val metadataArtistColor = if (metadataUsesArtworkChrome) Color.White.copy(alpha = 0.82f) else PhoebeUi.secondaryText
                            val metadataAlbumColor = if (metadataUsesArtworkChrome) Color.White.copy(alpha = 0.65f) else PhoebeUi.mutedText

                            Box(
                                modifier = Modifier
                                    .width(fullArtworkSize)
                                    .height(metadataReserve + metadataOverlap)
                                    .align(Alignment.BottomStart)
                                    .graphicsLayer { alpha = fullPlayerElementsAlpha }
                            ) {
                                if (visualizerPreset != NowPlayingVisualizerPreset.Artwork) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(PhoebeUi.panel.copy(alpha = 0.85f))
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(top = metadataOverlap + 12.dp, bottom = 12.dp)
                                ) {
                                    AutoScrollingText(
                                        track.title,
                                        color = metadataTitleColor,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                    AutoScrollingText(
                                        track.artist,
                                        color = metadataArtistColor,
                                        fontSize = 14.sp,
                                        modifier = Modifier.clickable(enabled = track.artist.isNotBlank()) {
                                            trackNavigationActions.onOpenArtistForTrack(track)
                                        },
                                    )
                                    if (track.album.isNotBlank()) {
                                        AutoScrollingText(
                                            track.album,
                                            color = metadataAlbumColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier.clickable {
                                                trackNavigationActions.onOpenAlbumForTrack(track)
                                            },
                                        )
                                    }
                                    if (remotePlaybackTarget != null) {
                                        Text(
                                            "Music Assistant: $remotePlaybackTarget",
                                            color = PhoebeUi.accentLight,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .graphicsLayer { alpha = fullPlayerElementsAlpha },
                    onSeek = if (track != null) onSeek else null,
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .graphicsLayer { alpha = fullPlayerElementsAlpha },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShuffleIcon(active = shuffle, onClick = onShuffle)
                    TransportIcon(PhoebeIcon.Previous, "Previous Track", onPrevious, iconSize = 16.dp)
                    PlayButton(isPlaying, isBuffering, 58.dp, onToggle, enabled = track != null)
                    TransportIcon(PhoebeIcon.Next, "Next Track", onNext, iconSize = 16.dp)
                    RepeatIcon(mode = repeat, onClick = onRepeat)
                }
                Spacer(Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(collapsedSheetHeight))
            }
        }

        if (track != null) {
            Box(
                modifier = Modifier
                    .offset(x = currentArtworkX, y = currentArtworkY)
                    .size(currentArtworkSize)
                    .clip(artworkContentShape)
                    .then(if (expansionFraction > 0.8f) horizontalDragModifier else Modifier)
            ) {
                SwipeableMobileArtwork(
                    track = track,
                    nextTrack = nextTrack,
                    previousTrack = previousTrack,
                    swipeOffset = currentSwipeOffset,
                    swipePreviewDirection = horizontalSwipePreviewDirection,
                    modifier = Modifier.fillMaxSize(),
                ) { t ->
                    var artworkFlipRotation by remember(t.id) { mutableFloatStateOf(0f) }
                    if (visualizerPreset == NowPlayingVisualizerPreset.Artwork) {
                        val artworkLoadsEnabled = LocalArtworkLoadingEnabled.current
                        CompositionLocalProvider(
                            LocalArtworkLoadingEnabled provides artworkLoadsEnabled,
                        ) {
                            val artworkFadeHeight = if (artworkFlipRotation > 90f) {
                                0.dp
                            } else {
                                lerp(0.dp, metadataOverlap, expansionFraction)
                            }
                            FlippableSongArtwork(
                                track = t,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .mobileArtworkBottomFade(artworkFadeHeight),
                                maxDecodeDimension = HeroArtworkMaxDecodeDimension,
                                shape = artworkContentShape,
                                onFlipRotationChange = { artworkFlipRotation = it },
                            ) {
                                val showFeedbackActions = (likeActions.likesEnabled && t.canTogglePlexLike()) || (listenBrainzFeedbackTarget.available && listenBrainzFeedbackTarget.trackId == t.id)
                                val showLikeControl = likeActions.likesEnabled && t.canTogglePlexLike()
                                MobileNowPlayingOverlayActions(
                                    track = t,
                                    showAudioQualityBadge = true,
                                    showFeedbackActions = showFeedbackActions,
                                    showLikeControl = showLikeControl,
                                    likeActions = likeActions,
                                    showListenBrainzFeedback = listenBrainzFeedbackTarget.available && listenBrainzFeedbackTarget.trackId == t.id,
                                    listenBrainzFeedbackTarget = listenBrainzFeedbackTarget,
                                    onListenBrainzFeedback = onListenBrainzFeedback,
                                    alpha = overlayActionsAlpha,
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            NowPlayingVisualizerSurface(
                                preset = visualizerPreset,
                                track = t,
                                audioAnalysis = audioAnalysis,
                                isPlaying = isPlaying,
                                positionMs = positionMs,
                                modifier = Modifier.fillMaxSize(),
                            )
                            val showFeedbackActions = (likeActions.likesEnabled && t.canTogglePlexLike()) || (listenBrainzFeedbackTarget.available && listenBrainzFeedbackTarget.trackId == t.id)
                            val showLikeControl = likeActions.likesEnabled && t.canTogglePlexLike()
                            MobileNowPlayingOverlayActions(
                                track = t,
                                showAudioQualityBadge = false,
                                showFeedbackActions = showFeedbackActions,
                                showLikeControl = showLikeControl,
                                likeActions = likeActions,
                                showListenBrainzFeedback = listenBrainzFeedbackTarget.available && listenBrainzFeedbackTarget.trackId == t.id,
                                listenBrainzFeedbackTarget = listenBrainzFeedbackTarget,
                                onListenBrainzFeedback = onListenBrainzFeedback,
                                alpha = overlayActionsAlpha,
                            )
                        }
                    }
                }
            }

            if (fullPlayerAlpha > 0f) {
                val collapsedSheetHeightPx = with(density) {
                    val navBarBottom = WindowInsets.navigationBars.getBottom(this).toDp()
                    (88.dp + navBarBottom).toPx()
                }
                val expandedSheetHeightPx = with(density) {
                    val controlsPx = 130.dp.toPx()
                    val headerPx = 56.dp.toPx()
                    (screenHeight.toPx() - controlsPx - headerPx)
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
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(with(density) { displayedSheetHeightPx.toDp() })
                        .graphicsLayer { alpha = fullPlayerElementsAlpha },
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

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

private fun lerp(start: Dp, stop: Dp, fraction: Float): Dp {
    return Dp(start.value + fraction * (stop.value - start.value))
}

private fun Modifier.playerDragGestures(
    expansionFraction: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
): Modifier = this.pointerInput(Unit) {
    var velocityTracker = VelocityTracker()
    var accumulatedY = 0f
    detectVerticalDragGestures(
        onDragStart = { _ ->
            velocityTracker = VelocityTracker()
            accumulatedY = 0f
            onDragStart()
        },
        onDragEnd = {
            val velocity = velocityTracker.calculateVelocity().y
            onDragEnd(velocity)
        },
        onDragCancel = {
            onDragEnd(0f)
        },
        onVerticalDrag = { change, dragAmount ->
            change.consume()
            accumulatedY += dragAmount
            velocityTracker.addPosition(change.uptimeMillis, Offset(0f, accumulatedY))
            onDrag(dragAmount)
        }
    )
}

@Composable
private fun MobileArtworkReflection(
    track: Track,
    artworkSize: Dp,
    blendOverlap: Dp,
    rotationY: Float,
    backColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clipToBounds()) {
        MobileArtworkReflectionLayer(
            track = track,
            artworkSize = artworkSize,
            blendOverlap = blendOverlap,
            rotationY = rotationY,
            backColor = backColor,
        )
    }
}

private fun Modifier.mobileArtworkBottomFade(fadeHeight: Dp): Modifier {
    if (fadeHeight <= 0.dp) return this
    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fadeStart = (1f - fadeHeight.toPx() / size.height).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White,
                        (fadeStart * 0.92f).coerceIn(0f, 1f) to Color.White,
                        fadeStart to Color.White.copy(alpha = 0.96f),
                        (fadeStart + 0.34f).coerceAtMost(0.96f) to Color.White.copy(alpha = 0.30f),
                        1.00f to Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

@Composable
private fun MobileArtworkMetadataScrim(
    blendOverlap: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawBehind {
            val overlapStop = (blendOverlap.toPx() / size.height).coerceIn(0f, 0.72f)
            val brush = if (overlapStop > 0f) {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        (overlapStop * 0.55f) to Color.Black.copy(alpha = 0.10f),
                        overlapStop to Color.Black.copy(alpha = 0.26f),
                        1.00f to Color.Black.copy(alpha = 0.56f),
                    ),
                    startY = 0f,
                    endY = size.height,
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.25f),
                        Color.Black.copy(alpha = 0.55f),
                    ),
                    startY = 0f,
                    endY = size.height,
                )
            }
            drawRect(brush)
        },
    )
}

@Composable
private fun BoxScope.MobileArtworkReflectionLayer(
    track: Track,
    artworkSize: Dp,
    blendOverlap: Dp,
    rotationY: Float,
    backColor: Color,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val overlapStop = (blendOverlap.toPx() / size.height).coerceIn(0f, 0.72f)
                val reflectionMask = if (overlapStop > 0f) {
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            (overlapStop * 0.55f) to Color.White.copy(alpha = 0.28f),
                            overlapStop to Color.White.copy(alpha = 0.92f),
                            (overlapStop + 0.22f).coerceAtMost(0.78f) to Color.White.copy(alpha = 0.52f),
                            0.88f to Color.White.copy(alpha = 0.10f),
                            1.00f to Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height,
                    )
                } else {
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = 0.96f),
                            0.18f to Color.White.copy(alpha = 0.80f),
                            0.48f to Color.White.copy(alpha = 0.34f),
                            0.78f to Color.White.copy(alpha = 0.08f),
                            1.00f to Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height,
                    )
                }
                drawRect(
                    brush = reflectionMask,
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        val reflectionModifier = Modifier
            .size(artworkSize)
            .align(Alignment.TopCenter)
            .graphicsLayer {
                this.rotationY = rotationY
                scaleY = -1f
                cameraDistance = 12f * density.density
            }

        Box(reflectionModifier) {
            if (rotationY <= 90f) {
                TrackArtworkImage(
                    track = track,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeEffect {
                            inputScale = HazeInputScale.Auto
                            blurEffect {
                                blurRadius = 34.dp
                                progressive = HazeProgressive.verticalGradient(
                                    startIntensity = 0f,
                                    endIntensity = 1f,
                                )
                                noiseFactor = 0f
                            }
                        },
                    shape = RectangleShape,
                    elevated = false,
                    maxDecodeDimension = HeroArtworkMaxDecodeDimension,
                    alignment = Alignment.BottomCenter,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.rotationY = 180f }
                        .background(backColor),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.MobileNowPlayingOverlayActions(
    track: Track,
    showAudioQualityBadge: Boolean,
    showFeedbackActions: Boolean,
    showLikeControl: Boolean,
    likeActions: LikeActions,
    showListenBrainzFeedback: Boolean,
    listenBrainzFeedbackTarget: ListenBrainzFeedbackTarget,
    onListenBrainzFeedback: (ListenBrainzFeedbackScore) -> Unit,
    alpha: Float = 1f,
) {
    if (showAudioQualityBadge && alpha > 0f) {
        AudioQualityBadge(
            track = track,
            onArtwork = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .graphicsLayer { this.alpha = alpha },
        )
    }
    if (showFeedbackActions && alpha > 0f) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .graphicsLayer { this.alpha = alpha }
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
