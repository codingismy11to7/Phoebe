package com.phoebe.app.ui

import androidx.compose.animation.AnimatedVisibility
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

private enum class DesktopSection {
    Home,
    Search,
    Library,
    Playlists,
    Settings,
}

internal enum class PhoebeIcon {
    Home,
    Search,
    Library,
    Settings,
    Plus,
    Heart,
    ChevronUp,
    ChevronDown,
    Bell,
    Back,
    Forward,
    Music,
    Previous,
    Next,
    Play,
    Pause,
    Volume,
    Queue,
    Cast,
    Repeat,
    Drag,
    More,
    ActiveDot,
    Grid,
    Close,
}

/** When showing tracks outside Library (e.g. Search), show all optional metadata columns. */
private val FullTrackMetadataColumns = LibraryColumnVisibility(
    year = true,
    genre = true,
    filepath = true,
    audioCodec = true,
    bitrate = true,
    duration = true,
)

private data class SearchHistoryState(
    val recentSearches: List<String>,
    val commitSearch: (String) -> Unit,
    val removeSearch: (String) -> Unit,
    val clearSearches: () -> Unit,
)

private val LocalSearchHistory = compositionLocalOf {
    SearchHistoryState(
        recentSearches = emptyList(),
        commitSearch = {},
        removeSearch = {},
        clearSearches = {},
    )
}

@Composable
private fun SignInWelcomeScreen(
    message: String,
    pinCode: String?,
    onStartSignIn: () -> Unit,
    onFinishSignIn: () -> Unit,
    showLocalFolderHint: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome", color = PhoebeUi.mutedText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
        Spacer(Modifier.height(8.dp))
        Text("Phoebe", color = PhoebeUi.primaryText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            color = PhoebeUi.secondaryText,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
                onClick = onStartSignIn,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = PhoebeUi.accent.copy(alpha = 0.22f),
                    contentColor = PhoebeUi.primaryText,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) { Text("Sign in with Plex", fontSize = 14.sp) }
            if (pinCode != null) {
                OutlinedButton(
                    onClick = onFinishSignIn,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) { Text("Finish: $pinCode", fontSize = 14.sp) }
            }
        }
        if (showLocalFolderHint) {
            Spacer(Modifier.height(28.dp))
            Text(
                "You can also expand the profile row at the bottom of the sidebar and add a local music folder.",
                color = PhoebeUi.mutedText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp),
            )
        }
    }
}

@Composable
private fun MobileSignInWelcomeScreen(
    message: String,
    pinCode: String?,
    onStartSignIn: () -> Unit,
    onFinishSignIn: () -> Unit,
    onAddLocalFolder: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var providersExpanded by remember { mutableStateOf(false) }
    val pickLocalFolder = rememberPickLocalFolder(onPicked = onAddLocalFolder)
    val lightMode = LocalPhoebePalette.current == PhoebePaletteLight

    Column(
        modifier
            .fillMaxSize()
            .background(AuthFlowBackgroundColor())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandMark(size = 34.dp)
            Text("phoebe", color = PhoebeUi.primaryText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(34.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 342.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.phoebe_icon_rounded),
                contentDescription = "Phoebe app icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "Your music.\nBeautifully played.",
            color = PhoebeUi.primaryText,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            message.ifBlank { "High-fidelity playback, rich metadata, and a listening experience that puts your music first." },
            color = PhoebeUi.secondaryText,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 350.dp),
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            WelcomeFeatureChip(PhoebeIcon.Music, "Lossless", lightMode = lightMode)
            WelcomeFeatureChip(PhoebeIcon.Library, "Local", lightMode = lightMode)
            WelcomeFeatureChip(PhoebeIcon.Settings, "Metadata", lightMode = lightMode)
        }

        Spacer(Modifier.height(28.dp))

        AnimatedVisibility(
            visible = !providersExpanded,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = tween(180)),
        ) {
            GradientActionButton(
                text = "Add media provider",
                onClick = { providersExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AnimatedVisibility(
            visible = providersExpanded,
            enter = fadeIn(animationSpec = tween(180)) + expandVertically(animationSpec = tween(240)),
            exit = fadeOut(animationSpec = tween(120)) + shrinkVertically(animationSpec = tween(180)),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProviderChoiceRow(
                    icon = PhoebeIcon.Cast,
                    title = if (pinCode == null) "Sign in with Plex" else "Finish Plex sign-in",
                    subtitle = if (pinCode == null) "Stream from your Plex music library" else "Approve code $pinCode in your browser first",
                    lightMode = lightMode,
                    onClick = {
                        if (pinCode == null) onStartSignIn() else onFinishSignIn()
                    },
                )
                ProviderChoiceRow(
                    icon = PhoebeIcon.Plus,
                    title = "Add local files",
                    subtitle = "Choose music stored on this device",
                    lightMode = lightMode,
                    onClick = { pickLocalFolder() },
                )
            }
        }
    }
}

@Composable
private fun AuthFlowBackgroundColor(): Color =
    if (LocalPhoebePalette.current == PhoebePaletteLight) Color.White else PhoebeUi.canvasBackground

@Composable
private fun WelcomeFeatureChip(icon: PhoebeIcon, label: String, lightMode: Boolean) {
    val chipBackground = if (lightMode) PhoebeUi.glass else PhoebeUi.subtleFill
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(chipBackground)
            .border(BorderStroke(1.dp, PhoebeUi.border), CircleShape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PhoebeIconView(icon, tint = PhoebeUi.accentLight, modifier = Modifier.size(15.dp))
        Text(label, color = PhoebeUi.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun GradientActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lightMode = LocalPhoebePalette.current == PhoebePaletteLight
    val shadowColor = if (lightMode) {
        PhoebeUi.accent.copy(alpha = 0.22f)
    } else {
        PhoebeUi.accent.copy(alpha = 0.32f)
    }
    Box(
        modifier
            .height(62.dp)
            .shadow(18.dp, RoundedCornerShape(18.dp), ambientColor = shadowColor, spotColor = shadowColor)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(PhoebeUi.accentLight, PhoebeUi.accent)))
            .clickable(onClick = onClick)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProviderChoiceRow(
    icon: PhoebeIcon,
    title: String,
    subtitle: String,
    lightMode: Boolean,
    onClick: () -> Unit,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val rowBackground = if (lightMode) PhoebeUi.glass else PhoebeUi.subtleFill
    val rowShadow = if (lightMode) Modifier.shadow(12.dp, rowShape, ambientColor = Color(0x14141820), spotColor = Color(0x14141820)) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowShadow)
            .clip(rowShape)
            .background(rowBackground)
            .border(BorderStroke(1.dp, PhoebeUi.border), rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(PhoebeUi.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(icon, tint = PhoebeUi.accentLight, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = PhoebeUi.primaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = PhoebeUi.secondaryText, fontSize = 12.sp, lineHeight = 16.sp)
        }
        PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.mutedText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PlexServerPickerPanel(
    servers: List<PlexServer>,
    busy: Boolean,
    onSelectServer: (PlexServer) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(AuthFlowBackgroundColor())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Choose a Plex server", color = PhoebeUi.primaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Select the server that stores your music. You need a Plex server with a music library on your account.",
            color = PhoebeUi.mutedText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        if (servers.isEmpty()) {
            Text("No servers were found for this Plex account.", color = PhoebeUi.secondaryText, fontSize = 14.sp)
            FilledTonalButton(
                onClick = onRetry,
                enabled = !busy,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = PhoebeUi.accent.copy(alpha = 0.22f),
                    contentColor = PhoebeUi.primaryText,
                ),
            ) { Text("Retry") }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(servers, key = { it.id }) { server ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !busy) { onSelectServer(server) }
                            .background(PhoebeUi.elevatedFill)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(server.name, color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(server.uri, color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Cancel sign-in") }
    }
}

@Composable
private fun PlexLibraryPickerPanel(
    libraries: List<MusicLibrary>,
    serverName: String?,
    busy: Boolean,
    onSelectLibrary: (MusicLibrary) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(AuthFlowBackgroundColor())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !busy, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                PhoebeIconView(PhoebeIcon.Back, tint = PhoebeUi.primaryText, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.weight(1f))
        }
        Text("Choose a music library", color = PhoebeUi.primaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        serverName?.let { n ->
            Text("Server: $n", color = PhoebeUi.mutedText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "Pick the Plex music library to browse in Phoebe.",
            color = PhoebeUi.mutedText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        if (libraries.isEmpty()) {
            Text("No music libraries found on this server.", color = PhoebeUi.secondaryText, fontSize = 14.sp)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(libraries, key = { it.key }) { lib ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !busy) { onSelectLibrary(lib) }
                            .background(PhoebeUi.elevatedFill)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(lib.title, color = PhoebeUi.primaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Cancel sign-in") }
    }
}

@Composable
fun PhoebeRoot(
    state: AppState,
    useLightAppearance: Boolean,
    onUseLightAppearanceChange: (Boolean) -> Unit,
) {
    val screen by state.screen.collectAsState()
    val catalog by state.catalog.collectAsState()
    val catalogRefreshing by state.catalogRefreshing.collectAsState()
    val session by state.session.collectAsState()
    val mediaSources by state.mediaSources.collectAsState()
    val player by state.player.collectAsState()
    val busy by state.busy.collectAsState()
    val message by state.message.collectAsState()
    val pin by state.pin.collectAsState()
    val servers by state.servers.collectAsState()
    val libraries by state.libraries.collectAsState()
    val libraryUi by state.libraryUi.collectAsState()
    val lastPlayedByArtist by state.lastPlayedByArtist.collectAsState()
    val lastPlayedByAlbum by state.lastPlayedByAlbum.collectAsState()
    val lastPlayedByTrack by state.lastPlayedByTrack.collectAsState()
    var browseSection by remember { mutableStateOf(DesktopSection.Home) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var recentSearches by remember { mutableStateOf(emptyList<String>()) }
    var libraryFilter by remember { mutableStateOf(LibraryFilterTab.Artists) }

    val upNext = player.upNext
    val currentTrack = player.currentTrack
    val currentIndex = player.currentIndex.takeIf { it >= 0 } ?: 0
    val catalogHasContent = catalog.artists.isNotEmpty() ||
        catalog.albums.isNotEmpty() ||
        catalog.playlists.isNotEmpty()

    val nowPlaying = remember(currentTrack?.id, player.isPlaying) {
        NowPlayingIndicatorState(trackId = currentTrack?.id, isPlaying = player.isPlaying)
    }
    val playHistory = remember(lastPlayedByArtist, lastPlayedByAlbum, lastPlayedByTrack) {
        PlayHistorySnapshot(
            byArtist = lastPlayedByArtist,
            byAlbum = lastPlayedByAlbum,
            byTrack = lastPlayedByTrack,
        )
    }
    val commitSearch: (String) -> Unit = { rawQuery ->
        val trimmed = rawQuery.trim()
        if (trimmed.isNotBlank()) {
            recentSearches = listOf(trimmed) + recentSearches.filterNot { it.equals(trimmed, ignoreCase = true) }
            recentSearches = recentSearches.take(6)
        }
    }
    val searchHistory = remember(recentSearches) {
        SearchHistoryState(
            recentSearches = recentSearches,
            commitSearch = commitSearch,
            removeSearch = { search ->
                recentSearches = recentSearches.filterNot { it.equals(search, ignoreCase = true) }
            },
            clearSearches = { recentSearches = emptyList() },
        )
    }
    LaunchedEffect(browseSection, selectedPlaylistId, searchQuery) {
        if (browseSection == DesktopSection.Search && selectedPlaylistId == null && searchQuery.isNotBlank()) {
            delay(900L)
            if (searchQuery.isNotBlank()) {
                commitSearch(searchQuery)
            }
        }
    }
    // Re-tick "now" every minute so relative timestamps in the library refresh
    // without requiring an unrelated recomposition. We also re-read the clock
    // immediately whenever the play history changes — without that nudge, a
    // brand-new play whose timestamp is newer than our cached `nowMs` would
    // briefly render as "Just now"… but only after the next 60s tick caught up.
    var nowMs by remember { mutableStateOf(currentTimeMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMs = currentTimeMs()
        }
    }
    LaunchedEffect(lastPlayedByTrack) {
        nowMs = currentTimeMs()
    }

    var createPlaylistFor by remember { mutableStateOf<List<Track>?>(null) }
    var metadataEditorTrack by remember { mutableStateOf<Track?>(null) }
    val playlistActions = remember(catalog.playlists, session) {
        val plexReady = session.supportsPlexPlaylists()
        val list = if (plexReady) catalog.playlists.filter { it.id.startsWith("plex:") } else emptyList()
        PlaylistActions(
            playlists = list,
            playlistsEnabled = plexReady,
            onAddTrackToPlaylist = { playlist, track -> state.addToPlaylist(playlist, track) },
            onCreatePlaylist = { title, initialTracks -> state.createPlaylist(title, initialTracks) },
            onRequestCreatePlaylist = { initialTracks ->
                if (session?.supportsPlexPlaylists() == true &&
                    initialTracks.none { it.isLocalMediaPlayback() || !it.isPlexLibraryTrack() }
                ) {
                    createPlaylistFor = initialTracks
                }
            },
        )
    }
    val metadataEditorActions = remember {
        MetadataEditorActions(onRequestEdit = { track -> metadataEditorTrack = track })
    }
    val dragDrop = remember { DragDropController() }

    CompositionLocalProvider(
        LocalCatalogHasContent provides catalogHasContent,
        LocalNowPlaying provides nowPlaying,
        LocalPlayHistory provides playHistory,
        LocalNowMs provides nowMs,
        LocalPlaylistActions provides playlistActions,
        LocalMetadataEditorActions provides metadataEditorActions,
        LocalDragDrop provides dragDrop,
        LocalSearchHistory provides searchHistory,
    ) {
    createPlaylistFor?.let { seedTracks ->
        CreatePlaylistDialog(
            initialTracks = seedTracks,
            onDismiss = { createPlaylistFor = null },
            onConfirm = { title ->
                state.createPlaylist(title, seedTracks)
                createPlaylistFor = null
            },
        )
    }
    // Wrap everything in a single Box so the drag-ghost overlay actually sits ON TOP of the
    // app rather than under it (CompositionLocalProvider isn't a layout, so emitting siblings
    // here results in painter order = source order, with the last one rendered last/highest).
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 900.dp
            val wideDesktop = maxWidth >= 1120.dp
            val shellModifier = if (compact) {
                Modifier
                    .fillMaxSize()
                    .background(PhoebeUi.shellTop)
            } else {
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(PhoebeUi.shellRadialTint, PhoebeUi.canvasBackground),
                            center = Offset(420f, 40f),
                            radius = 960f,
                        ),
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            }
            Box(modifier = shellModifier) {
            if (compact) {
                when (val scr = screen) {
                    is AppScreen.ServerPicker -> PlexServerPickerPanel(
                        servers = servers,
                        busy = busy,
                        onSelectServer = state::selectServer,
                        onCancel = state::signOut,
                        onRetry = state::loadServers,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AppScreen.LibraryPicker -> PlexLibraryPickerPanel(
                        libraries = libraries,
                        serverName = session?.selectedServer?.name,
                        busy = busy,
                        onSelectLibrary = state::selectLibrary,
                        onBack = state::returnToServerPicker,
                        onCancel = state::signOut,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AppScreen.SignIn -> MobileSignInWelcomeScreen(
                        message = message,
                        pinCode = pin?.code,
                        onStartSignIn = state::startPlexSignIn,
                        onFinishSignIn = state::finishPlexSignIn,
                        onAddLocalFolder = state::addLocalFolderFromUri,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is AppScreen.ArtistDetail -> ArtistDetailPanel(
                        artist = scr.artist,
                        catalog = catalog,
                        libraryUi = libraryUi,
                        modifier = Modifier.fillMaxSize(),
                        searchQuery = searchQuery,
                        onBack = state::popDetail,
                        onAlbum = { state.open(AppScreen.AlbumDetail(it)) },
                        onPlayTracks = { tracks, index ->
                            state.playTracks(tracks, index)
                            state.open(AppScreen.Player)
                        },
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onLibraryColumns = state::setLibraryColumns,
                    )
                    is AppScreen.AlbumDetail -> AlbumDetailPanel(
                        album = scr.album,
                        catalog = catalog,
                        libraryUi = libraryUi,
                        modifier = Modifier.fillMaxSize(),
                        searchQuery = searchQuery,
                        onBack = state::popDetail,
                        onPlayTracks = { tracks, index ->
                            state.playTracks(tracks, index)
                            state.open(AppScreen.Player)
                        },
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onLibraryColumns = state::setLibraryColumns,
                    )
                    is AppScreen.PlaylistDetail -> PlaylistDetailPanel(
                        playlist = scr.playlist,
                        catalog = catalog,
                        catalogRefreshing = catalogRefreshing,
                        libraryUi = libraryUi,
                        modifier = Modifier.fillMaxSize(),
                        searchQuery = searchQuery,
                        onBack = {
                            state.popDetail()
                            selectedPlaylistId = null
                        },
                        onPlayTracks = { tracks, index ->
                            state.playTracks(tracks, index)
                            state.open(AppScreen.Player)
                        },
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onLibraryColumns = state::setLibraryColumns,
                    )
                    AppScreen.Player -> MobilePlayer(
                        track = currentTrack,
                        upNext = upNext,
                        isPlaying = player.isPlaying,
                        shuffle = player.shuffle,
                        repeat = player.repeat,
                        positionMs = player.positionMs,
                        currentIndex = currentIndex,
                        onToggle = state::togglePlayPause,
                        onPrevious = state::previous,
                        onNext = state::next,
                        onShuffle = state::toggleShuffle,
                        onRepeat = state::cycleRepeat,
                        onSeek = state::seekTo,
                        onPlayQueue = state::playUpNext,
                        onMoveUpNext = state::moveUpNext,
                        onRemoveUpNext = state::removeUpNext,
                        onBack = { state.open(AppScreen.Home) },
                    )
                    AppScreen.Home -> MobileBrowseShell(
                        catalog = catalog,
                        catalogRefreshing = catalogRefreshing,
                        session = session,
                        section = browseSection,
                        selectedPlaylistId = selectedPlaylistId,
                        searchQuery = searchQuery,
                        libraryFilter = libraryFilter,
                        libraryUi = libraryUi,
                        currentTrack = currentTrack,
                        isPlaying = player.isPlaying,
                        onNavigate = {
                            state.dismissDetailsToHome()
                            browseSection = it
                            selectedPlaylistId = null
                        },
                        onSearchQuery = { newQuery ->
                            searchQuery = newQuery
                            val scopedScreen = screen
                            val scoped = scopedScreen is AppScreen.ArtistDetail ||
                                scopedScreen is AppScreen.AlbumDetail ||
                                scopedScreen is AppScreen.PlaylistDetail ||
                                selectedPlaylistId != null ||
                                browseSection == DesktopSection.Library ||
                                browseSection == DesktopSection.Playlists ||
                                browseSection == DesktopSection.Settings
                            if (!scoped && newQuery.isNotBlank()) {
                                browseSection = DesktopSection.Search
                            }
                        },
                        onLibraryFilter = { libraryFilter = it },
                        onPlaylist = { playlist ->
                            selectedPlaylistId = playlist.id
                            browseSection = DesktopSection.Playlists
                            state.open(AppScreen.PlaylistDetail(playlist))
                        },
                        onArtist = { state.open(AppScreen.ArtistDetail(it)) },
                        onAlbum = { state.open(AppScreen.AlbumDetail(it)) },
                        onPlayTracks = { tracks, index ->
                            state.playTracks(tracks, index)
                            state.open(AppScreen.Player)
                        },
                        onAddToUpNext = state::addToUpNext,
                        onDownload = state::download,
                        onOpenNowPlaying = { state.open(AppScreen.Player) },
                        onTogglePlayPause = state::togglePlayPause,
                        onSignOut = state::signOut,
                        onAddLocalFolder = state::addLocalFolderFromUri,
                        onRefreshLibrary = state::refreshCatalog,
                        onLibrarySortBy = state::setLibrarySortBy,
                        onLibraryAscending = state::setLibrarySortAscending,
                        onLibraryColumns = state::setLibraryColumns,
                        useLightAppearance = useLightAppearance,
                        onUseLightAppearanceChange = onUseLightAppearanceChange,
                    )
                }
            } else {
                DesktopPlayer(
                    screen = screen,
                    catalog = catalog,
                    catalogRefreshing = catalogRefreshing,
                    session = session,
                    mediaSources = mediaSources,
                    track = currentTrack,
                    upNext = upNext,
                    isPlaying = player.isPlaying,
                    positionMs = player.positionMs,
                    currentIndex = currentIndex,
                    section = browseSection,
                    selectedPlaylistId = selectedPlaylistId,
                    searchQuery = searchQuery,
                    libraryFilter = libraryFilter,
                    libraryUi = libraryUi,
                    appMessage = message,
                    pinCode = pin?.code,
                    shuffle = player.shuffle,
                    repeat = player.repeat,
                    volume = player.volume,
                    showQueue = wideDesktop,
                    compact = !wideDesktop,
                    busy = busy,
                    onNavigate = {
                        state.dismissDetailsToHome()
                        browseSection = it
                        selectedPlaylistId = null
                    },
                    onSearchQuery = { newQuery ->
                        searchQuery = newQuery
                        // Stay in any scoped context (playlist, detail, or library tab)
                        // and let that view filter its own contents by the query.
                        val scoped = screen is AppScreen.ArtistDetail ||
                            screen is AppScreen.AlbumDetail ||
                            screen is AppScreen.PlaylistDetail ||
                            selectedPlaylistId != null ||
                                browseSection == DesktopSection.Library ||
                                browseSection == DesktopSection.Playlists ||
                                browseSection == DesktopSection.Settings
                        if (!scoped && newQuery.isNotBlank()) {
                            browseSection = DesktopSection.Search
                        }
                    },
                    onLibraryFilter = { libraryFilter = it },
                    onPlaylist = { playlist ->
                        selectedPlaylistId = playlist.id
                        browseSection = DesktopSection.Library
                        state.open(AppScreen.PlaylistDetail(playlist))
                    },
                    onArtist = { state.open(AppScreen.ArtistDetail(it)) },
                    onAlbum = { state.open(AppScreen.AlbumDetail(it)) },
                    onPopDetail = state::popDetail,
                    onToggle = state::togglePlayPause,
                    onPrevious = state::previous,
                    onNext = state::next,
                    onShuffle = state::toggleShuffle,
                    onRepeat = state::cycleRepeat,
                    onVolume = state::setVolume,
                    onSeek = state::seekTo,
                    onPlayQueue = state::playUpNext,
                    onClearQueue = state::clearQueue,
                    onMoveUpNext = state::moveUpNext,
                    onRemoveUpNext = state::removeUpNext,
                    onPlayTracks = state::playTracks,
                    onAddToUpNext = state::addToUpNext,
                    onDownload = state::download,
                    onStartSignIn = state::startPlexSignIn,
                    onFinishSignIn = state::finishPlexSignIn,
                    onSignOut = state::signOut,
                    onAddLocalFolder = state::addLocalFolderFromUri,
                    onRemoveLocalFolder = state::removeLocalFolder,
                    onToggleLocalFolder = state::setLocalFolderEnabled,
                    onRefreshLibrary = state::refreshCatalog,
                    servers = servers,
                    libraries = libraries,
                    onSelectServer = { state.selectServer(it) },
                    onSelectLibrary = { state.selectLibrary(it) },
                    onCancelPlexSetup = { state.signOut() },
                    onBackToServerPicker = { state.returnToServerPicker() },
                    onRetryServers = { state.loadServers() },
                    onLibrarySortBy = state::setLibrarySortBy,
                    onLibraryAscending = state::setLibrarySortAscending,
                    onLibraryColumns = state::setLibraryColumns,
                    useLightAppearance = useLightAppearance,
                    onUseLightAppearanceChange = onUseLightAppearanceChange,
                )
            }
            metadataEditorTrack?.let { editing ->
                val latest = catalog.tracksByParent.values
                    .asSequence()
                    .flatten()
                    .firstOrNull { it.id == editing.id } ?: editing
                MetadataEditorOverlay(
                    track = latest,
                    compact = compact,
                    onDismiss = { metadataEditorTrack = null },
                    onSave = { update ->
                        state.updateTrackMetadata(update)
                        metadataEditorTrack = null
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = busy,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
        ) {
            val overlayInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PhoebeUi.overlayScrim)
                    .clickable(indication = null, interactionSource = overlayInteraction) {},
            ) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = PhoebeUi.accentLight,
                        strokeWidth = 3.dp,
                        trackColor = PhoebeUi.progressTrack,
                    )
                    Text("Please wait", color = PhoebeUi.primaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = message.ifBlank { "Finishing up…" },
                        color = PhoebeUi.secondaryText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                }
            }
        }
    }
    // Drag-ghost overlay — must be the LAST child of the wrapper Box so it draws above the
    // rest of the UI. Renders nothing until a drag is in flight.
    DragGhost()
    }
    }
}

@Composable
private fun DesktopPlayer(
    screen: AppScreen,
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    session: PlexSession?,
    mediaSources: MediaSourcesState,
    track: Track?,
    upNext: List<Track>,
    isPlaying: Boolean,
    positionMs: Long,
    currentIndex: Int,
    section: DesktopSection,
    selectedPlaylistId: String?,
    searchQuery: String,
    libraryFilter: LibraryFilterTab,
    libraryUi: LibraryUiPreferences,
    appMessage: String,
    pinCode: String?,
    shuffle: Boolean,
    repeat: RepeatMode,
    volume: Float,
    showQueue: Boolean,
    compact: Boolean,
    busy: Boolean,
    onNavigate: (DesktopSection) -> Unit,
    onSearchQuery: (String) -> Unit,
    onLibraryFilter: (LibraryFilterTab) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPopDetail: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onVolume: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onStartSignIn: () -> Unit,
    onFinishSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onAddLocalFolder: (String?) -> Unit,
    onRemoveLocalFolder: (String) -> Unit,
    onToggleLocalFolder: (String, Boolean) -> Unit,
    onRefreshLibrary: () -> Unit,
    servers: List<PlexServer>,
    libraries: List<MusicLibrary>,
    onSelectServer: (PlexServer) -> Unit,
    onSelectLibrary: (MusicLibrary) -> Unit,
    onCancelPlexSetup: () -> Unit,
    onBackToServerPicker: () -> Unit,
    onRetryServers: () -> Unit,
    onLibrarySortBy: (LibrarySortBy) -> Unit,
    onLibraryAscending: (Boolean) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
    useLightAppearance: Boolean,
    onUseLightAppearanceChange: (Boolean) -> Unit,
) {
    var desktopUpNextExpanded by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = Color.Transparent,
        ) {
            Box(
                Modifier.background(
                    Brush.radialGradient(
                        colors = listOf(PhoebeUi.shellRadialTint, Color.Transparent),
                        center = Offset(500f, 20f),
                        radius = 560f,
                    ),
                ).background(Brush.verticalGradient(listOf(PhoebeUi.shellTop, PhoebeUi.shellBottom)))
            ) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(
                        catalog = catalog,
                        catalogRefreshing = catalogRefreshing,
                        session = session,
                        mediaSources = mediaSources,
                        activeSection = section,
                        selectedPlaylistId = selectedPlaylistId,
                        onNavigate = onNavigate,
                        onPlaylist = onPlaylist,
                        onSignOut = onSignOut,
                        onAddLocalFolder = onAddLocalFolder,
                        onRemoveLocalFolder = onRemoveLocalFolder,
                        onToggleLocalFolder = onToggleLocalFolder,
                        onRefreshLibrary = onRefreshLibrary,
                    )
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            when (screen) {
                                is AppScreen.ServerPicker -> PlexServerPickerPanel(
                                    servers = servers,
                                    busy = busy,
                                    onSelectServer = onSelectServer,
                                    onCancel = onCancelPlexSetup,
                                    onRetry = onRetryServers,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                                is AppScreen.LibraryPicker -> PlexLibraryPickerPanel(
                                    libraries = libraries,
                                    serverName = session?.selectedServer?.name,
                                    busy = busy,
                                    onSelectLibrary = onSelectLibrary,
                                    onBack = onBackToServerPicker,
                                    onCancel = onCancelPlexSetup,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                                is AppScreen.SignIn -> SignInWelcomeScreen(
                                    message = appMessage,
                                    pinCode = pinCode,
                                    onStartSignIn = onStartSignIn,
                                    onFinishSignIn = onFinishSignIn,
                                    showLocalFolderHint = true,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                                is AppScreen.ArtistDetail -> Column(Modifier.weight(1f).fillMaxHeight()) {
                                    LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                    ArtistDetailPanel(
                                        artist = screen.artist,
                                        catalog = catalog,
                                        libraryUi = libraryUi,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        searchQuery = searchQuery,
                                        onBack = onPopDetail,
                                        onAlbum = onAlbum,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                        onLibraryColumns = onLibraryColumns,
                                    )
                                }
                                is AppScreen.AlbumDetail -> Column(Modifier.weight(1f).fillMaxHeight()) {
                                    LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                    AlbumDetailPanel(
                                        album = screen.album,
                                        catalog = catalog,
                                        libraryUi = libraryUi,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        searchQuery = searchQuery,
                                        onBack = onPopDetail,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                        onLibraryColumns = onLibraryColumns,
                                    )
                                }
                                else -> when {
                                    section == DesktopSection.Home && selectedPlaylistId == null -> MainFeature(
                                        track = track,
                                        searchQuery = searchQuery,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        onSearchQuery = onSearchQuery,
                                    )
                                    section == DesktopSection.Search && selectedPlaylistId == null -> SearchDesktopView(
                                        catalog = catalog,
                                        catalogRefreshing = catalogRefreshing,
                                        searchQuery = searchQuery,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        onSearchQuery = onSearchQuery,
                                        onArtist = onArtist,
                                        onAlbum = onAlbum,
                                        onPlayTracks = onPlayTracks,
                                        onAddToUpNext = onAddToUpNext,
                                        onDownload = onDownload,
                                    )
                                    section == DesktopSection.Library && selectedPlaylistId == null -> {
                                        Column(Modifier.weight(1f).fillMaxHeight()) {
                                            LibraryTopBar(searchQuery = searchQuery, onSearchQuery = onSearchQuery)
                                            LibraryDesktopView(
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
                                                searchQuery = searchQuery,
                                                onAddToUpNext = onAddToUpNext,
                                                onDownload = onDownload,
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                            )
                                        }
                                    }
                                    section == DesktopSection.Settings && selectedPlaylistId == null -> SettingsDesktopView(
                                        isLightMode = useLightAppearance,
                                        onLightModeChange = onUseLightAppearanceChange,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                    else -> DesktopContent(
                                        catalog = catalog,
                                        catalogRefreshing = catalogRefreshing,
                                        section = section,
                                        selectedPlaylistId = selectedPlaylistId,
                                        searchQuery = searchQuery,
                                        libraryFilter = libraryFilter,
                                        libraryUi = libraryUi,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
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
                                    )
                                }
                            }
                            val isLibrary = section == DesktopSection.Library && selectedPlaylistId == null &&
                                screen !is AppScreen.ArtistDetail && screen !is AppScreen.AlbumDetail
                            val isSearch = section == DesktopSection.Search && selectedPlaylistId == null &&
                                screen !is AppScreen.ArtistDetail && screen !is AppScreen.AlbumDetail
                            val isSettings = section == DesktopSection.Settings && selectedPlaylistId == null &&
                                screen !is AppScreen.ArtistDetail && screen !is AppScreen.AlbumDetail
                            if (showQueue && !isLibrary && !isSearch && !isSettings && desktopUpNextExpanded) {
                                QueuePanel(
                                    upNext = upNext,
                                    currentTrack = track,
                                    repeat = repeat,
                                    modifier = Modifier.width(330.dp).fillMaxHeight(),
                                    onPlayQueue = onPlayQueue,
                                    onClearQueue = onClearQueue,
                                    onMoveUpNext = onMoveUpNext,
                                    onRemoveUpNext = onRemoveUpNext,
                                )
                            }
                        }
                        DesktopTransport(
                            track = track,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            shuffle = shuffle,
                            repeat = repeat,
                            volume = volume,
                            compact = compact,
                            upNextVisible = showQueue && desktopUpNextExpanded,
                            upNextToggleEnabled = showQueue,
                            onToggle = onToggle,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onShuffle = onShuffle,
                            onRepeat = onRepeat,
                            onVolume = onVolume,
                            onSeek = onSeek,
                            onToggleUpNext = { desktopUpNextExpanded = !desktopUpNextExpanded },
                            onCast = { /* TODO: implement casting */ },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    session: PlexSession?,
    mediaSources: MediaSourcesState,
    activeSection: DesktopSection,
    selectedPlaylistId: String?,
    onNavigate: (DesktopSection) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onSignOut: () -> Unit,
    onAddLocalFolder: (String?) -> Unit,
    onRemoveLocalFolder: (String) -> Unit,
    onToggleLocalFolder: (String, Boolean) -> Unit,
    onRefreshLibrary: () -> Unit,
) {
    var profileExpanded by remember { mutableStateOf(false) }
    val pickLocalFolder = rememberPickLocalFolder(onPicked = onAddLocalFolder)
    val playlistActions = LocalPlaylistActions.current

    Column(
        modifier = Modifier
            .width(236.dp)
            .fillMaxHeight()
            .background(PhoebeUi.sidebar)
            .padding(start = 14.dp, top = 54.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrandMark(size = 28.dp)
            Text("Phoebe", color = PhoebeUi.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NavRow(PhoebeIcon.Home, "Home", activeSection == DesktopSection.Home && selectedPlaylistId == null) { onNavigate(DesktopSection.Home) }
            NavRow(PhoebeIcon.Search, "Search", activeSection == DesktopSection.Search) { onNavigate(DesktopSection.Search) }
            NavRow(PhoebeIcon.Library, "Your Library", activeSection == DesktopSection.Library && selectedPlaylistId == null) { onNavigate(DesktopSection.Library) }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(contentType = "label") { SectionLabel("Playlists", PhoebeUi.mutedText) }
            if (catalogRefreshing) {
                item(contentType = "loading") { CatalogLoadingStrip(Modifier.padding(bottom = 4.dp)) }
            }
            if (playlistActions.playlistsEnabled) {
                item(contentType = "create") {
                    PlaylistRow(
                        icon = PhoebeIcon.Plus,
                        title = "Create Playlist",
                        subtitle = null,
                        onClick = { playlistActions.onRequestCreatePlaylist(emptyList()) },
                    )
                }
            }
            items(playlistActions.playlists, key = { it.id }, contentType = { "playlist-nav" }) { playlist ->
                val controller = LocalDragDrop.current
                val isHovered = controller?.draggedTrack != null &&
                    controller.isHovering(playlist.id)
                Box(
                    modifier = Modifier
                        .playlistDropTarget(playlist)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isHovered) PhoebeUi.accentLight.copy(alpha = 0.32f) else Color.Transparent)
                        .border(
                            BorderStroke(
                                width = if (isHovered) 1.5.dp else 0.dp,
                                color = if (isHovered) PhoebeUi.accentLight else Color.Transparent,
                            ),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(2.dp),
                ) {
                    PlaylistRow(
                        icon = if (playlist.title.contains("Liked", ignoreCase = true)) PhoebeIcon.Heart else null,
                        title = playlist.title,
                        subtitle = "${playlist.trackCount} songs",
                        thumbUrl = playlist.thumbUrl,
                        accent = playlist.title.contains("Liked", ignoreCase = true),
                        active = playlist.id == selectedPlaylistId,
                        onClick = { onPlaylist(playlist) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { profileExpanded = !profileExpanded }
                    .background(PhoebeUi.subtleFill)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF3876C8), Color(0xFFB87C5C)))))
                Column(Modifier.weight(1f)) {
                    Text(session?.userName ?: "Guest", color = PhoebeUi.secondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (session?.token?.isNotBlank() == true) "Plex signed in" else "Not signed in",
                        color = PhoebeUi.mutedText,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                PhoebeIconView(
                    if (profileExpanded) PhoebeIcon.ChevronUp else PhoebeIcon.ChevronDown,
                    tint = PhoebeUi.mutedText,
                    modifier = Modifier.size(14.dp),
                )
            }

            AnimatedVisibility(visible = profileExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                profileExpanded = false
                                onNavigate(DesktopSection.Settings)
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PhoebeIconView(PhoebeIcon.Settings, tint = PhoebeUi.secondaryText, modifier = Modifier.size(18.dp))
                        Text("Settings", color = PhoebeUi.primaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    session?.selectedServer?.name?.let { n ->
                        Text(n, color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    session?.selectedLibrary?.title?.let { t ->
                        Text(t, color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (session?.token?.isNotBlank() == true) {
                        OutlinedButton(
                            onClick = {
                                profileExpanded = false
                                onSignOut()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text("Sign out of Plex", fontSize = 11.sp) }
                    }
                    SectionLabel("Media sources", PhoebeUi.primaryText)
                    Text("Plex — streaming library", color = PhoebeUi.mutedText, fontSize = 11.sp, lineHeight = 15.sp)
                    Text("Local folders — files on this device", color = PhoebeUi.mutedText, fontSize = 11.sp, lineHeight = 15.sp)
                    mediaSources.localFolders.forEach { folder ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(folder.label, color = PhoebeUi.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (folder.enabled) "Enabled" else "Disabled", color = PhoebeUi.mutedText, fontSize = 10.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (folder.enabled) "Off" else "On",
                                    color = PhoebeUi.accentLight,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onToggleLocalFolder(folder.id, !folder.enabled) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                                Text(
                                    "Remove",
                                    color = PhoebeUi.mutedText,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onRemoveLocalFolder(folder.id) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickLocalFolder() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("Add local folder", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = onRefreshLibrary, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("Rescan", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavRow(icon: PhoebeIcon, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(if (active) PhoebeUi.elevatedFill else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            PhoebeIconView(icon, tint = if (active) PhoebeUi.accentLight else PhoebeUi.secondaryText, modifier = Modifier.size(18.dp))
        }
        Text(label, color = if (active) PhoebeUi.primaryText else PhoebeUi.secondaryText, fontSize = 14.sp)
    }
}

@Composable
private fun PlaylistRow(icon: PhoebeIcon?, title: String, subtitle: String?, thumbUrl: String? = null, accent: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(if (active) PhoebeUi.accent.copy(alpha = 0.09f) else Color.Transparent)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtworkImage(title, thumbUrl, Modifier.size(36.dp), radius = 6.dp)
            if (accent || icon != null) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (accent) Brush.linearGradient(listOf(PhoebeUi.accentLight.copy(alpha = 0.82f), Color(0xCC6D45E8))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        PhoebeIconView(icon, tint = PhoebeUi.primaryText, modifier = Modifier.size(18.dp), filled = accent)
                    }
                }
            }
        }
        Column {
            Text(title, color = PhoebeUi.secondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = PhoebeUi.mutedText, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun PhoebeIconView(
    icon: PhoebeIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Canvas(modifier) {
        val s = size.minDimension
        val strokeWidth = (s * 0.105f).coerceAtLeast(1.35f)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        fun p(x: Float, y: Float) = Offset(s * x, s * y)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)

        when (icon) {
            PhoebeIcon.Home -> {
                val roof = Path().apply {
                    moveTo(s * 0.16f, s * 0.52f)
                    lineTo(s * 0.5f, s * 0.20f)
                    lineTo(s * 0.84f, s * 0.52f)
                }
                drawPath(roof, tint, style = stroke)
                line(0.25f, 0.48f, 0.25f, 0.82f)
                line(0.75f, 0.48f, 0.75f, 0.82f)
                line(0.25f, 0.82f, 0.75f, 0.82f)
                line(0.47f, 0.82f, 0.47f, 0.62f)
                line(0.57f, 0.82f, 0.57f, 0.62f)
            }
            PhoebeIcon.Search -> {
                drawCircle(tint, radius = s * 0.25f, center = p(0.43f, 0.42f), style = stroke)
                line(0.61f, 0.61f, 0.82f, 0.82f)
            }
            PhoebeIcon.Library -> {
                line(0.22f, 0.30f, 0.78f, 0.30f)
                line(0.22f, 0.50f, 0.78f, 0.50f)
                line(0.22f, 0.70f, 0.78f, 0.70f)
            }
            PhoebeIcon.Queue -> {
                val play = Path().apply {
                    moveTo(s * 0.18f, s * 0.26f)
                    lineTo(s * 0.18f, s * 0.74f)
                    lineTo(s * 0.44f, s * 0.50f)
                    close()
                }
                drawPath(play, tint, style = androidx.compose.ui.graphics.drawscope.Fill)
                line(0.54f, 0.30f, 0.82f, 0.30f)
                line(0.54f, 0.50f, 0.82f, 0.50f)
                line(0.54f, 0.70f, 0.82f, 0.70f)
            }
            PhoebeIcon.Plus -> {
                line(0.50f, 0.20f, 0.50f, 0.80f)
                line(0.20f, 0.50f, 0.80f, 0.50f)
            }
            PhoebeIcon.Heart -> {
                val path = Path().apply {
                    moveTo(s * 0.50f, s * 0.82f)
                    cubicTo(s * 0.18f, s * 0.58f, s * 0.12f, s * 0.38f, s * 0.28f, s * 0.27f)
                    cubicTo(s * 0.39f, s * 0.19f, s * 0.48f, s * 0.25f, s * 0.50f, s * 0.35f)
                    cubicTo(s * 0.52f, s * 0.25f, s * 0.61f, s * 0.19f, s * 0.72f, s * 0.27f)
                    cubicTo(s * 0.88f, s * 0.38f, s * 0.82f, s * 0.58f, s * 0.50f, s * 0.82f)
                }
                drawPath(path, tint, style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else stroke)
            }
            PhoebeIcon.ChevronUp -> {
                line(0.25f, 0.62f, 0.50f, 0.38f)
                line(0.50f, 0.38f, 0.75f, 0.62f)
            }
            PhoebeIcon.ChevronDown -> {
                line(0.25f, 0.38f, 0.50f, 0.62f)
                line(0.50f, 0.62f, 0.75f, 0.38f)
            }
            PhoebeIcon.Bell -> {
                line(0.30f, 0.72f, 0.70f, 0.72f)
                line(0.36f, 0.72f, 0.32f, 0.40f)
                line(0.68f, 0.72f, 0.64f, 0.40f)
                drawCircle(tint, radius = s * 0.18f, center = p(0.50f, 0.40f), style = stroke)
                line(0.46f, 0.84f, 0.54f, 0.84f)
            }
            PhoebeIcon.Back -> {
                line(0.62f, 0.22f, 0.34f, 0.50f)
                line(0.34f, 0.50f, 0.62f, 0.78f)
            }
            PhoebeIcon.Forward -> {
                line(0.38f, 0.22f, 0.66f, 0.50f)
                line(0.66f, 0.50f, 0.38f, 0.78f)
            }
            PhoebeIcon.Music -> {
                line(0.62f, 0.20f, 0.62f, 0.68f)
                line(0.62f, 0.20f, 0.80f, 0.26f)
                drawCircle(tint, radius = s * 0.12f, center = p(0.46f, 0.72f), style = stroke)
                line(0.50f, 0.70f, 0.62f, 0.66f)
            }
            PhoebeIcon.Previous -> {
                line(0.22f, 0.24f, 0.22f, 0.76f)
                val path = Path().apply {
                    moveTo(s * 0.78f, s * 0.24f)
                    lineTo(s * 0.34f, s * 0.50f)
                    lineTo(s * 0.78f, s * 0.76f)
                    close()
                }
                drawPath(path, tint)
            }
            PhoebeIcon.Next -> {
                line(0.78f, 0.24f, 0.78f, 0.76f)
                val path = Path().apply {
                    moveTo(s * 0.22f, s * 0.24f)
                    lineTo(s * 0.66f, s * 0.50f)
                    lineTo(s * 0.22f, s * 0.76f)
                    close()
                }
                drawPath(path, tint)
            }
            PhoebeIcon.Play -> {
                val path = Path().apply {
                    moveTo(s * 0.34f, s * 0.22f)
                    lineTo(s * 0.76f, s * 0.50f)
                    lineTo(s * 0.34f, s * 0.78f)
                    close()
                }
                drawPath(path, tint)
            }
            PhoebeIcon.Pause -> {
                drawRoundRect(tint, topLeft = Offset(s * 0.32f, s * 0.22f), size = Size(s * 0.12f, s * 0.56f), cornerRadius = CornerRadius(s * 0.04f, s * 0.04f))
                drawRoundRect(tint, topLeft = Offset(s * 0.56f, s * 0.22f), size = Size(s * 0.12f, s * 0.56f), cornerRadius = CornerRadius(s * 0.04f, s * 0.04f))
            }
            PhoebeIcon.Volume -> {
                val speaker = Path().apply {
                    moveTo(s * 0.18f, s * 0.42f)
                    lineTo(s * 0.34f, s * 0.42f)
                    lineTo(s * 0.54f, s * 0.26f)
                    lineTo(s * 0.54f, s * 0.74f)
                    lineTo(s * 0.34f, s * 0.58f)
                    lineTo(s * 0.18f, s * 0.58f)
                    close()
                }
                drawPath(speaker, tint, style = stroke)
                line(0.66f, 0.38f, 0.74f, 0.50f)
                line(0.74f, 0.50f, 0.66f, 0.62f)
            }
            PhoebeIcon.Cast -> {
                line(0.20f, 0.28f, 0.80f, 0.28f)
                line(0.80f, 0.28f, 0.80f, 0.70f)
                line(0.20f, 0.70f, 0.80f, 0.70f)
                drawCircle(tint, radius = s * 0.025f, center = p(0.22f, 0.78f))
                drawArc(tint, startAngle = -90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(s * 0.12f, s * 0.58f), size = Size(s * 0.28f, s * 0.28f), style = stroke)
                drawArc(tint, startAngle = -90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(s * 0.02f, s * 0.48f), size = Size(s * 0.48f, s * 0.48f), style = stroke)
            }
            PhoebeIcon.Repeat -> {
                line(0.28f, 0.34f, 0.72f, 0.34f)
                line(0.72f, 0.34f, 0.62f, 0.24f)
                line(0.72f, 0.34f, 0.62f, 0.44f)
                line(0.72f, 0.66f, 0.28f, 0.66f)
                line(0.28f, 0.66f, 0.38f, 0.56f)
                line(0.28f, 0.66f, 0.38f, 0.76f)
            }
            PhoebeIcon.Drag -> {
                repeat(3) { row ->
                    drawCircle(tint, radius = s * 0.035f, center = p(0.42f, 0.32f + row * 0.18f))
                    drawCircle(tint, radius = s * 0.035f, center = p(0.58f, 0.32f + row * 0.18f))
                }
            }
            PhoebeIcon.More -> {
                drawCircle(tint, radius = s * 0.045f, center = p(0.28f, 0.50f))
                drawCircle(tint, radius = s * 0.045f, center = p(0.50f, 0.50f))
                drawCircle(tint, radius = s * 0.045f, center = p(0.72f, 0.50f))
            }
            PhoebeIcon.ActiveDot -> {
                drawCircle(tint, radius = s * 0.22f, center = p(0.50f, 0.50f))
            }
            PhoebeIcon.Grid -> {
                val cell = s * 0.22f
                listOf(0.24f to 0.24f, 0.54f to 0.24f, 0.24f to 0.54f, 0.54f to 0.54f).forEach { (x, y) ->
                    drawRoundRect(
                        tint,
                        topLeft = Offset(s * x, s * y),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(s * 0.045f, s * 0.045f),
                        style = stroke,
                    )
                }
            }
            PhoebeIcon.Close -> {
                line(0.30f, 0.30f, 0.70f, 0.70f)
                line(0.70f, 0.30f, 0.30f, 0.70f)
            }
            PhoebeIcon.Settings -> {
                val center = p(0.5f, 0.5f)
                drawCircle(tint, radius = s * 0.14f, center = center, style = stroke)
                drawCircle(tint, radius = s * 0.30f, center = center, style = stroke)
                val r1 = s * 0.28f
                val r2 = s * 0.40f
                repeat(8) { i ->
                    val a = (kotlin.math.PI.toFloat() / 4f) * i - kotlin.math.PI.toFloat() / 8f
                    val ca = kotlin.math.cos(a.toDouble()).toFloat()
                    val sa = kotlin.math.sin(a.toDouble()).toFloat()
                    drawLine(
                        tint,
                        Offset(center.x + r1 * ca, center.y + r1 * sa),
                        Offset(center.x + r2 * ca, center.y + r2 * sa),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTopBar(searchQuery: String, onSearchQuery: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        SearchPill(searchQuery, onSearchQuery, Modifier.width(380.dp))
        Spacer(Modifier.weight(1f))
        GlassIcon(PhoebeIcon.Bell, "Notifications")
    }
}

@Composable
private fun MainFeature(track: Track?, searchQuery: String, modifier: Modifier, onSearchQuery: (String) -> Unit) {
    Column(modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassIcon(PhoebeIcon.Back, "Back")
                GlassIcon(PhoebeIcon.Forward, "Forward")
            }
            Spacer(Modifier.weight(1f))
            SearchPill(searchQuery, onSearchQuery)
            Spacer(Modifier.width(12.dp))
            GlassIcon(PhoebeIcon.Bell, "Notifications")
        }

        if (track == null) {
            HomeNothingPlayingHero()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                ArtworkImage(track.album, track.thumbUrl, Modifier.size(292.dp))
                Column(Modifier.widthIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("Now Playing", PhoebeUi.accentLight)
                    Text(track.title, color = PhoebeUi.primaryText, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black)
                    Text(track.artist.uppercase(), color = PhoebeUi.secondaryText, fontSize = 20.sp, letterSpacing = 0.05.em)
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        PhoebeIconView(PhoebeIcon.Heart, tint = PhoebeUi.accentLight, modifier = Modifier.size(30.dp), filled = true)
                        PhoebeIconView(PhoebeIcon.Queue, tint = PhoebeUi.secondaryText, modifier = Modifier.size(24.dp))
                        PhoebeIconView(PhoebeIcon.More, tint = PhoebeUi.secondaryText, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Column(Modifier.widthIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("About The Album", PhoebeUi.mutedText)
                Text(
                    buildString {
                        if (track.album.isNotBlank()) {
                            append("Notes for ")
                            append(track.album)
                            append(" will appear here when your library provides them.")
                        } else {
                            append("Album notes from your library appear here when available.")
                        }
                    },
                    color = PhoebeUi.secondaryText,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (track.durationMs > 0L) {
                        WaveformDurationBar(
                            seed = trackWaveformSeed(track),
                            durationMs = track.durationMs,
                            progress = null,
                            contentDescription = "Track length ${formatDuration(track.durationMs)}",
                            modifier = Modifier.width(132.dp).height(22.dp),
                        )
                        Text(formatDuration(track.durationMs), color = PhoebeUi.secondaryText, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeNothingPlayingHero() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        EmptyNowPlayingArtworkSlot(Modifier.size(292.dp), glyphSp = 52.sp)
        Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Now Playing", PhoebeUi.accentLight)
            Text("Nothing playing", color = PhoebeUi.primaryText, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black)
            Text(
                "When you start a track, it appears here. Use search or your library to pick something.",
                color = PhoebeUi.secondaryText,
                fontSize = 15.sp,
                lineHeight = 23.sp,
            )
        }
    }
    Column(Modifier.widthIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Listening", PhoebeUi.mutedText)
        Text(
            "The queue and transport below stay ready. Nothing is queued until you play music.",
            color = PhoebeUi.mutedText,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun EmptyNowPlayingArtworkSlot(modifier: Modifier = Modifier, glyphSp: TextUnit = 52.sp) {
    Box(
        modifier
            .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.28f))
            .clip(RoundedCornerShape(14.dp))
            .background(PhoebeUi.glass)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(PhoebeIcon.Music, tint = PhoebeUi.mutedText.copy(alpha = 0.42f), modifier = Modifier.size(glyphSp.value.dp))
    }
}

@Composable
private fun DesktopContent(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    section: DesktopSection,
    selectedPlaylistId: String?,
    searchQuery: String,
    libraryFilter: LibraryFilterTab,
    libraryUi: LibraryUiPreferences,
    modifier: Modifier,
    onSearchQuery: (String) -> Unit,
    onLibraryFilter: (LibraryFilterTab) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onLibrarySortBy: (LibrarySortBy) -> Unit,
    onLibraryAscending: (Boolean) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
    edgePadding: Dp = 36.dp,
    headlineFontSize: TextUnit = 30.sp,
    headlineLineHeight: TextUnit = 35.sp,
    searchPillModifier: Modifier = Modifier.width(270.dp),
) {
    val selectedPlaylist = catalog.playlists.firstOrNull { it.id == selectedPlaylistId }
    val playlistTracks = selectedPlaylistId?.let { catalog.tracksByParent[it].orEmpty() }.orEmpty()

    Column(
        modifier.padding(
            start = edgePadding,
            end = edgePadding,
            top = edgePadding,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(
                    when {
                        selectedPlaylist != null -> "Playlist"
                        section == DesktopSection.Search -> "Search"
                        section == DesktopSection.Library -> "Your Library"
                        section == DesktopSection.Playlists -> "Playlists"
                        section == DesktopSection.Settings -> "Settings"
                        else -> "Home"
                    },
                    PhoebeUi.accentLight,
                )
                Text(
                    selectedPlaylist?.title ?: when (section) {
                        DesktopSection.Search -> "Find your sound"
                        DesktopSection.Library -> "Albums, artists, and songs"
                        DesktopSection.Playlists -> "Your Plex playlists"
                        DesktopSection.Settings -> "Customize your listening experience"
                        DesktopSection.Home -> "Now playing"
                    },
                    color = PhoebeUi.primaryText,
                    fontSize = headlineFontSize,
                    lineHeight = headlineLineHeight,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SearchPill(searchQuery, onSearchQuery, searchPillModifier)
        }

        when {
            selectedPlaylist != null -> {
                var playlistSortBy by remember(selectedPlaylist.id) { mutableStateOf(LibrarySortBy.Name) }
                var playlistAscending by remember(selectedPlaylist.id) { mutableStateOf(true) }
                val sortedPlaylistTracks = remember(playlistTracks, playlistSortBy, playlistAscending) {
                    sortTracksForLibrary(playlistTracks, playlistSortBy, playlistAscending)
                }
                val filteredPlaylistTracks = remember(sortedPlaylistTracks, searchQuery) {
                    filterTracksByQuery(sortedPlaylistTracks, searchQuery)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailSectionToolbar(
                        sortBy = playlistSortBy,
                        sortKeys = listOf(LibrarySortBy.Name, LibrarySortBy.Album, LibrarySortBy.Year),
                        sortLabel = { key ->
                            when (key) {
                                LibrarySortBy.Album -> "Album name"
                                LibrarySortBy.Year -> "Release date"
                                else -> "Song name"
                            }
                        },
                        onSortBy = { playlistSortBy = it },
                        ascending = playlistAscending,
                        onAscending = { playlistAscending = it },
                        columns = libraryUi.columns,
                        onColumns = onLibraryColumns,
                    )
                    TrackList(
                        tracks = filteredPlaylistTracks,
                        empty = if (searchQuery.isNotBlank()) {
                            "No tracks in ${selectedPlaylist.title} match \"$searchQuery\"."
                        } else {
                            "No tracks loaded for ${selectedPlaylist.title}."
                        },
                        catalogRefreshing = catalogRefreshing,
                        onPlayTracks = onPlayTracks,
                        onAddToUpNext = onAddToUpNext,
                        onDownload = onDownload,
                        libraryColumns = libraryUi.columns,
                    )
                }
            }
            section == DesktopSection.Search -> {
                val query = searchQuery.trim()
                val allTracks = remember(catalog.tracksByParent) {
                    catalog.tracksByParent.values.asSequence().flatten().distinctBy { it.id }.toList()
                }
                val results = if (query.isBlank()) {
                    allTracks.take(8)
                } else {
                    allTracks.filter {
                        it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true) ||
                            it.album.contains(query, ignoreCase = true)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailSectionToolbar(
                        sortBy = null,
                        sortKeys = emptyList(),
                        sortLabel = { "" },
                        onSortBy = null,
                        ascending = null,
                        onAscending = null,
                        columns = libraryUi.columns,
                        onColumns = onLibraryColumns,
                    )
                    TrackList(
                        results,
                        if (query.isBlank()) "Start typing to search songs, artists, and albums." else "No matches for \"$query\".",
                        catalogRefreshing,
                        onPlayTracks,
                        onAddToUpNext,
                        onDownload,
                        libraryColumns = libraryUi.columns,
                    )
                }
            }
            section == DesktopSection.Library -> LibraryPanel(
                catalog,
                catalogRefreshing,
                libraryFilter,
                libraryUi,
                onLibraryFilter,
                onLibrarySortBy,
                onLibraryAscending,
                onLibraryColumns,
                onPlaylist,
                onArtist,
                onAlbum,
                onPlayTracks,
                onAddToUpNext,
                onDownload,
            )
            else -> {
                val firstTracks = catalog.tracksByParent.values.firstOrNull().orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailSectionToolbar(
                        sortBy = null,
                        sortKeys = emptyList(),
                        sortLabel = { "" },
                        onSortBy = null,
                        ascending = null,
                        onAscending = null,
                        columns = libraryUi.columns,
                        onColumns = onLibraryColumns,
                    )
                    TrackList(
                        firstTracks,
                        "Your library is empty.",
                        catalogRefreshing,
                        onPlayTracks,
                        onAddToUpNext,
                        onDownload,
                        libraryColumns = libraryUi.columns,
                    )
                }
            }
        }
    }
}

private data class SearchUiResults(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val topArtist: Artist?,
    val topAlbum: Album?,
    val topTrack: Track?,
)

private enum class SearchResultScope {
    Overview,
    Songs,
    Albums,
    Artists,
}

@Composable
private fun rememberSearchUiResults(catalog: CatalogSnapshot, searchQuery: String): SearchUiResults {
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

private fun <T> bestSearchMatch(items: List<T>, query: String, label: (T) -> String): T? {
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
private fun SearchDesktopView(
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
private fun SearchMobileView(
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
private fun SearchTopResultSection(
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
private fun SearchTopAlbumCard(
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
private fun SearchTopTrackCard(
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
private fun SearchTopArtistCard(
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
private fun SearchAllResultsPanel(
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
private fun SearchAllResultsHeader(title: String, count: Int, onBack: () -> Unit) {
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
private fun SearchSongsSection(
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
private fun SearchSongsHeader() {
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
private fun SearchSongResultRow(
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
private fun SearchAlbumsSection(
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
private fun SearchAlbumTile(
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
private fun SearchAllAlbumsSection(
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
private fun SearchArtistsSection(
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
private fun SearchAllArtistsSection(
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
private fun SearchArtistTile(artist: Artist, catalog: CatalogSnapshot, onArtist: (Artist) -> Unit, modifier: Modifier = Modifier) {
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
private fun SearchArtistRow(artist: Artist, catalog: CatalogSnapshot, onArtist: (Artist) -> Unit, compact: Boolean) {
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
private fun SearchRecentPanel(
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
private fun SearchSectionHeader(label: String, showSeeAll: Boolean, onSeeAll: (() -> Unit)? = null) {
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
private fun SearchPlayChip(enabled: Boolean, onClick: () -> Unit) {
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
private fun SearchRoundPlayButton(enabled: Boolean, onClick: () -> Unit) {
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
private fun SearchEmptyCard(message: String) {
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
private fun SearchOverflowMenu(
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

private fun catalogTracksForAlbum(catalog: CatalogSnapshot, album: Album): List<Track> {
    val direct = catalog.tracksByParent[album.id].orEmpty()
    if (direct.isNotEmpty()) return direct
    return catalog.tracksByParent.values
        .asSequence()
        .flatten()
        .filter { it.album.equals(album.title, ignoreCase = true) && it.artist.equals(album.artist, ignoreCase = true) }
        .distinctBy { it.id }
        .toList()
}

private fun catalogTrackCountForArtist(catalog: CatalogSnapshot, artist: Artist): Int =
    catalog.tracksByParent.values
        .asSequence()
        .flatten()
        .filter { it.artist.equals(artist.title, ignoreCase = true) }
        .distinctBy { it.id }
        .count()
        .takeIf { it > 0 }
        ?: artist.songCount

private fun songCountLabel(count: Int): String {
    val word = if (count == 1) "song" else "songs"
    return "$count $word"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistDetailPanel(
    artist: Artist,
    catalog: CatalogSnapshot,
    libraryUi: LibraryUiPreferences,
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

    BoxWithConstraints(modifier.fillMaxSize()) {
        val useTable = maxWidth >= 640.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 36.dp, end = 36.dp, top = 36.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(contentType = "artist-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    PhoebeIconView(PhoebeIcon.Back, tint = PhoebeUi.primaryText, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("Artist", PhoebeUi.accentLight)
            Text(artist.title, color = PhoebeUi.primaryText, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${albums.size} $albumWord · ${tracks.size} $songWord", color = PhoebeUi.secondaryText, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(artist.title, artist.thumbUrl, Modifier.size(120.dp))
            }
            Spacer(Modifier.height(18.dp))
            SectionLabel("Albums", PhoebeUi.primaryText)
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
            item(contentType = "artist-album-grid") {
                ArtistAlbumGrid(albums = visibleAlbums, onAlbum = onAlbum, modifier = Modifier.animateItem())
            }
        } else {
            items(visibleAlbums, key = { it.id }, contentType = { "artist-album" }) { album ->
                LibraryRow(
                    title = album.title,
                    subtitle = "${album.artist} • ${album.year ?: "Album"}",
                    seed = album.title,
                    thumbUrl = album.thumbUrl,
                    onClick = { onAlbum(album) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(contentType = "artist-songs-label") {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Songs", PhoebeUi.primaryText)
        }
        item(contentType = "artist-song-toolbar") {
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
        }
        if (visibleTracks.isEmpty() && searchQuery.isNotBlank()) {
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
                    modifier = Modifier.animateItem(),
                )
            }
        } else {
            itemsIndexed(visibleTracks, key = { _, t -> t.id }, contentType = { _, _ -> "artist-song" }) { index, track ->
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

@Composable
private fun ArtistAlbumGrid(albums: List<Album>, onAlbum: (Album) -> Unit, modifier: Modifier = Modifier) {
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
                                ArtworkImage(album.title, album.thumbUrl, Modifier.fillMaxSize())
                            }
                            Text(album.title, color = PhoebeUi.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun AlbumDetailPanel(
    album: Album,
    catalog: CatalogSnapshot,
    libraryUi: LibraryUiPreferences,
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

    BoxWithConstraints(modifier.fillMaxSize()) {
        val useTable = maxWidth >= 640.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 36.dp, end = 36.dp, top = 36.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(contentType = "album-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    PhoebeIconView(PhoebeIcon.Back, tint = PhoebeUi.primaryText, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("Album", PhoebeUi.accentLight)
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(album.title, album.thumbUrl, Modifier.size(160.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(album.title, color = PhoebeUi.primaryText, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(album.artist.uppercase(), color = PhoebeUi.secondaryText, fontSize = 14.sp, letterSpacing = 0.05.em)
                    album.year?.let { y ->
                        Text("$y", color = PhoebeUi.mutedText, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionLabel("Tracks", PhoebeUi.primaryText)
        }
        item(contentType = "album-track-toolbar") {
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
        }
        if (visibleTracks.isEmpty()) {
            item(contentType = "album-empty") {
                Text(
                    if (searchQuery.isNotBlank()) {
                        "No tracks on ${album.title} match \"$searchQuery\"."
                    } else {
                        "No tracks loaded yet."
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
                    modifier = Modifier.animateItem(),
                )
            }
        } else {
            itemsIndexed(visibleTracks, key = { _, t -> t.id }, contentType = { _, _ -> "album-track" }) { index, track ->
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetailPanel(
    playlist: Playlist,
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
    libraryUi: LibraryUiPreferences,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onLibraryColumns: (LibraryColumnVisibility) -> Unit,
) {
    val tracks = remember(catalog.tracksByParent, playlist.id) {
        catalog.tracksByParent[playlist.id].orEmpty()
    }

    var sortBy by remember(playlist.id) { mutableStateOf(LibrarySortBy.Name) }
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    PhoebeIconView(PhoebeIcon.Back, tint = PhoebeUi.primaryText, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("Playlist", PhoebeUi.accentLight)
            Text(playlist.title, color = PhoebeUi.primaryText, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackCount} songs", color = PhoebeUi.secondaryText, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            SectionLabel("Tracks", PhoebeUi.primaryText)
        }
        item(contentType = "playlist-track-toolbar") {
            DetailSectionToolbar(
                sortBy = sortBy,
                sortKeys = listOf(LibrarySortBy.Name, LibrarySortBy.Album, LibrarySortBy.Year),
                sortLabel = { key ->
                    when (key) {
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
                if (catalogRefreshing) CatalogLoadingStrip()
                Text(
                    if (searchQuery.isNotBlank()) {
                        "No tracks in this playlist match \"$searchQuery\"."
                    } else {
                        "No tracks loaded for this playlist yet."
                    },
                    color = PhoebeUi.mutedText,
                    fontSize = 15.sp,
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackList(
    tracks: List<Track>,
    empty: String,
    catalogRefreshing: Boolean,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    libraryColumns: LibraryColumnVisibility = FullTrackMetadataColumns,
) {
    if (tracks.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (catalogRefreshing) {
                CatalogLoadingStrip()
            }
            Text(empty, color = PhoebeUi.mutedText, fontSize = 15.sp)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useTable = maxWidth >= 640.dp
        LazyColumn(verticalArrangement = Arrangement.spacedBy(if (useTable) 2.dp else 10.dp)) {
            if (catalogRefreshing) {
                item(contentType = "loading") { CatalogLoadingStrip(Modifier.padding(bottom = 4.dp)) }
            }
            if (useTable) {
                item(contentType = "track-header") {
                    SongsTableHeader(libraryColumns)
                }
                itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                    SongRow(
                        track = track,
                        selected = false,
                        columns = libraryColumns,
                        onSelect = { onPlayTracks(tracks, index) },
                        onPlay = { onPlayTracks(tracks, index) },
                        onAddToUpNext = { onAddToUpNext(track) },
                        onDownload = { onDownload(track) },
                        modifier = Modifier.animateItem(),
                    )
                }
            } else {
                itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                    ContentTrackRow(
                        track = track,
                        libraryColumns = libraryColumns,
                        onPlay = { onPlayTracks(tracks, index) },
                        onAddToUpNext = { onAddToUpNext(track) },
                        onDownload = { onDownload(track) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentTrackRow(
    track: Track,
    libraryColumns: LibraryColumnVisibility = FullTrackMetadataColumns,
    onPlay: () -> Unit,
    onAddToUpNext: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cols = libraryColumns
    val techParts = buildList {
        if (cols.audioCodec && !track.audioCodec.isNullOrBlank()) add(track.audioCodec!!)
        if (cols.bitrate && track.bitrateKbps != null && track.bitrateKbps > 0) add("${track.bitrateKbps} kbps")
        if (cols.sampleRate) {
            val rate = displaySampleRateLabel(track)
            if (rate != "—") add(rate)
        }
        if (cols.fileType) {
            val ext = displayFileTypeLabel(track)
            if (ext != "—") add(ext.trimStart('.').uppercase())
        }
    }
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = nowPlaying.trackId == track.id
    Box(modifier.draggableSong(track)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(onClick = onPlay, onLongClick = { menuExpanded = true })
                .background(
                    if (isCurrent) PhoebeUi.accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.045f),
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "⠿",
                color = PhoebeUi.mutedText,
                fontSize = 15.sp,
                modifier = Modifier
                    .draggableSong(track, immediate = true)
                    .padding(horizontal = 2.dp, vertical = 6.dp),
            )
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                ArtworkImage(track.album, track.thumbUrl, Modifier.fillMaxSize())
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
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = if (isCurrent) PhoebeUi.accentLight else PhoebeUi.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${track.artist} • ${track.album}", color = PhoebeUi.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (cols.year && track.year != null) {
                    Text(track.year.toString(), color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (cols.genre && !track.genre.isNullOrBlank()) {
                    Text(track.genre!!, color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (cols.filepath && !track.filepath.isNullOrBlank()) {
                    Text(track.filepath!!, color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (techParts.isNotEmpty()) {
                    Text(techParts.joinToString(" · "), color = PhoebeUi.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (cols.duration) {
                WaveformDurationBar(
                    seed = trackWaveformSeed(track),
                    durationMs = track.durationMs,
                    progress = null,
                    contentDescription = "Duration ${formatDuration(track.durationMs)}",
                    modifier = Modifier.width(64.dp).height(16.dp),
                )
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { menuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("···", color = PhoebeUi.secondaryText, fontSize = 17.sp)
            }
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

@Composable
private fun TrackActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddToUpNext: () -> Unit,
    onDownload: () -> Unit,
    track: Track? = null,
) {
    val actions = LocalPlaylistActions.current
    val metadataEditorActions = LocalMetadataEditorActions.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (track != null) {
            DropdownMenuItem(
                text = { Text("Edit Metadata") },
                onClick = {
                    metadataEditorActions.onRequestEdit(track)
                    onDismiss()
                },
            )
        }
        DropdownMenuItem(
            text = { Text("Add to Up Next") },
            onClick = {
                onAddToUpNext()
                onDismiss()
            },
        )
        if (track != null) {
            AddToPlaylistMenuItems(
                track = track,
                actions = actions,
                onAfter = onDismiss,
            )
        }
        DropdownMenuItem(
            text = { Text("Download Song") },
            onClick = {
                onDownload()
                onDismiss()
            },
        )
    }
}

/**
 * Reusable group of [DropdownMenuItem]s for **Plex** playlists: "New playlist…" plus existing
 * playlists. No-ops (emits nothing) when [PlaylistActions.playlistsEnabled] is false or [track]
 * is not a Plex library row / is a local file.
 */
@Composable
internal fun AddToPlaylistMenuItems(
    track: Track,
    actions: PlaylistActions = LocalPlaylistActions.current,
    onAfter: () -> Unit = {},
) {
    if (!actions.playlistsEnabled) return
    if (!track.isPlexLibraryTrack() || track.isLocalMediaPlayback()) return
    var submenuExpanded by remember { mutableStateOf(false) }
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add to Playlist", modifier = Modifier.weight(1f))
                PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.mutedText, modifier = Modifier.size(14.dp))
            }
        },
        onClick = { submenuExpanded = true },
    )
    DropdownMenu(expanded = submenuExpanded, onDismissRequest = { submenuExpanded = false }) {
        DropdownMenuItem(
            text = { Text("New playlist…", color = PhoebeUi.accentLight, fontWeight = FontWeight.SemiBold) },
            onClick = {
                submenuExpanded = false
                actions.onRequestCreatePlaylist(listOf(track))
                onAfter()
            },
        )
        if (actions.playlists.isNotEmpty()) {
            actions.playlists.forEach { playlist ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${playlist.trackCount} songs",
                                color = PhoebeUi.mutedText,
                                fontSize = 11.sp,
                            )
                        }
                    },
                    onClick = {
                        submenuExpanded = false
                        actions.onAddTrackToPlaylist(playlist, track)
                        onAfter()
                    },
                )
            }
        } else {
            DropdownMenuItem(
                text = { Text("No playlists yet", color = PhoebeUi.mutedText) },
                onClick = { submenuExpanded = false },
                enabled = false,
            )
        }
    }
}

/**
 * Minimal modal to capture the title for a new playlist. Driven from anywhere that
 * pushes onto [PlaylistActions.onRequestCreatePlaylist]; we collect the title and call
 * back into [AppState.createPlaylist] with the original seed tracks.
 */
@Composable
private fun CreatePlaylistDialog(
    initialTracks: List<Track>,
    onDismiss: () -> Unit,
    onConfirm: (title: String) -> Unit,
) {
    var title by remember { mutableStateOf(defaultPlaylistName(initialTracks)) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(min = 320.dp, max = 440.dp)
                .shadow(elevation = 30.dp, shape = RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(PhoebeUi.modalSurface)
                .border(BorderStroke(1.dp, PhoebeUi.accentLight.copy(alpha = 0.18f)), RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "New Playlist",
                    color = PhoebeUi.primaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                when {
                    initialTracks.size == 1 -> Text(
                        "Adding \"${initialTracks.first().title}\" to a new playlist.",
                        color = PhoebeUi.secondaryText,
                        fontSize = 13.sp,
                    )
                    initialTracks.size > 1 -> Text(
                        "Adding ${initialTracks.size} songs to a new playlist.",
                        color = PhoebeUi.secondaryText,
                        fontSize = 13.sp,
                    )
                    else -> Text(
                        "Pick a name for your new playlist.",
                        color = PhoebeUi.secondaryText,
                        fontSize = 13.sp,
                    )
                }
                PillTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Playlist name",
                    contentDescription = "Playlist name",
                    leadingIcon = PhoebeIcon.Plus,
                    showClearButton = true,
                    clearButtonContentDescription = "Clear playlist name",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = PhoebeUi.secondaryText)
                    }
                    TextButton(
                        onClick = { if (title.isNotBlank()) onConfirm(title.trim()) },
                        enabled = title.isNotBlank(),
                    ) {
                        Text(
                            "Create",
                            color = if (title.isNotBlank()) PhoebeUi.accentLight else PhoebeUi.mutedText,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun defaultPlaylistName(initialTracks: List<Track>): String =
    when {
        initialTracks.isEmpty() -> "New Playlist"
        initialTracks.size == 1 -> initialTracks.first().title.take(40)
        else -> "New Playlist"
    }

/** Filter a list of tracks by a free-form search query against title/artist/album. */
internal fun filterTracksByQuery(tracks: List<Track>, query: String): List<Track> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return tracks
    return tracks.filter {
        it.title.contains(trimmed, ignoreCase = true) ||
            it.artist.contains(trimmed, ignoreCase = true) ||
            it.album.contains(trimmed, ignoreCase = true)
    }
}

/** Filter albums by query against album title or artist. */
internal fun filterAlbumsByQuery(albums: List<Album>, query: String): List<Album> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return albums
    return albums.filter {
        it.title.contains(trimmed, ignoreCase = true) ||
            it.artist.contains(trimmed, ignoreCase = true)
    }
}

/** Filter artists by query against their title. */
internal fun filterArtistsByQuery(artists: List<Artist>, query: String): List<Artist> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return artists
    return artists.filter { it.title.contains(trimmed, ignoreCase = true) }
}

private fun artistAlbumCountSubtitle(artist: Artist): String {
    val w = if (artist.albumCount == 1) "album" else "albums"
    return "${artist.albumCount} $w"
}

@Composable
private fun LibraryColumnDropdownRow(
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
private fun LibrarySortAndDisplayBar(
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
                                LibrarySortBy.Name -> "Name"
                                LibrarySortBy.Artist -> "Artist"
                                LibrarySortBy.Album -> "Album"
                                LibrarySortBy.Year -> "Year"
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
private fun LibraryPanel(
    catalog: CatalogSnapshot,
    catalogRefreshing: Boolean,
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
) {
    val allTracksRaw = remember(catalog.tracksByParent) {
        catalog.tracksByParent.values.asSequence().flatten().distinctBy { it.id }.toList()
    }
    val sortBy = libraryUi.sortBy
    val ascending = libraryUi.ascending
    val sortedArtists = remember(catalog.artists, catalog.albums, sortBy, ascending) {
        sortArtistsForLibrary(catalog, sortBy, ascending)
    }
    val sortedAlbums = remember(catalog.albums, sortBy, ascending) {
        sortAlbumsForLibrary(catalog.albums, sortBy, ascending)
    }
    val sortedTracks = remember(allTracksRaw, sortBy, ascending) {
        sortTracksForLibrary(allTracksRaw, sortBy, ascending)
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        when (filter) {
            LibraryFilterTab.Artists -> {
                items(sortedArtists, key = { it.id }, contentType = { "artist" }) { artist ->
                    LibraryRow(artist.title, artistAlbumCountSubtitle(artist), artist.title, artist.thumbUrl) {
                        onArtist(artist)
                    }
                }
            }
            LibraryFilterTab.Albums -> {
                items(sortedAlbums, key = { it.id }, contentType = { "album" }) { album ->
                    LibraryRow(album.title, "${album.artist} • ${album.year ?: "Album"}", album.title, album.thumbUrl) {
                        onAlbum(album)
                    }
                }
            }
            LibraryFilterTab.Songs -> {
                itemsIndexed(sortedTracks, key = { _, track -> track.id }, contentType = { _, _ -> "song" }) { index, track ->
                    ContentTrackRow(
                        track = track,
                        libraryColumns = libraryUi.columns,
                        onPlay = { onPlayTracks(sortedTracks, index) },
                        onAddToUpNext = { onAddToUpNext(track) },
                        onDownload = { onDownload(track) },
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
                LibraryRow(
                    title = playlist.title,
                    subtitle = "${playlist.trackCount} songs",
                    seed = playlist.title,
                    thumbUrl = playlist.thumbUrl,
                    onClick = { onPlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
private fun LibraryFilterToggle(selected: LibraryFilterTab, onSelected: (LibraryFilterTab) -> Unit) {
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

/** Phoebe brand mark — the foreground bird from the app icon shown beside the wordmark. */
@Composable
private fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.phoebe_bird),
        contentDescription = "Phoebe",
        modifier = modifier.size(size),
    )
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    seed: String,
    thumbUrl: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.045f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(seed, thumbUrl, Modifier.size(46.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = PhoebeUi.primaryText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = PhoebeUi.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.mutedText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun QueuePanel(
    upNext: List<Track>,
    currentTrack: Track?,
    repeat: RepeatMode,
    modifier: Modifier,
    onPlayQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
) {
    Column(modifier.padding(top = 132.dp, end = 36.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Up Next", PhoebeUi.primaryText)
            if (repeat != RepeatMode.Off) {
                Spacer(Modifier.width(8.dp))
                RepeatBadge(mode = repeat)
            }
            Spacer(Modifier.weight(1f))
            if (upNext.isNotEmpty()) {
                Text(
                    "Clear",
                    color = PhoebeUi.mutedText,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClearQueue)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
        if (currentTrack == null && upNext.isEmpty()) {
            Text("Pick a song to start a queue.", color = PhoebeUi.mutedText, fontSize = 13.sp, lineHeight = 18.sp)
        } else {
            UpNextList(
                currentTrack = currentTrack,
                upNext = upNext,
                repeat = repeat,
                onPlayQueue = onPlayQueue,
                onMoveUpNext = onMoveUpNext,
                onRemoveUpNext = onRemoveUpNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RepeatBadge(mode: RepeatMode) {
    val (label, description) = when (mode) {
        RepeatMode.One -> "1" to "Repeating current track"
        RepeatMode.All -> "All" to "Repeating queue"
        RepeatMode.Off -> return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PhoebeUi.accent.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PhoebeIconView(PhoebeIcon.Repeat, tint = PhoebeUi.accentLight, modifier = Modifier.size(10.dp))
        Text(label, color = PhoebeUi.accentLight, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em)
    }
}

/**
 * Vertical Up Next list with a non-draggable "currently playing" header row followed
 * by reorderable upcoming tracks. Used on both desktop and mobile expanded panels.
 */
@Composable
private fun UpNextList(
    currentTrack: Track?,
    upNext: List<Track>,
    repeat: RepeatMode = RepeatMode.Off,
    onPlayQueue: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: Dp = 44.dp,
    rowHeight: Dp = 60.dp,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    var draggingTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (currentTrack != null) {
            item(key = "now-playing-${currentTrack.id}", contentType = "now-playing") {
                UpNextRow(
                    track = currentTrack,
                    active = true,
                    repeatBadge = if (repeat == RepeatMode.One) "1" else null,
                    thumbnail = thumbnail,
                    rowHeight = rowHeight,
                    dragHandle = null,
                    onClick = { /* no-op, already playing */ },
                )
            }
        }
        itemsIndexed(upNext, key = { _, t -> t.id }, contentType = { _, _ -> "up-next" }) { index, track ->
            val isDragging = draggingTrackId == track.id
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, if (isDragging) dragOffsetPx.roundToInt() else 0) }
                    .zIndex(if (isDragging) 1f else 0f),
            ) {
                UpNextRow(
                    track = track,
                    active = false,
                    thumbnail = thumbnail,
                    rowHeight = rowHeight,
                    backgroundAlpha = if (isDragging) 0.22f else 0f,
                    dragHandle = {
                        Box(
                            Modifier
                                .size(36.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingTrackId = track.id
                                            dragOffsetPx = 0f
                                        },
                                        onDragEnd = {
                                            draggingTrackId = null
                                            dragOffsetPx = 0f
                                        },
                                        onDragCancel = {
                                            draggingTrackId = null
                                            dragOffsetPx = 0f
                                        },
                                        onDrag = { _, drag ->
                                            dragOffsetPx += drag.y
                                            val currentId = draggingTrackId
                                                ?: return@detectDragGestures
                                            val curIndex = upNext.indexOfFirst { it.id == currentId }
                                            if (curIndex < 0) return@detectDragGestures
                                            val shift = (dragOffsetPx / rowHeightPx).roundToInt()
                                            if (shift != 0) {
                                                val target = (curIndex + shift)
                                                    .coerceIn(0, upNext.lastIndex)
                                                if (target != curIndex) {
                                                    onMoveUpNext(curIndex, target)
                                                    dragOffsetPx -= shift * rowHeightPx
                                                }
                                            }
                                        },
                                    )
                                }
                                .semantics { contentDescription = "Reorder ${track.title}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            PhoebeIconView(PhoebeIcon.Drag, tint = PhoebeUi.mutedText, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = { onPlayQueue(index) },
                    onLongPress = { onRemoveUpNext(index) },
                )
            }
        }
        if (repeat == RepeatMode.All && (currentTrack != null || upNext.isNotEmpty())) {
            item(contentType = "repeat-all-divider") {
                RepeatAllDivider()
            }
        }
    }
}

@Composable
private fun RepeatAllDivider() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(PhoebeUi.accent.copy(alpha = 0.32f)))
        Text(
            "Loops",
            color = PhoebeUi.accentLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.10.em,
        )
        Box(Modifier.weight(1f).height(1.dp).background(PhoebeUi.accent.copy(alpha = 0.32f)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpNextRow(
    track: Track,
    active: Boolean,
    thumbnail: Dp,
    rowHeight: Dp,
    backgroundAlpha: Float = 0f,
    repeatBadge: String? = null,
    dragHandle: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) PhoebeUi.accent.copy(alpha = 0.10f)
                else if (backgroundAlpha > 0f) Color.Black.copy(alpha = backgroundAlpha)
                else Color.Transparent,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(thumbnail), contentAlignment = Alignment.Center) {
            ArtworkImage(track.album, track.thumbUrl, Modifier.fillMaxSize(), radius = 6.dp)
            if (active) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PhoebeIconView(PhoebeIcon.ActiveDot, tint = PhoebeUi.accentLight, modifier = Modifier.size(14.dp), filled = true)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    track.title,
                    color = if (active) PhoebeUi.accentLight else PhoebeUi.primaryText,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (repeatBadge != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PhoebeUi.accent.copy(alpha = 0.22f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            repeatBadge,
                            color = PhoebeUi.accentLight,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.04.em,
                        )
                    }
                }
            }
            Text(
                track.artist,
                color = PhoebeUi.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatDuration(track.durationMs),
            color = PhoebeUi.mutedText,
            fontSize = 12.sp,
        )
        if (dragHandle != null) {
            dragHandle()
        } else {
            Spacer(Modifier.width(36.dp))
        }
    }
}

@Composable
private fun DesktopTransport(
    track: Track?,
    isPlaying: Boolean,
    positionMs: Long,
    shuffle: Boolean,
    repeat: RepeatMode,
    volume: Float,
    compact: Boolean,
    upNextVisible: Boolean,
    upNextToggleEnabled: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onVolume: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleUpNext: () -> Unit,
    onCast: () -> Unit,
) {
    val hasTrack = track != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (track != null) {
            ArtworkImage(track.album, track.thumbUrl, Modifier.size(56.dp))
        } else {
            EmptyNowPlayingArtworkSlot(Modifier.size(56.dp), glyphSp = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(150.dp)) {
            Text(
                track?.title ?: "Nothing playing",
                color = PhoebeUi.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.artist?.takeIf { it.isNotBlank() } ?: "Pick a track to begin",
                color = if (hasTrack) PhoebeUi.secondaryText else PhoebeUi.mutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PhoebeIconView(
            PhoebeIcon.Heart,
            tint = if (hasTrack) PhoebeUi.accentLight else PhoebeUi.mutedText.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp),
            filled = hasTrack,
        )
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(50.dp)) {
                ShuffleIcon(active = shuffle, onClick = onShuffle)
                TransportIcon(PhoebeIcon.Previous, "Previous Track", onPrevious)
                PlayButton(isPlaying, 48.dp, onToggle, enabled = hasTrack)
                TransportIcon(PhoebeIcon.Next, "Next Track", onNext)
                RepeatIcon(mode = repeat, onClick = onRepeat)
            }
            ProgressLine(
                positionMs,
                track?.durationMs ?: 0L,
                waveformSeed = track?.let(::trackWaveformSeed) ?: "",
                Modifier.width(if (compact) 320.dp else 460.dp),
                onSeek = if (hasTrack) onSeek else null,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(40.dp),
        ) {
            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                PhoebeIconView(PhoebeIcon.Volume, tint = PhoebeUi.secondaryText, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                VolumeSlider(volume, onVolume, Modifier.width(if (compact) 84.dp else 112.dp))
            }
            UpNextToggleIcon(
                visible = upNextVisible,
                enabled = upNextToggleEnabled,
                onClick = onToggleUpNext,
            )
            CastIcon(onClick = onCast)
        }
    }
}

@Composable
private fun ShuffleIcon(active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) PhoebeUi.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (active) "Shuffle on" else "Shuffle off" },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (active) PhoebeUi.accentLight else PhoebeUi.primaryText
        Canvas(Modifier.size(20.dp)) {
            val s = size.minDimension
            val arrowHeadLen = s * 0.18f
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.085f, cap = StrokeCap.Round)
            val p1Start = Offset(s * 0.10f, s * 0.22f)
            val p1End = Offset(s * 0.85f, s * 0.78f)
            val p2Start = Offset(s * 0.10f, s * 0.78f)
            val p2End = Offset(s * 0.85f, s * 0.22f)
            drawLine(tint, p1Start, p1End, strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p2Start, p2End, strokeWidth = stroke.width, cap = StrokeCap.Round)
            // Arrowheads
            drawLine(tint, p1End, p1End + Offset(-arrowHeadLen, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p1End, p1End + Offset(0f, -arrowHeadLen), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p2End, p2End + Offset(-arrowHeadLen, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, p2End, p2End + Offset(0f, arrowHeadLen), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun RepeatIcon(mode: RepeatMode, onClick: () -> Unit) {
    val active = mode != RepeatMode.Off
    val label = when (mode) {
        RepeatMode.Off -> "Repeat off"
        RepeatMode.One -> "Repeat one"
        RepeatMode.All -> "Repeat all"
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) PhoebeUi.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            RepeatMode.Off -> PhoebeIconView(PhoebeIcon.Repeat, tint = PhoebeUi.primaryText, modifier = Modifier.size(20.dp))
            RepeatMode.One -> Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PhoebeUi.accentLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "1",
                    color = PhoebeUi.canvasBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            RepeatMode.All -> Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PhoebeUi.accentLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "All",
                    color = PhoebeUi.canvasBackground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.02.em,
                )
            }
        }
    }
}

@Composable
private fun UpNextToggleIcon(visible: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = when {
        !enabled -> PhoebeUi.mutedText.copy(alpha = 0.35f)
        visible -> PhoebeUi.accentLight
        else -> PhoebeUi.secondaryText
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (visible) PhoebeUi.accent.copy(alpha = 0.16f) else Color.Transparent)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .semantics { contentDescription = if (visible) "Hide Up Next" else "Show Up Next" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val stroke = h * 0.10f
            val barHeight = stroke
            val y1 = h * 0.28f
            val y2 = h * 0.50f
            val y3 = h * 0.72f
            val barColor = tint
            drawRect(color = barColor, topLeft = Offset(0f, y1), size = androidx.compose.ui.geometry.Size(w, barHeight))
            drawRect(color = barColor, topLeft = Offset(0f, y2), size = androidx.compose.ui.geometry.Size(w * 0.78f, barHeight))
            drawRect(color = barColor, topLeft = Offset(0f, y3), size = androidx.compose.ui.geometry.Size(w * 0.55f, barHeight))
        }
    }
}

@Composable
private fun CastIcon(onClick: () -> Unit) {
    val strokeColor = PhoebeUi.secondaryText
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Cast" },
        contentAlignment = Alignment.Center,
    ) {
        // Match the Up Next toggle's 20.dp canvas. Inside that canvas, draw the cast
        // glyph in a 14.dp-tall band that is vertically centred — this keeps the icon's
        // optical centre on the same baseline as the music note, slider, and up-next bars.
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val rectH = h * 0.70f
            val rectTop = (h - rectH) / 2f
            val rectBottom = rectTop + rectH
            val stroke = h * 0.10f
            val cornerRadius = h * 0.12f
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(0f, rectTop),
                size = androidx.compose.ui.geometry.Size(w, rectH),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            // Wifi-style arcs anchored at the bottom-left interior of the screen.
            val arcOrigin = Offset(stroke * 1.5f, rectBottom - stroke * 1.5f)
            drawCircle(color = strokeColor, radius = stroke * 0.9f, center = arcOrigin)
            val midRadius = rectH * 0.28f
            drawArc(
                color = strokeColor,
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(arcOrigin.x - midRadius, arcOrigin.y - midRadius),
                size = androidx.compose.ui.geometry.Size(midRadius * 2, midRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val outRadius = rectH * 0.5f
            drawArc(
                color = strokeColor,
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(arcOrigin.x - outRadius, arcOrigin.y - outRadius),
                size = androidx.compose.ui.geometry.Size(outRadius * 2, outRadius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun MobileCompactMainFeature(
    track: Track?,
    searchQuery: String,
    onSearchQuery: (String) -> Unit,
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
        SearchPill(searchQuery, onSearchQuery, Modifier.fillMaxWidth())
        MobileHomeHero(track, onOpenFullPlayer)
    }
}

@Composable
private fun MobileHomeHero(track: Track?, onOpenFullPlayer: () -> Unit) {
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
private fun MobileBottomNavigation(
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
private fun MobileScreenToolbar(
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

private fun mobileSectionTitle(section: DesktopSection): String = when (section) {
    DesktopSection.Home -> "Home"
    DesktopSection.Search -> "Search"
    DesktopSection.Library -> "Library"
    DesktopSection.Playlists -> "Playlists"
    DesktopSection.Settings -> "Settings"
}

@Composable
private fun MobileBrowseShell(
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
        selectedPlaylistId != null -> {
            catalog.playlists.firstOrNull { it.id == selectedPlaylistId }?.title ?: "Playlist"
        }
        else -> mobileSectionTitle(section)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(PhoebeUi.shellTop),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
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
                    searchQuery = searchQuery,
                    onSearchQuery = onSearchQuery,
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
                PlayButton(isPlaying, 40.dp, onTogglePlayPause, enabled = true)
            }
        }

        MobileBottomNavigation(section = section, onSection = onNavigate)
        }
    }
}

@Composable
private fun MobilePlayer(
    track: Track?,
    upNext: List<Track>,
    isPlaying: Boolean,
    shuffle: Boolean,
    repeat: RepeatMode,
    positionMs: Long,
    @Suppress("UNUSED_PARAMETER") currentIndex: Int,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayQueue: (Int) -> Unit,
    onMoveUpNext: (Int, Int) -> Unit,
    onRemoveUpNext: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var mobileUpNextExpanded by remember { mutableStateOf(true) }
    val hasTrack = track != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(PhoebeUi.shellRadialTint, Color.Transparent),
                    center = Offset(210f, 50f),
                    radius = 380f,
                ),
            )
            .background(Brush.verticalGradient(listOf(PhoebeUi.shellTop, PhoebeUi.canvasBackground)))
            .statusBarsPadding()
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
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                PhoebeIconView(PhoebeIcon.More, tint = PhoebeUi.primaryText, modifier = Modifier.size(22.dp))
            }
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
            PlayButton(isPlaying, 58.dp, onToggle, enabled = hasTrack)
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
private fun MobileQueueSheet(
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

@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    contentDescription: String,
    leadingIcon: PhoebeIcon,
    showClearButton: Boolean = true,
    clearButtonContentDescription: String = "Clear",
) {
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, PhoebeUi.border), CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PhoebeIconView(leadingIcon, tint = PhoebeUi.mutedText, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = PhoebeUi.primaryText, fontSize = 12.sp),
            cursorBrush = SolidColor(PhoebeUi.primaryText),
            modifier = Modifier
                .weight(1f)
                .semantics { this.contentDescription = contentDescription },
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(placeholder, color = PhoebeUi.mutedText, fontSize = 12.sp, maxLines = 1)
                }
                innerTextField()
            },
        )
        if (showClearButton && value.isNotBlank()) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") }
                    .semantics { this.contentDescription = clearButtonContentDescription },
                contentAlignment = Alignment.Center,
            ) {
                PhoebeIconView(PhoebeIcon.Close, tint = PhoebeUi.mutedText, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SearchPill(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier.width(270.dp)) {
    PillTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = "Search songs, artists, albums",
        contentDescription = "Search songs, artists, albums",
        leadingIcon = PhoebeIcon.Search,
        showClearButton = true,
        clearButtonContentDescription = "Clear search",
    )
}

@Composable
private fun GlassIcon(icon: PhoebeIcon, description: String) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(icon, tint = PhoebeUi.secondaryText, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun TransportIcon(icon: PhoebeIcon, description: String, onClick: () -> Unit = {}, active: Boolean = false) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) PhoebeUi.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(icon, tint = if (active) PhoebeUi.accentLight else PhoebeUi.primaryText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, size: Dp, onClick: () -> Unit, enabled: Boolean = true) {
    val scale by animateFloatAsState(if (isPlaying && enabled) 1f else 0.98f, spring(), label = "play-button-scale")
    val gradient = if (enabled) {
        Brush.linearGradient(listOf(PhoebeUi.accentLight, PhoebeUi.accent))
    } else {
        Brush.linearGradient(listOf(PhoebeUi.mutedText.copy(alpha = 0.28f), PhoebeUi.mutedText.copy(alpha = 0.38f)))
    }
    Box(
        Modifier
            .size(size * scale)
            .then(
                if (enabled) {
                    Modifier.shadow(18.dp, CircleShape, ambientColor = PhoebeUi.accent.copy(alpha = 0.4f), spotColor = PhoebeUi.accent.copy(alpha = 0.38f))
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(gradient)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        PhoebeIconView(
            if (isPlaying) PhoebeIcon.Pause else PhoebeIcon.Play,
            tint = if (enabled) PhoebeUi.primaryText else PhoebeUi.mutedText.copy(alpha = 0.55f),
            modifier = Modifier.size(if (size > 52.dp) 24.dp else 21.dp),
        )
    }
}

/** Stable per-track seed so wave shape differs across library even when ids are opaque or similar. */
private fun trackWaveformSeed(track: Track): String =
    "${track.id}\u0000${track.title}\u0000${track.artist}\u0000${track.album}\u0000${track.durationMs}"

private fun waveBarHeight(seed: String, index: Int): Float {
    var h = 0L
    for (c in seed) {
        h = h * 31L + c.code
    }
    var x = h xor (index.toLong() * 0x9e3779b9L)
    x = (x * 0x85ebca6bL) xor (x ushr 13)
    x = (x * 0xc2b2ae35L) xor (x ushr 16)
    var t = x xor (index.toLong() * 0x27d4eb2fL)
    t = t xor (t ushr 4)
    t *= 0xcc9e2d51L
    t = t xor (t ushr 11)
    val u = ((t ushr 8) and 0xffffL).toFloat() / 65536f
    val w = kotlin.math.sin(index * 1.17 + h * 2.1e-5 + t * 1.5e-4).toFloat()
    val w2 = kotlin.math.cos(index * 0.53 + (t and 0xffL).toDouble() * 0.11).toFloat()
    return (0.13f + 0.54f * u + 0.19f * w + 0.15f * w2).coerceIn(0.12f, 1f)
}

@Composable
private fun WaveformDurationBar(
    seed: String,
    durationMs: Long,
    progress: Float?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val p = progress?.coerceIn(0f, 1f)
    val playedColor = PhoebeUi.accentLight
    val unplayedBase = PhoebeUi.waveformUnplayed
    val playheadColor = PhoebeUi.waveformPlayhead
    Canvas(
        modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val barSlots = (size.width / (2.2f * density)).toInt().coerceIn(20, 120)
        val slotW = size.width / barSlots
        val barW = (slotW * 0.62f).coerceAtLeast(1.2f)
        val played = playedColor
        val queued = unplayedBase
        for (i in 0 until barSlots) {
            val frac = (i + 0.5f) / barSlots
            val amp = if (durationMs > 0L) waveBarHeight(seed, i) else 0.12f
            val barH = size.height * amp
            val x = i * slotW + (slotW - barW) / 2f
            val color = when {
                durationMs <= 0L -> unplayedBase.copy(alpha = (unplayedBase.alpha * 0.75f).coerceIn(0.08f, 0.5f))
                p == null -> {
                    val a = (0.12f + 0.52f * amp).coerceIn(0.12f, 0.55f)
                    unplayedBase.copy(alpha = a)
                }
                frac <= p -> played
                else -> queued
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW * 0.45f, barW * 0.45f),
            )
        }
        if (durationMs > 0L && p != null && p in 0.001f..0.999f) {
            val cx = size.width * p
            drawLine(
                color = playheadColor,
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

@Composable
private fun ProgressLine(positionMs: Long, durationMs: Long, waveformSeed: String, modifier: Modifier, onSeek: ((Long) -> Unit)? = null) {
    val safeDuration = max(durationMs, 1L)
    val progressFrac = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val seekModifier = if (onSeek != null && durationMs > 0L) {
        modifier.pointerInput(durationMs, onSeek) {
            detectTapGestures { offset ->
                val frac = (offset.x / size.width).coerceIn(0f, 1f)
                onSeek.invoke((durationMs * frac).toLong())
            }
        }
    } else {
        modifier
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WaveformDurationBar(
            seed = waveformSeed,
            durationMs = durationMs,
            progress = if (durationMs > 0L) progressFrac else null,
            contentDescription = if (durationMs > 0L) {
                "Playback progress, ${formatDuration(positionMs)} of ${formatDuration(durationMs)}"
            } else {
                "Playback progress, no duration"
            },
            modifier = seekModifier
                .fillMaxWidth()
                .height(28.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(positionMs), color = PhoebeUi.mutedText, fontSize = 12.sp)
            Text(formatDuration(durationMs), color = PhoebeUi.mutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun VolumeSlider(volume: Float, onVolume: (Float) -> Unit, modifier: Modifier) {
    Slider(
        value = volume,
        onValueChange = onVolume,
        modifier = modifier.semantics { contentDescription = "Volume" },
        colors = SliderDefaults.colors(
            thumbColor = PhoebeUi.accentLight,
            activeTrackColor = PhoebeUi.accentLight,
            inactiveTrackColor = PhoebeUi.progressTrack,
        ),
    )
}

/**
 * True once the catalog has at least some cached content. Used by [CatalogLoadingStrip] to
 * pick between the first-run "Loading your library…" message and the subsequent "Syncing…"
 * message when a background refresh is happening on top of an already-populated UI.
 */
internal val LocalCatalogHasContent = compositionLocalOf { false }

/** Current playback state, exposed implicitly so any track row can show a "now playing" badge. */
internal data class NowPlayingIndicatorState(
    val trackId: String? = null,
    val isPlaying: Boolean = false,
)

internal val LocalNowPlaying = compositionLocalOf { NowPlayingIndicatorState() }

/**
 * Snapshot of "last played" timestamps (Unix millis) keyed by artist title, album title,
 * and track id. Threaded through the composition so any row in the library / detail UI
 * can ask "when was this last heard?" without taking a hard dependency on AppState.
 */
internal data class PlayHistorySnapshot(
    val byArtist: Map<String, Long> = emptyMap(),
    val byAlbum: Map<String, Long> = emptyMap(),
    val byTrack: Map<String, Long> = emptyMap(),
)

internal val LocalPlayHistory = compositionLocalOf { PlayHistorySnapshot() }

/**
 * Current wall-clock reference used to render relative "last played" strings.
 * Updated periodically by [PhoebeRoot] so "Just now" eventually slides to "Today",
 * "Today" slides to "Yesterday", etc. without requiring a recomposition trigger
 * from elsewhere.
 */
internal val LocalNowMs = compositionLocalOf { 0L }

/**
 * Playlist mutation surface exposed via [CompositionLocal] so overflow menus / song rows /
 * sidebar drop targets can add to Plex playlists without threading callbacks everywhere.
 *
 * [playlistsEnabled] is true only when Plex is signed in with a server and music library selected.
 * [onAddTrackToPlaylist] and [onCreatePlaylist] both no-op by default.
 */
internal data class PlaylistActions(
    val playlists: List<Playlist> = emptyList(),
    /** Plex token + server + music library — required for any playlist UI or mutations. */
    val playlistsEnabled: Boolean = false,
    val onAddTrackToPlaylist: (Playlist, Track) -> Unit = { _, _ -> },
    val onCreatePlaylist: (title: String, initialTracks: List<Track>) -> Unit = { _, _ -> },
    val onRequestCreatePlaylist: (initialTracks: List<Track>) -> Unit = {},
)

internal val LocalPlaylistActions = compositionLocalOf { PlaylistActions() }

/**
 * Drag state for "drag a song row onto a sidebar playlist row to add it". Song rows update
 * this when the user picks one up; playlist rows register their on-screen bounds via
 * [DragDropController.register] and read [DragDropController.draggedTrack] to draw a
 * highlight when the pointer is hovering above them.
 */
internal class DragDropController {
    var draggedTrack by mutableStateOf<Track?>(null)
        private set
    var pointer by mutableStateOf<Offset?>(null)
        private set
    private val targets = mutableStateMapOf<String, DropTarget>()

    /** Title of the playlist row currently hovered by the drag pointer, or null. */
    val hoveringPlaylistTitle: String?
        get() = currentHover()?.title

    fun start(track: Track, initialPointer: Offset) {
        draggedTrack = track
        pointer = initialPointer
    }

    fun update(pointer: Offset) {
        this.pointer = pointer
    }

    /** Drops the dragged track, returning the playlist it landed on (if any). */
    fun end(): Playlist? {
        val hit = currentHover()
        draggedTrack = null
        pointer = null
        return hit
    }

    fun cancel() {
        draggedTrack = null
        pointer = null
    }

    fun register(playlist: Playlist, bounds: Rect) {
        targets[playlist.id] = DropTarget(playlist, bounds)
    }

    fun unregister(playlistId: String) {
        targets.remove(playlistId)
    }

    /** True when the active drag pointer is currently hovering over [playlistId]. */
    fun isHovering(playlistId: String): Boolean {
        if (draggedTrack == null) return false
        val pt = pointer ?: return false
        return targets[playlistId]?.bounds?.contains(pt) == true
    }

    private fun currentHover(): Playlist? {
        val pt = pointer ?: return null
        return targets.values.firstOrNull { it.bounds.contains(pt) }?.playlist
    }

    private data class DropTarget(val playlist: Playlist, val bounds: Rect)
}

internal val LocalDragDrop = compositionLocalOf<DragDropController?> { null }

/**
 * Marks this composable as a draggable song. When [immediate] is true the drag picks up the
 * moment the pointer moves past Compose's touch slop (used by the visible drag-handle icon
 * inside each library song row). When [immediate] is false the drag waits for a long-press
 * first, which is the right behavior for whole-row gestures on a list that also needs to
 * accept clicks and (on mobile) scroll gestures.
 *
 * Either way, [LocalDragDrop] broadcasts the pointer position so any [playlistDropTarget]
 * can receive it, and on release the dropped playlist's `onAddTrackToPlaylist` fires.
 *
 * We capture the host's `positionInRoot` via `onGloballyPositioned` so pointer offsets
 * (which arrive in this composable's local space) can be re-projected into the root window
 * — the same coordinate space the drop targets advertise.
 */
internal fun Modifier.draggableSong(
    track: Track,
    enabled: Boolean = true,
    immediate: Boolean = false,
): Modifier = composed {
    val controller = LocalDragDrop.current ?: return@composed this
    val actions = LocalPlaylistActions.current
    val allowDrag = enabled && actions.playlistsEnabled && !track.isLocalMediaPlayback() && track.isPlexLibraryTrack()
    if (!allowDrag) return@composed this
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(track.id, immediate) {
            val onStart: (Offset) -> Unit = { offset -> controller.start(track, origin + offset) }
            val onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit = { change, _ ->
                controller.update(origin + change.position)
            }
            val onEnd: () -> Unit = {
                val target = controller.end()
                if (target != null) actions.onAddTrackToPlaylist(target, track)
            }
            val onCancel: () -> Unit = { controller.cancel() }
            if (immediate) {
                detectDragGestures(
                    onDragStart = onStart,
                    onDrag = onDrag,
                    onDragEnd = onEnd,
                    onDragCancel = onCancel,
                )
            } else {
                detectDragGesturesAfterLongPress(
                    onDragStart = onStart,
                    onDrag = onDrag,
                    onDragEnd = onEnd,
                    onDragCancel = onCancel,
                )
            }
        }
}

/**
 * Marks this composable as a drop target for a song being dragged onto [playlist]. The
 * registered bounds are kept in root-window coordinates so they line up with the pointer
 * positions reported by [draggableSong].
 */
internal fun Modifier.playlistDropTarget(playlist: Playlist): Modifier = composed {
    val controller = LocalDragDrop.current ?: return@composed this
    DisposableEffect(playlist.id) {
        onDispose { controller.unregister(playlist.id) }
    }
    onGloballyPositioned { controller.register(playlist, it.boundsInRoot()) }
}

/**
 * Floating ghost rendered above all other UI while a drag is in flight.
 *
 * Implementation notes:
 *  - Lives inside a `fillMaxSize` Box at the very TOP of [PhoebeRoot]'s overlay layer so it
 *    paints above every other panel.
 *  - Has no [pointerInput] of its own. Pointer events therefore pass straight through to the
 *    originating song row whose [pointerInput] is still driving the drag — if we swallowed
 *    them here the row would stop receiving `onDrag` updates and the gesture would silently
 *    abort.
 *  - Reads [DragDropController.hoveringPlaylistTitle] so it can morph into an
 *    "Add to {playlist}" pill the moment the pointer enters a sidebar drop target, giving
 *    the user a clear "yes, this will work" affordance before they release.
 */
@Composable
internal fun DragGhost() {
    val controller = LocalDragDrop.current ?: return
    val track = controller.draggedTrack ?: return
    val pointer = controller.pointer ?: return
    val density = LocalDensity.current
    val hoveringTitle = controller.hoveringPlaylistTitle
    val onTarget = hoveringTitle != null

    Box(
        modifier = Modifier
            .offset {
                // Anchor the ghost just below-and-right of the actual cursor so it doesn't
                // hide the underlying drop target. Constants are in px.
                val px = with(density) { 14.dp.toPx() }
                val py = with(density) { 10.dp.toPx() }
                IntOffset(
                    x = (pointer.x + px).roundToInt(),
                    y = (pointer.y + py).roundToInt(),
                )
            }
            .zIndex(1000f)
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (onTarget) PhoebeUi.accentLight.copy(alpha = 0.96f) else PhoebeUi.accent.copy(alpha = 0.92f),
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = if (onTarget) 0.45f else 0.18f)),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (onTarget) "Add to $hoveringTitle" else "Moving",
                color = if (onTarget) Color.White else PhoebeUi.primaryText.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
            )
            Text(
                "♪  ${track.title}",
                color = PhoebeUi.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}

/**
 * Three-bar animated equaliser used as a "this track is currently playing" indicator
 * inside library track rows. The bars pulse while [isPlaying] is true and freeze at a low
 * height when playback is paused.
 */
@Composable
internal fun NowPlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = PhoebeUi.accentLight,
) {
    val transition = rememberInfiniteTransition(label = "now-playing")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "bar1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "bar2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 460, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "bar3",
    )
    Canvas(modifier) {
        val heights = if (isPlaying) listOf(bar1, bar2, bar3) else listOf(0.3f, 0.3f, 0.3f)
        val barWidth = size.width / 7f
        val gap = barWidth
        val totalWidth = barWidth * 3 + gap * 2
        val startX = (size.width - totalWidth) / 2f
        heights.forEachIndexed { i, h ->
            val barH = (size.height * h).coerceAtLeast(barWidth)
            val x = startX + i * (barWidth + gap)
            val y = size.height - barH
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun CatalogLoadingStrip(modifier: Modifier = Modifier) {
    val hasContent = LocalCatalogHasContent.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = PhoebeUi.accentLight,
            trackColor = Color.White.copy(alpha = 0.08f),
        )
        Text(
            if (hasContent) "Syncing library…" else "Loading your library…",
            color = PhoebeUi.mutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.06.em,
        )
    }
}

@Composable
internal fun SectionLabel(label: String, color: Color) {
    Text(label.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em)
}

@Composable
internal fun ArtworkImage(seed: String, thumbUrl: String?, modifier: Modifier = Modifier, radius: Dp = 10.dp) {
    val image = rememberRemoteImage(thumbUrl)
    val shape = RoundedCornerShape(radius)
    val imageModifier = if (prefersReducedArtworkEffects()) {
        modifier.clip(shape)
    } else {
        modifier
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.38f))
            .clip(shape)
    }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = imageModifier,
        )
    } else {
        AlbumArtwork(seed, modifier, radius)
    }
}

@Composable
private fun rememberRemoteImage(url: String?): ImageBitmap? {
    val target = url?.takeIf { it.isNotBlank() } ?: return null
    val image = RemoteArtworkCache.images[target]
    LaunchedEffect(url) {
        RemoteArtworkCache.load(target)
    }
    return image
}

@Composable
private fun AlbumArtwork(seed: String, modifier: Modifier = Modifier, radius: Dp = 10.dp) {
    val shape = RoundedCornerShape(radius)
    if (prefersReducedArtworkEffects()) {
        Box(
            modifier
                .clip(shape)
                .background(ArtworkBrush(seed)),
        )
        return
    }
    Box(
        modifier
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.38f))
            .clip(shape)
            .background(ArtworkBrush(seed)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF17345E), Color(0xFF7F5C91), Color(0xFF162033))), alpha = 0.94f)
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension * 0.52f, center = Offset(size.width * 0.58f, size.height * 0.34f))
            drawRect(Color(0x33200630), topLeft = Offset(0f, size.height * 0.58f), size = Size(size.width, size.height * 0.42f))
            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = Offset(0f, size.height * 0.61f),
                end = Offset(size.width, size.height * 0.61f),
                strokeWidth = 1.dp.toPx(),
            )
            repeat(28) { star ->
                val x = ((star * 47) % 100) / 100f * size.width
                val y = ((star * 29) % 48) / 100f * size.height
                drawCircle(Color.White.copy(alpha = 0.35f), radius = 0.8.dp.toPx(), center = Offset(x, y))
            }
            val figureX = size.width * 0.5f
            val groundY = size.height * 0.69f
            drawCircle(Color(0xFF050710), radius = size.width * 0.018f, center = Offset(figureX, groundY - size.height * 0.12f))
            drawRoundRect(
                color = Color(0xFF050710),
                topLeft = Offset(figureX - size.width * 0.018f, groundY - size.height * 0.105f),
                size = Size(size.width * 0.036f, size.height * 0.13f),
            )
            val reflection = Path().apply {
                moveTo(figureX, groundY + size.height * 0.02f)
                lineTo(figureX - size.width * 0.025f, size.height * 0.84f)
                lineTo(figureX + size.width * 0.012f, size.height * 0.84f)
                close()
            }
            drawPath(reflection, Color.Black.copy(alpha = 0.26f))
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.26f))),
                topLeft = Offset.Zero,
                size = size,
            )
        }
    }
}

private object RemoteArtworkCache {
    val images = mutableStateMapOf<String, ImageBitmap>()

    private val httpClient: HttpClient by lazy { createPlatformHttpClient() }
    private val gate = Semaphore(permits = 2)
    private val mutex = Mutex()
    private val loading = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()

    suspend fun load(url: String) {
        if (images.containsKey(url) || failed.contains(url)) return
        val shouldLoad = mutex.withLock {
            when {
                images.containsKey(url) || failed.contains(url) || loading.contains(url) -> false
                else -> {
                    loading += url
                    true
                }
            }
        }
        if (!shouldLoad) return

        try {
            gate.withPermit {
                val decoded = runCatching {
                    val bytes: ByteArray = httpClient.get(url).body()
                    yield()
                    decodeImageBitmap(bytes)
                }.getOrNull()
                if (decoded != null) {
                    images[url] = decoded
                } else {
                    mutex.withLock { failed += url }
                }
            }
        } finally {
            mutex.withLock { loading -= url }
        }
    }
}

private fun ArtworkBrush(seed: String): Brush {
    val hash = seed.fold(0) { acc, char -> acc * 31 + char.code }
    val palettes = listOf(
        listOf(Color(0xFF123969), Color(0xFFB97596), Color(0xFF061323)),
        listOf(Color(0xFF1B234F), Color(0xFFED704C), Color(0xFF111827)),
        listOf(Color(0xFF14395B), Color(0xFF5C8F55), Color(0xFF10151F)),
        listOf(Color(0xFF11243A), Color(0xFF9B4DFF), Color(0xFF0A0D14)),
    )
    return Brush.linearGradient(palettes[kotlin.math.abs(hash) % palettes.size])
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
