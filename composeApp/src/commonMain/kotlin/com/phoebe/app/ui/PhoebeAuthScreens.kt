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

@Composable
internal fun SignInWelcomeScreen(
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
internal fun MobileSignInWelcomeScreen(
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AuthFlowBackgroundColor()),
    ) {
        val compactHeight = maxHeight < 820.dp
        val heroMaxSize = if (compactHeight) 282.dp else 342.dp
        val topPadding = if (compactHeight) 18.dp else 28.dp
        val brandTopPadding = if (compactHeight) 8.dp else 16.dp
        val brandSpacer = if (compactHeight) 22.dp else 34.dp
        val heroSpacer = if (compactHeight) 22.dp else 28.dp
        val featureSpacer = if (compactHeight) 20.dp else 24.dp
        val ctaSpacer = if (compactHeight) 22.dp else 28.dp
        val titleSize = if (compactHeight) 30.sp else 32.sp
        val titleLineHeight = if (compactHeight) 35.sp else 38.sp

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = topPadding, end = 24.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.padding(top = brandTopPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrandMark(size = if (compactHeight) 30.dp else 34.dp)
                Text(
                    "phoebe",
                    color = PhoebeUi.primaryText,
                    fontSize = if (compactHeight) 25.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(brandSpacer))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = heroMaxSize)
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

            Spacer(Modifier.height(heroSpacer))

            Text(
                "Your music.\nBeautifully played.",
                color = PhoebeUi.primaryText,
                fontSize = titleSize,
                lineHeight = titleLineHeight,
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

            Spacer(Modifier.height(featureSpacer))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                WelcomeFeatureChip(PhoebeIcon.Music, "Lossless", lightMode = lightMode)
                WelcomeFeatureChip(PhoebeIcon.Library, "Local", lightMode = lightMode)
                WelcomeFeatureChip(PhoebeIcon.Settings, "Metadata", lightMode = lightMode)
            }

            Spacer(Modifier.height(ctaSpacer))

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
}

@Composable
internal fun AuthFlowBackgroundColor(): Color =
    if (LocalPhoebePalette.current == PhoebePaletteLight) Color.White else PhoebeUi.canvasBackground

@Composable
internal fun WelcomeFeatureChip(icon: PhoebeIcon, label: String, lightMode: Boolean) {
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
internal fun GradientActionButton(
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
internal fun ProviderChoiceRow(
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
internal fun PlexServerPickerPanel(
    servers: List<PlexServer>,
    busy: Boolean,
    serversLoading: Boolean = false,
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
        if (serversLoading && servers.isEmpty()) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = PhoebeUi.accentLight,
                    strokeWidth = 2.5.dp,
                    trackColor = PhoebeUi.progressTrack,
                )
                Text("Finding your Plex servers…", color = PhoebeUi.secondaryText, fontSize = 14.sp)
            }
        } else if (servers.isEmpty()) {
            Text("No servers were found for this Plex account.", color = PhoebeUi.secondaryText, fontSize = 14.sp)
            FilledTonalButton(
                onClick = onRetry,
                enabled = !busy && !serversLoading,
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
                            .clickable(enabled = !busy && !serversLoading) { onSelectServer(server) }
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
internal fun PlexLibraryPickerPanel(
    libraries: List<MusicLibrary>,
    serverName: String?,
    busy: Boolean,
    librariesLoading: Boolean = false,
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
        DetailBackButton(onBack = onBack, enabled = !busy)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
        if (librariesLoading && libraries.isEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = PhoebeUi.accent,
                    trackColor = PhoebeUi.progressTrack,
                )
                Text("Finding music libraries...", color = PhoebeUi.secondaryText, fontSize = 14.sp)
            }
        } else if (libraries.isEmpty()) {
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
                            .clickable(enabled = !busy && !librariesLoading) { onSelectLibrary(lib) }
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
