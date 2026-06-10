package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import com.phoebe.app.AppNavigationRequest
import com.phoebe.app.CollectionMixSeed
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.AppScreen
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.PlayHistoryKind
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.RecentlyAddedKind
import com.phoebe.app.domain.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import com.phoebe.app.platform.isIosPlatform
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Serializable
internal sealed interface PhoebeRoute : NavKey {
    @Serializable
    data object SignIn : PhoebeRoute

    @Serializable
    data object ServerPicker : PhoebeRoute

    @Serializable
    data object LibraryPicker : PhoebeRoute

    @Serializable
    data class Browse(val section: BrowseSection = BrowseSection.Home) : PhoebeRoute

    @Serializable
    data class Collections(
        val entry: CollectionEntry,
    ) : PhoebeRoute

    @Serializable
    data class CollectionItems(
        val entry: CollectionEntry,
        val value: String,
    ) : PhoebeRoute

    @Serializable
    data class ArtistDetail(val artistId: String) : PhoebeRoute

    @Serializable
    data class ArtistSlugDetail(val artistSlug: String) : PhoebeRoute

    @Serializable
    data class AlbumDetail(val albumId: String) : PhoebeRoute

    @Serializable
    data class ArtistAlbumSlugDetail(
        val artistSlug: String,
        val albumSlug: String,
    ) : PhoebeRoute

    @Serializable
    data class SongDetail(val trackId: String) : PhoebeRoute

    @Serializable
    data class Lyrics(val trackId: String? = null) : PhoebeRoute

    @Serializable
    data class RecentlyAdded(val kind: RecentlyAddedKind) : PhoebeRoute

    @Serializable
    data class PlayHistory(val kind: PlayHistoryKind) : PhoebeRoute

    @Serializable
    data object FavoritePlaylists : PhoebeRoute

    @Serializable
    data object FavoriteArtists : PhoebeRoute

    @Serializable
    data object FavoriteAlbums : PhoebeRoute

    @Serializable
    data class PlaylistDetail(val playlistId: String) : PhoebeRoute

    @Serializable
    data class PlaylistSlugDetail(val playlistSlug: String) : PhoebeRoute

    @Serializable
    data object Player : PhoebeRoute
}

internal val phoebeRouteSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(PhoebeRoute.SignIn::class, PhoebeRoute.SignIn.serializer())
        subclass(PhoebeRoute.ServerPicker::class, PhoebeRoute.ServerPicker.serializer())
        subclass(PhoebeRoute.LibraryPicker::class, PhoebeRoute.LibraryPicker.serializer())
        subclass(PhoebeRoute.Browse::class, PhoebeRoute.Browse.serializer())
        subclass(PhoebeRoute.Collections::class, PhoebeRoute.Collections.serializer())
        subclass(PhoebeRoute.CollectionItems::class, PhoebeRoute.CollectionItems.serializer())
        subclass(PhoebeRoute.ArtistDetail::class, PhoebeRoute.ArtistDetail.serializer())
        subclass(PhoebeRoute.ArtistSlugDetail::class, PhoebeRoute.ArtistSlugDetail.serializer())
        subclass(PhoebeRoute.AlbumDetail::class, PhoebeRoute.AlbumDetail.serializer())
        subclass(PhoebeRoute.ArtistAlbumSlugDetail::class, PhoebeRoute.ArtistAlbumSlugDetail.serializer())
        subclass(PhoebeRoute.SongDetail::class, PhoebeRoute.SongDetail.serializer())
        subclass(PhoebeRoute.Lyrics::class, PhoebeRoute.Lyrics.serializer())
        subclass(PhoebeRoute.RecentlyAdded::class, PhoebeRoute.RecentlyAdded.serializer())
        subclass(PhoebeRoute.PlayHistory::class, PhoebeRoute.PlayHistory.serializer())
        subclass(PhoebeRoute.FavoritePlaylists::class, PhoebeRoute.FavoritePlaylists.serializer())
        subclass(PhoebeRoute.FavoriteArtists::class, PhoebeRoute.FavoriteArtists.serializer())
        subclass(PhoebeRoute.FavoriteAlbums::class, PhoebeRoute.FavoriteAlbums.serializer())
        subclass(PhoebeRoute.PlaylistDetail::class, PhoebeRoute.PlaylistDetail.serializer())
        subclass(PhoebeRoute.PlaylistSlugDetail::class, PhoebeRoute.PlaylistSlugDetail.serializer())
        subclass(PhoebeRoute.Player::class, PhoebeRoute.Player.serializer())
    }
}

private val phoebeRouteJson = Json {
    serializersModule = phoebeRouteSerializersModule
    classDiscriminator = "type"
}

private val phoebeRouteListSerializer = ListSerializer(PhoebeRoute.serializer())

internal fun encodePhoebeRouteBackStack(routes: List<PhoebeRoute>): String =
    phoebeRouteJson.encodeToString(phoebeRouteListSerializer, routes)

internal fun decodePhoebeRouteBackStack(routesJson: String): NavBackStack<PhoebeRoute> {
    val routes = phoebeRouteJson
        .decodeFromString(phoebeRouteListSerializer, routesJson)
        .ifEmpty { listOf(PhoebeRoute.SignIn) }
    return NavBackStack<PhoebeRoute>(routes.first()).apply {
        addAll(routes.drop(1))
    }
}

private val PhoebeRouteBackStackSaver = Saver<NavBackStack<PhoebeRoute>, String>(
    save = { backStack -> encodePhoebeRouteBackStack(backStack) },
    restore = ::decodePhoebeRouteBackStack,
)

@Composable
internal fun MissingRouteFallback(
    title: String,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(24.dp),
        ) {
            Text(
                text = title,
                color = PhoebeUi.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                color = PhoebeUi.secondaryText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
internal fun PhoebeNavDisplay(
    backStack: List<PhoebeRoute>,
    modifier: Modifier = Modifier,
    animateTransitions: Boolean = true,
    opaqueSceneBackgrounds: Boolean = false,
    onBack: () -> Unit,
    content: @Composable (PhoebeRoute) -> Unit,
) {
    if (isIosPlatform()) {
        SwipeBackNavDisplay(
            backStack = backStack,
            modifier = modifier,
            opaqueSceneBackgrounds = opaqueSceneBackgrounds,
            onBack = onBack,
            content = content
        )
    } else {
        val transitionSpec: AnimatedContentTransitionScope<Scene<PhoebeRoute>>.() -> ContentTransform =
            if (animateTransitions) defaultTransitionSpec() else noPhoebeRouteTransition()
        val popTransitionSpec: AnimatedContentTransitionScope<Scene<PhoebeRoute>>.() -> ContentTransform =
            if (animateTransitions) defaultPopTransitionSpec() else noPhoebeRouteTransition()
        val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<PhoebeRoute>>.(Int) -> ContentTransform =
            if (animateTransitions) {
                { edge -> defaultPredictivePopTransitionSpec<PhoebeRoute>().invoke(this, edge) }
            } else {
                { _ -> noPhoebeRouteContentTransform() }
            }

        NavDisplay(
            backStack = backStack.ifEmpty { listOf(PhoebeRoute.SignIn) },
            modifier = modifier,
            transitionSpec = transitionSpec,
            popTransitionSpec = popTransitionSpec,
            predictivePopTransitionSpec = predictivePopTransitionSpec,
            onBack = onBack,
            entryProvider = entryProvider {
                entry<PhoebeRoute.SignIn> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.ServerPicker> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.LibraryPicker> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.Browse>(clazzContentKey = { "browse" }) { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.Collections> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.CollectionItems> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.ArtistDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.ArtistSlugDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.AlbumDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.ArtistAlbumSlugDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.SongDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.Lyrics> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.RecentlyAdded> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.PlayHistory> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.FavoritePlaylists> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.FavoriteArtists> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.FavoriteAlbums> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.PlaylistDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.PlaylistSlugDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                entry<PhoebeRoute.Player> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
            },
        )
    }
}

@Composable
private fun SwipeBackNavDisplay(
    backStack: List<PhoebeRoute>,
    modifier: Modifier = Modifier,
    opaqueSceneBackgrounds: Boolean = false,
    onBack: () -> Unit,
    content: @Composable (PhoebeRoute) -> Unit,
) {
    var swipePopInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(backStack) {
        swipePopInProgress = false
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }

        val edgeDragModifier = Modifier.pointerInput(backStack) {
            if (backStack.size <= 1) return@pointerInput
            val edgeWidthPx = 32.dp.toPx()
            val touchSlopPx = 8.dp.toPx()

            coroutineScope {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startX = down.position.x
                        if (startX <= edgeWidthPx) {
                            var dragOffsetValue = 0f
                            var isDragGestureStarted = false
                            val velocityTracker = VelocityTracker()
                            val dragPointerId = down.id
                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                            var totalDeltaX = 0f
                            var totalDeltaY = 0f
                            var dragCompleted = false

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == dragPointerId }
                                if (change == null) {
                                    break
                                }
                                if (!change.pressed) {
                                    dragCompleted = true
                                    break
                                }

                                val horizontalDelta = change.positionChange().x
                                val verticalDelta = change.positionChange().y

                                if (!isDragGestureStarted) {
                                    totalDeltaX += horizontalDelta
                                    totalDeltaY += verticalDelta

                                    val absX = kotlin.math.abs(totalDeltaX)
                                    val absY = kotlin.math.abs(totalDeltaY)

                                    if (absX > touchSlopPx || absY > touchSlopPx) {
                                        // Lock in if drag is to the right and primarily horizontal
                                        if (totalDeltaX > 0 && absX > absY) {
                                            isDragGestureStarted = true
                                            isDragging = true
                                            dragOffsetValue = totalDeltaX - touchSlopPx
                                            dragOffset = dragOffsetValue
                                            change.consume()
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        } else {
                                            // Diagonal/vertical drag first, cancel tracking this touch
                                            break
                                        }
                                    }
                                } else {
                                    dragOffsetValue = (dragOffsetValue + horizontalDelta).coerceAtLeast(0f)
                                    isDragging = true
                                    dragOffset = dragOffsetValue
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                }
                            }

                            if (isDragGestureStarted) {
                                val velocity = velocityTracker.calculateVelocity().x
                                val minDragDistancePx = with(density) { 56.dp.toPx() }
                                val velocityThresholdPx = with(density) { 600.dp.toPx() }

                                if (dragCompleted) {
                                    if (dragOffsetValue > screenWidthPx / 3f || (dragOffsetValue > minDragDistancePx && velocity > velocityThresholdPx)) {
                                        launch {
                                            animate(
                                                initialValue = dragOffsetValue,
                                                targetValue = screenWidthPx
                                            ) { value, _ ->
                                                dragOffset = value
                                            }
                                            swipePopInProgress = true
                                            onBack()
                                            isDragging = false
                                            dragOffset = 0f
                                        }
                                    } else {
                                        launch {
                                            animate(
                                                initialValue = dragOffsetValue,
                                                targetValue = 0f
                                            ) { value, _ ->
                                                dragOffset = value
                                            }
                                            isDragging = false
                                            dragOffset = 0f
                                        }
                                    }
                                } else {
                                    launch {
                                        animate(
                                            initialValue = dragOffsetValue,
                                            targetValue = 0f
                                        ) { value, _ ->
                                            dragOffset = value
                                        }
                                        isDragging = false
                                        dragOffset = 0f
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().then(edgeDragModifier)) {
            val progress = if (screenWidthPx > 0f) (dragOffset / screenWidthPx).coerceIn(0f, 1f) else 0f
            val parallaxOffset = (-screenWidthPx / 3f) * (1f - progress)

            // Previous screen underneath
            if (isDragging && backStack.size > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = parallaxOffset
                        }
                ) {
                    val prevRoute = backStack[backStack.size - 2]
                    SwipeBackNavEntryContent(prevRoute, opaqueSceneBackgrounds, content)

                    // Dimming overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f * (1f - progress)))
                    )
                }
            }

            // Active top screen (sliding wrapper)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragOffset
                        }
                    }
                    .drawBehind {
                        if (isDragging && dragOffset > 0f) {
                            val shadowWidth = 16.dp.toPx()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)),
                                    startX = -shadowWidth,
                                    endX = 0f
                                ),
                                topLeft = Offset(-shadowWidth, 0f),
                                size = Size(shadowWidth, size.height)
                            )
                        }
                    }
            ) {
                val animate = !swipePopInProgress
                val transitionSpec: AnimatedContentTransitionScope<Scene<PhoebeRoute>>.() -> ContentTransform =
                    if (animate) defaultTransitionSpec() else noPhoebeRouteTransition()
                val popTransitionSpec: AnimatedContentTransitionScope<Scene<PhoebeRoute>>.() -> ContentTransform =
                    if (animate) defaultPopTransitionSpec() else noPhoebeRouteTransition()
                val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<PhoebeRoute>>.(Int) -> ContentTransform =
                    if (animate) {
                        { edge -> defaultPredictivePopTransitionSpec<PhoebeRoute>().invoke(this, edge) }
                    } else {
                        { _ -> noPhoebeRouteContentTransform() }
                    }

                NavDisplay(
                    backStack = backStack.ifEmpty { listOf(PhoebeRoute.SignIn) },
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = transitionSpec,
                    popTransitionSpec = popTransitionSpec,
                    predictivePopTransitionSpec = predictivePopTransitionSpec,
                    onBack = onBack,
                    entryProvider = entryProvider {
                        entry<PhoebeRoute.SignIn> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.ServerPicker> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.LibraryPicker> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.Browse>(clazzContentKey = { "browse" }) { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.Collections> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.CollectionItems> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.ArtistDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.ArtistSlugDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.AlbumDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.ArtistAlbumSlugDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.SongDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.Lyrics> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.RecentlyAdded> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.PlayHistory> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.FavoritePlaylists> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.FavoriteArtists> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.FavoriteAlbums> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.PlaylistDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.PlaylistSlugDetail> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                        entry<PhoebeRoute.Player> { route -> PhoebeNavEntryContent(route, opaqueSceneBackgrounds, content) }
                    },
                )
            }
        }
    }
}

private fun noPhoebeRouteTransition():
    AnimatedContentTransitionScope<Scene<PhoebeRoute>>.() -> ContentTransform = {
    noPhoebeRouteContentTransform()
}

private fun noPhoebeRouteContentTransform(): ContentTransform =
    ContentTransform(EnterTransition.None, ExitTransition.None)

@Composable
private fun PhoebeNavEntryContent(
    route: PhoebeRoute,
    opaqueSceneBackground: Boolean,
    content: @Composable (PhoebeRoute) -> Unit,
) {
    CompositionLocalProvider(
        LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
    ) {
        if (opaqueSceneBackground) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PhoebeUi.shellTop),
            ) {
                content(route)
            }
        } else {
            content(route)
        }
    }
}

@Composable
private fun SwipeBackNavEntryContent(
    route: PhoebeRoute,
    opaqueSceneBackground: Boolean,
    content: @Composable (PhoebeRoute) -> Unit,
) {
    if (opaqueSceneBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .background(PhoebeUi.shellTop),
        ) {
            content(route)
        }
    } else {
        content(route)
    }
}

@Composable
internal fun rememberPhoebeNavigator(initialRoute: PhoebeRoute): PhoebeNavigator =
    rememberPhoebeNavigator(listOf(initialRoute))

@Composable
internal fun rememberPhoebeNavigator(initialRoutes: List<PhoebeRoute>): PhoebeNavigator {
    val safeInitialRoutes = initialRoutes.ifEmpty { listOf(PhoebeRoute.SignIn) }
    val backStack = rememberSaveable(saver = PhoebeRouteBackStackSaver) {
        NavBackStack<PhoebeRoute>(safeInitialRoutes.first()).apply {
            addAll(safeInitialRoutes.drop(1))
        }
    }
    return remember(backStack) { PhoebeNavigator(backStack) }
}

internal class PhoebeNavigator(
    private val backStack: MutableList<PhoebeRoute>,
) {
    constructor(initialRoute: PhoebeRoute) : this(mutableStateListOf(initialRoute))
    constructor(backStack: SnapshotStateList<PhoebeRoute>) : this(backStack as MutableList<PhoebeRoute>)

    val routes: List<PhoebeRoute>
        get() = backStack.toList()

    val currentRoute: PhoebeRoute
        get() = routes.lastOrNull() ?: PhoebeRoute.SignIn

    fun open(route: PhoebeRoute) {
        if (currentRoute != route) {
            backStack.add(route)
        }
    }

    fun replaceRoot(route: PhoebeRoute) {
        if (backStack.size == 1 && backStack.firstOrNull() == route) return
        if (backStack.isEmpty()) {
            backStack.add(route)
            return
        }
        backStack[0] = route
        while (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun replaceAll(routes: List<PhoebeRoute>) {
        val safeRoutes = routes.ifEmpty { listOf(PhoebeRoute.SignIn) }
        if (this.routes == safeRoutes) return
        backStack.clear()
        backStack.addAll(safeRoutes)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun openPlayer() {
        open(PhoebeRoute.Player)
    }

    fun handle(request: AppNavigationRequest) {
        when (request) {
            AppNavigationRequest.SignIn -> replaceRoot(PhoebeRoute.SignIn)
            AppNavigationRequest.ServerPicker -> openSetupRoute(PhoebeRoute.ServerPicker)
            AppNavigationRequest.LibraryPicker -> openSetupRoute(PhoebeRoute.LibraryPicker)
            AppNavigationRequest.Home -> openHomeFromAppRequest()
            AppNavigationRequest.Player -> openPlayer()
            is AppNavigationRequest.PlaylistDetail -> {
                replaceRoot(PhoebeRoute.Browse())
                open(PhoebeRoute.PlaylistDetail(request.playlistId))
            }
        }
    }

    fun openBrowse(section: BrowseSection) {
        replaceRoot(PhoebeRoute.Browse(section))
    }

    private fun openHomeFromAppRequest() {
        // Startup/session restore can emit Home after the user has already entered browse.
        if (routes.firstOrNull() is PhoebeRoute.Browse) return
        replaceRoot(PhoebeRoute.Browse())
    }

    private fun openSetupRoute(route: PhoebeRoute) {
        if (routes.firstOrNull() != PhoebeRoute.SignIn) {
            replaceRoot(PhoebeRoute.SignIn)
        }
        when (route) {
            PhoebeRoute.ServerPicker -> {
                while (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
                }
                open(route)
            }
            PhoebeRoute.LibraryPicker -> {
                openSetupRoute(PhoebeRoute.ServerPicker)
                open(route)
            }
            else -> open(route)
        }
    }
}

internal fun AppNavigationRequest.toPhoebeRoute(): PhoebeRoute = when (this) {
    AppNavigationRequest.SignIn -> PhoebeRoute.SignIn
    AppNavigationRequest.ServerPicker -> PhoebeRoute.ServerPicker
    AppNavigationRequest.LibraryPicker -> PhoebeRoute.LibraryPicker
    AppNavigationRequest.Home -> PhoebeRoute.Browse()
    AppNavigationRequest.Player -> PhoebeRoute.Player
    is AppNavigationRequest.PlaylistDetail -> PhoebeRoute.PlaylistDetail(playlistId)
}

internal sealed interface PhoebeRouteResolution {
    val route: PhoebeRoute

    data class Resolved(
        override val route: PhoebeRoute,
        val screen: AppScreen,
    ) : PhoebeRouteResolution

    data class Missing(
        override val route: PhoebeRoute,
        val title: String,
        val message: String,
    ) : PhoebeRouteResolution
}

internal fun resolvePhoebeRoute(
    route: PhoebeRoute,
    catalog: CatalogSnapshot,
    currentTrack: Track?,
): PhoebeRouteResolution = when (route) {
    PhoebeRoute.SignIn -> route.resolved(AppScreen.SignIn)
    PhoebeRoute.ServerPicker -> route.resolved(AppScreen.ServerPicker)
    PhoebeRoute.LibraryPicker -> route.resolved(AppScreen.LibraryPicker)
    is PhoebeRoute.Browse -> route.resolved(AppScreen.Home)
    is PhoebeRoute.Collections -> route.resolved(AppScreen.Collections(route.entry))
    is PhoebeRoute.CollectionItems -> route.resolved(AppScreen.CollectionItems(route.entry, route.value))
    is PhoebeRoute.ArtistDetail -> catalog.findArtist(route.artistId)
        ?.let { route.resolved(AppScreen.ArtistDetail(it)) }
        ?: route.missing("Artist not found", "This artist is no longer available in the current library.")
    is PhoebeRoute.ArtistSlugDetail -> when (val match = catalog.findArtistBySlug(route.artistSlug)) {
        is SlugMatch.Found -> route.resolved(AppScreen.ArtistDetail(match.value))
        SlugMatch.Ambiguous -> route.missing("Artist name is ambiguous", "More than one artist matches this URL.")
        SlugMatch.Missing -> route.missing("Artist not found", "No artist in the current library matches this URL.")
    }
    is PhoebeRoute.AlbumDetail -> catalog.findAlbum(route.albumId)
        ?.let { route.resolved(AppScreen.AlbumDetail(it)) }
        ?: route.missing("Album not found", "This album is no longer available in the current library.")
    is PhoebeRoute.ArtistAlbumSlugDetail -> when (val match = catalog.findAlbumByArtistAndAlbumSlug(route.artistSlug, route.albumSlug)) {
        is SlugMatch.Found -> route.resolved(AppScreen.AlbumDetail(match.value))
        SlugMatch.Ambiguous -> route.missing("Album name is ambiguous", "More than one album matches this URL.")
        SlugMatch.Missing -> route.missing("Album not found", "No album in the current library matches this URL.")
    }
    is PhoebeRoute.SongDetail -> catalog.findTrack(route.trackId, currentTrack)
        ?.let { route.resolved(AppScreen.SongDetail(it)) }
        ?: route.missing("Song not found", "This song is no longer available in the current library.")
    is PhoebeRoute.Lyrics -> {
        val track = route.trackId?.let { catalog.findTrack(it, currentTrack) } ?: currentTrack
        if (route.trackId != null && track == null) {
            route.missing("Lyrics unavailable", "The selected song is no longer available in the current library.")
        } else {
            route.resolved(AppScreen.Lyrics(track))
        }
    }
    is PhoebeRoute.RecentlyAdded -> route.resolved(AppScreen.RecentlyAdded(route.kind))
    is PhoebeRoute.PlayHistory -> route.resolved(AppScreen.PlayHistory(route.kind))
    PhoebeRoute.FavoritePlaylists -> route.resolved(AppScreen.FavoritePlaylists)
    PhoebeRoute.FavoriteArtists -> route.resolved(AppScreen.FavoriteArtists)
    PhoebeRoute.FavoriteAlbums -> route.resolved(AppScreen.FavoriteAlbums)
    is PhoebeRoute.PlaylistDetail -> catalog.findPlaylist(route.playlistId)
        ?.let { route.resolved(AppScreen.PlaylistDetail(it)) }
        ?: route.missing("Playlist not found", "This playlist is no longer available in the current library.")
    is PhoebeRoute.PlaylistSlugDetail -> when (val match = catalog.findPlaylistBySlug(route.playlistSlug)) {
        is SlugMatch.Found -> route.resolved(AppScreen.PlaylistDetail(match.value))
        SlugMatch.Ambiguous -> route.missing("Playlist name is ambiguous", "More than one playlist matches this URL.")
        SlugMatch.Missing -> route.missing("Playlist not found", "No playlist in the current library matches this URL.")
    }
    PhoebeRoute.Player -> route.resolved(AppScreen.Player)
}

internal fun Collection<PhoebeRoute>.collectionMixSeed(): CollectionMixSeed? {
    val route = filterIsInstance<PhoebeRoute.CollectionItems>().lastOrNull() ?: return null
    return CollectionMixSeed(route.entry.facet, route.value)
}

internal fun Artist.route(): PhoebeRoute = PhoebeRoute.ArtistDetail(id)
internal fun Album.route(): PhoebeRoute = PhoebeRoute.AlbumDetail(id)
internal fun Track.route(): PhoebeRoute = PhoebeRoute.SongDetail(id)
internal fun Playlist.route(): PhoebeRoute = PhoebeRoute.PlaylistDetail(id)

internal val PhoebeRoute.telemetryName: String
    get() = when (this) {
        PhoebeRoute.SignIn -> "sign_in"
        PhoebeRoute.ServerPicker -> "server_picker"
        PhoebeRoute.LibraryPicker -> "library_picker"
        is PhoebeRoute.Browse -> "home"
        is PhoebeRoute.Collections -> "collections"
        is PhoebeRoute.CollectionItems -> "collection_items"
        is PhoebeRoute.AlbumDetail -> "album_detail"
        is PhoebeRoute.ArtistAlbumSlugDetail -> "album_detail"
        is PhoebeRoute.ArtistDetail -> "artist_detail"
        is PhoebeRoute.ArtistSlugDetail -> "artist_detail"
        is PhoebeRoute.SongDetail -> "song_detail"
        is PhoebeRoute.Lyrics -> "lyrics"
        is PhoebeRoute.RecentlyAdded -> "recently_added"
        is PhoebeRoute.PlayHistory -> "play_history"
        PhoebeRoute.FavoritePlaylists -> "favorite_playlists"
        PhoebeRoute.FavoriteArtists -> "favorite_artists"
        PhoebeRoute.FavoriteAlbums -> "favorite_albums"
        is PhoebeRoute.PlaylistDetail -> "playlist_detail"
        is PhoebeRoute.PlaylistSlugDetail -> "playlist_detail"
        PhoebeRoute.Player -> "player"
    }

private fun PhoebeRoute.resolved(screen: AppScreen) = PhoebeRouteResolution.Resolved(this, screen)

private fun PhoebeRoute.missing(title: String, message: String) =
    PhoebeRouteResolution.Missing(this, title, message)

private fun CatalogSnapshot.findArtist(id: String): Artist? = artists.firstOrNull { it.id == id }

private fun CatalogSnapshot.findAlbum(id: String): Album? = albums.firstOrNull { it.id == id }

private fun CatalogSnapshot.findPlaylist(id: String): Playlist? = playlists.firstOrNull { it.id == id }

private fun CatalogSnapshot.findTrack(id: String, currentTrack: Track?): Track? =
    currentTrack?.takeIf { it.id == id }
        ?: tracksByParent.values.asSequence().flatten().firstOrNull { it.id == id }

private fun CatalogSnapshot.findArtistBySlug(slug: String): SlugMatch<Artist> =
    artists.matchSingleBySlug(slug) { title }

private fun CatalogSnapshot.findAlbumByArtistAndAlbumSlug(
    artistSlug: String,
    albumSlug: String,
): SlugMatch<Album> =
    albums
        .filter { phoebePathSlug(it.artist) == artistSlug }
        .matchSingleBySlug(albumSlug) { title }

private fun CatalogSnapshot.findPlaylistBySlug(slug: String): SlugMatch<Playlist> =
    playlists.matchSingleBySlug(slug) { title }

private sealed interface SlugMatch<out T> {
    data class Found<T>(val value: T) : SlugMatch<T>
    data object Missing : SlugMatch<Nothing>
    data object Ambiguous : SlugMatch<Nothing>
}

private inline fun <T> Iterable<T>.matchSingleBySlug(
    slug: String,
    label: T.() -> String,
): SlugMatch<T> {
    val matches = filter { phoebePathSlug(it.label()) == slug }
    return when (matches.size) {
        0 -> SlugMatch.Missing
        1 -> SlugMatch.Found(matches.single())
        else -> SlugMatch.Ambiguous
    }
}
