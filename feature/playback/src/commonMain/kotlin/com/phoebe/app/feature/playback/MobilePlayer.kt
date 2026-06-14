package com.phoebe.app.feature.playback

import com.phoebe.app.ui.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoebe.app.data.ListenBrainzFeedbackScore
import com.phoebe.app.data.ListenBrainzFeedbackTarget
import com.phoebe.app.domain.AudioAnalysisFrame
import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.NowPlayingVisualizerPreset
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.canTogglePlexLike
import com.phoebe.app.feature.playback.EqualizerDialog
import com.phoebe.app.platform.isDesktopPlatform
import com.phoebe.app.player.CastState
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MobilePlayerContinuousMotionDelayMs = 240L
private val MobilePlayerMetadataReserveWithAlbum = 104.dp
private val MobilePlayerMetadataReserveWithoutAlbum = 84.dp
private val MobilePlayerRemoteTargetReserve = 18.dp
private val MobilePlayerReflectionOverlap = 112.dp
private val CollapsedMobilePlayerMetadataHeight = 34.dp

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
fun MobilePlayer(
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
    val horizontalSettleOffset = remember { Animatable(0f) }
    var horizontalDragOffset by remember { mutableFloatStateOf(0f) }
    var horizontalIsDragging by remember { mutableStateOf(false) }
    var horizontalSettleJob by remember { mutableStateOf<Job?>(null) }
    var horizontalSwipePreviewDirection by remember { mutableStateOf(0) }

    val currentTrackId = track?.id
    var lastTrackId by remember { mutableStateOf(currentTrackId) }
    var synchronousSwipeOffsetReset by remember { mutableStateOf(false) }

    if (currentTrackId != lastTrackId) {
        lastTrackId = currentTrackId
        horizontalDragOffset = 0f
        horizontalSwipePreviewDirection = 0
        synchronousSwipeOffsetReset = true
        horizontalSettleJob?.cancel()
    }

    LaunchedEffect(currentTrackId) {
        horizontalSettleOffset.snapTo(0f)
        synchronousSwipeOffsetReset = false
    }

    val inheritedContinuousMotionEnabled = LocalContinuousMotionEnabled.current
    var playerContinuousMotionEnabled by remember(track?.id) { mutableStateOf(false) }
    val playerMotionEnabled = inheritedContinuousMotionEnabled && playerContinuousMotionEnabled
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

    val clampedExpansionFraction = expansionFraction.coerceIn(0f, 1f)
    val navBarColor = PhoebeUi.navBar
    val shellRadialTint = PhoebeUi.shellRadialTint
    val shellTop = PhoebeUi.shellTop
    val canvasBackground = PhoebeUi.canvasBackground
    val borderColor = PhoebeUi.border
    val collapsedChromeAlpha = (1f - clampedExpansionFraction * 3f).coerceIn(0f, 1f)

    val cornerRadius = lerp(14.dp, 0.dp, clampedExpansionFraction)
    val containerShape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)

    BoxWithConstraints(
        modifier = modifier
            .playerDragGestures(
                expansionFraction = clampedExpansionFraction,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            )
            .clip(containerShape)
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = borderColor.alpha * (1f - clampedExpansionFraction)),
                shape = containerShape,
            )
            .drawBehind {
                if (clampedExpansionFraction > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                shellTop,
                                canvasBackground.copy(alpha = 0.94f),
                                canvasBackground,
                            ),
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(shellRadialTint.copy(alpha = clampedExpansionFraction * 0.105f), Color.Transparent),
                            center = Offset(210f * this.density, 50f * this.density),
                            radius = 520f * this.density,
                        )
                    )
                }
                if (collapsedChromeAlpha > 0f) {
                    drawRect(color = navBarColor.copy(alpha = collapsedChromeAlpha))
                }
            }
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

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

        val currentArtworkSize = lerp(44.dp, fullArtworkSize, clampedExpansionFraction)
        val currentArtworkX = lerp(12.dp, 20.dp, clampedExpansionFraction)
        val statusBarTopPadding = with(density) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
        val currentArtworkY = lerp(14.dp, 80.dp + statusBarTopPadding, clampedExpansionFraction)

        val miniPlayerAlpha = collapsedChromeAlpha
        val fullPlayerAlpha = ((clampedExpansionFraction - 0.2f) * 1.25f).coerceIn(0f, 1f)
        val overlayActionsAlpha = ((clampedExpansionFraction - 0.7f) / 0.2f).coerceIn(0f, 1f)
        val fullPlayerElementsAlpha = ((clampedExpansionFraction - 0.8f) / 0.2f).coerceIn(0f, 1f)
        val collapsedSheetHeight = with(density) {
            val navBarBottom = WindowInsets.navigationBars.getBottom(this).toDp()
            88.dp + navBarBottom
        }

        val nextTrack = upNext.firstOrNull()
        val currentSwipeOffset = when {
            synchronousSwipeOffsetReset -> 0f
            horizontalIsDragging -> horizontalDragOffset
            else -> horizontalSettleOffset.value
        }
        val swipeThresholdPx = with(density) { 56.dp.toPx() }

        val useBlurredArtworkChrome = track != null && visualizerPreset == NowPlayingVisualizerPreset.Artwork && blurredArtworkAppearance
        val bottomCorner = lerp(10.dp, 0.dp, clampedExpansionFraction)
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
                horizontalSettleOffset.snapTo(releaseOffset)
                val artworkSizePx = with(density) { currentArtworkSize.toPx() }
                val swipeThresholdPx = with(density) { 56.dp.toPx() }
                when {
                    releaseOffset < -swipeThresholdPx && nextTrack != null -> {
                        horizontalSettleOffset.animateTo(
                            targetValue = -artworkSizePx,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        )
                        val steps = (abs(releaseOffset) / artworkSizePx).toInt().coerceIn(1, 5)
                        onSkipQueueBy(steps)
                        delay(600L)
                        horizontalSettleOffset.animateTo(
                            0f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        )
                    }
                    releaseOffset > swipeThresholdPx && previousTrack != null -> {
                        horizontalSettleOffset.animateTo(
                            targetValue = artworkSizePx,
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        )
                        val steps = -(abs(releaseOffset) / artworkSizePx).toInt().coerceIn(1, 5)
                        onSkipQueueBy(steps)
                        delay(600L)
                        horizontalSettleOffset.animateTo(
                            0f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioNoBouncy,
                            ),
                        )
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
                horizontalSwipePreviewDirection = 0
            }
        }

        val horizontalDragModifier = if (track != null) {
            val artworkSizePx = with(density) { currentArtworkSize.toPx() }
            Modifier.pointerInput(track.id, artworkSizePx, swipeThresholdPx) {
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

        if (miniPlayerAlpha > 0f && track != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MobileMiniPlayerChromeHeight)
                    .graphicsLayer {
                        alpha = miniPlayerAlpha
                        if (clampedExpansionFraction < 0.1f) {
                            translationX = currentSwipeOffset
                            val swipeProgress = (abs(currentSwipeOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                            alpha = miniPlayerAlpha * (1f - swipeProgress * 0.14f)
                            val scale = 1f - swipeProgress * 0.025f
                            scaleX = scale
                            scaleY = scale
                        }
                    }
                    .then(if (clampedExpansionFraction < 0.1f) horizontalDragModifier else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        if (useBlurredArtworkChrome) {
            val scale = currentArtworkSize.value / fullArtworkSize.value
            val currentMetadataOverlap = lerp(0.dp, metadataOverlap, clampedExpansionFraction)
            val currentReflectionHeight = (metadataReserve + metadataOverlap) * scale
            val reflectionY = currentArtworkY + currentArtworkSize - currentMetadataOverlap
            val reflectionDismissAlpha = ((clampedExpansionFraction - 0.88f) / 0.12f).coerceIn(0f, 1f)
            val reflectionAlpha = fullPlayerAlpha * reflectionDismissAlpha

            if (currentReflectionHeight > 0.dp && reflectionAlpha > 0f) {
                val artworkSizePx = with(density) { currentArtworkSize.toPx() }
                Box(
                    modifier = Modifier
                        .offset(x = currentArtworkX, y = reflectionY)
                        .width(currentArtworkSize)
                        .height(currentReflectionHeight)
                        .graphicsLayer { alpha = reflectionAlpha }
                        .clipToBounds()
                ) {
                    val reflectionsToRender = remember(track, nextTrack, previousTrack, horizontalSwipePreviewDirection) {
                        buildList {
                            if (previousTrack != null && horizontalSwipePreviewDirection > 0) {
                                add(previousTrack to -1)
                            }
                            add(track to 0)
                            if (nextTrack != null && horizontalSwipePreviewDirection < 0) {
                                add(nextTrack to 1)
                            }
                        }
                    }

                    for ((t, position) in reflectionsToRender) {
                        key(t.id) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset {
                                        val baseOffset = when (position) {
                                            -1 -> -artworkSizePx
                                            1 -> artworkSizePx
                                            else -> 0f
                                        }
                                        IntOffset((baseOffset + currentSwipeOffset).roundToInt(), 0)
                                    }
                                    .graphicsLayer {
                                        if (position == 0) {
                                            val dragProgress = (abs(currentSwipeOffset) / artworkSizePx).coerceIn(0f, 1f)
                                            val s = 1f - dragProgress * 0.03f
                                            scaleX = s
                                            scaleY = s
                                        }
                                    }
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
                                        track = t,
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
                }
            }
        }

        if (fullPlayerAlpha > 0f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fullPlayerAlpha }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
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
                        if (castState.isConnected) {
                            Spacer(Modifier.size(40.dp))
                        } else if (!isDesktopPlatform() || castState.isAvailable) {
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
                    Spacer(Modifier.size(58.dp))
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
                    .then(if (clampedExpansionFraction > 0.8f) horizontalDragModifier else Modifier)
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
                        val artworkFadeHeight = if (artworkFlipRotation > 90f) {
                            0.dp
                        } else {
                            lerp(0.dp, metadataOverlap, clampedExpansionFraction)
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

            val metadataUsesArtworkChrome = visualizerPreset == NowPlayingVisualizerPreset.Artwork && blurredArtworkAppearance
            val metadataTitleColor = if (metadataUsesArtworkChrome) Color.White else PhoebeUi.primaryText
            val metadataArtistColor = if (metadataUsesArtworkChrome) Color.White.copy(alpha = 0.82f) else PhoebeUi.secondaryText

            val titleColor = androidx.compose.ui.graphics.lerp(PhoebeUi.primaryText, metadataTitleColor, clampedExpansionFraction)
            val artistColor = androidx.compose.ui.graphics.lerp(PhoebeUi.secondaryText, metadataArtistColor, clampedExpansionFraction)

            val currentTextX = lerp(68.dp, 36.dp, clampedExpansionFraction)
            val collapsedTextY = (MobileMiniPlayerChromeHeight - CollapsedMobilePlayerMetadataHeight) / 2f
            val currentTextY = lerp(collapsedTextY, 80.dp + statusBarTopPadding + fullArtworkSize + 12.dp, clampedExpansionFraction)
            val collapsedTextWidth = if (castState.isConnected) {
                (screenWidth - 176.dp).coerceAtLeast(96.dp)
            } else {
                screenWidth - 128.dp
            }
            val currentTextWidth = lerp(collapsedTextWidth, fullArtworkSize - 32.dp, clampedExpansionFraction)

            val titleFontSize = (14f + (20f - 14f) * clampedExpansionFraction).sp
            val artistFontSize = (12f + (14f - 12f) * clampedExpansionFraction).sp
            val titleLineHeight = (18f + (24f - 18f) * clampedExpansionFraction).sp
            val artistLineHeight = (16f + (18f - 16f) * clampedExpansionFraction).sp
            val metadataTextStable = clampedExpansionFraction < 0.08f || clampedExpansionFraction > 0.96f
            val titleFontWeight = if (clampedExpansionFraction > 0.96f) FontWeight.Black else FontWeight.Bold

            Column(
                modifier = Modifier
                    .offset(x = currentTextX, y = currentTextY)
                    .width(currentTextWidth)
                    .graphicsLayer {
                        if (clampedExpansionFraction < 0.1f) {
                            translationX = currentSwipeOffset
                            val swipeProgress = (abs(currentSwipeOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                            alpha = miniPlayerAlpha * (1f - swipeProgress * 0.14f)
                            val scale = 1f - swipeProgress * 0.025f
                            scaleX = scale
                            scaleY = scale
                        }
                    }
            ) {
                AutoScrollingText(
                    text = track.title,
                    color = titleColor,
                    fontSize = titleFontSize,
                    fontWeight = titleFontWeight,
                    lineHeight = titleLineHeight,
                    marqueeEnabled = metadataTextStable,
                )
                AutoScrollingText(
                    text = track.artist,
                    color = artistColor,
                    fontSize = artistFontSize,
                    lineHeight = artistLineHeight,
                    modifier = if (clampedExpansionFraction >= 0.85f && track.artist.isNotBlank()) {
                        Modifier.clickable { trackNavigationActions.onOpenArtistForTrack(track) }
                    } else {
                        Modifier
                    },
                    marqueeEnabled = metadataTextStable,
                )
                if (clampedExpansionFraction > 0.5f) {
                    val fadeAlpha = ((clampedExpansionFraction - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    val metadataAlbumColor = if (metadataUsesArtworkChrome) Color.White.copy(alpha = 0.65f) else PhoebeUi.mutedText
                    if (track.album.isNotBlank()) {
                        AutoScrollingText(
                            text = track.album,
                            color = metadataAlbumColor,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .graphicsLayer { alpha = fadeAlpha * fullPlayerElementsAlpha }
                                .clickable {
                                    trackNavigationActions.onOpenAlbumForTrack(track)
                                },
                            marqueeEnabled = metadataTextStable,
                        )
                    }
                    if (remotePlaybackTarget != null) {
                        Text(
                            text = "Music Assistant: $remotePlaybackTarget",
                            color = PhoebeUi.accentLight.copy(alpha = fadeAlpha * fullPlayerElementsAlpha),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = fadeAlpha * fullPlayerElementsAlpha }
                        )
                    }
                }
            }
        }

        if (track != null && clampedExpansionFraction < 0.1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MobileMiniPlayerChromeHeight)
                    .then(horizontalDragModifier)
            )
        }

        if (track != null || fullPlayerAlpha > 0f) {
            val collapsedPlayButtonSize = 40.dp
            val expandedPlayButtonSize = 58.dp
            val playButtonSize = lerp(collapsedPlayButtonSize, expandedPlayButtonSize, clampedExpansionFraction)
            val collapsedPlayButtonX = screenWidth - 12.dp - collapsedPlayButtonSize
            val collapsedPlayButtonY = (MobileMiniPlayerChromeHeight - collapsedPlayButtonSize) / 2f
            val expandedPlayButtonX = (screenWidth - expandedPlayButtonSize) / 2f
            val expandedPlayButtonY = screenHeight - collapsedSheetHeight - 12.dp - expandedPlayButtonSize
            val collapsedCastButtonX = collapsedPlayButtonX - 50.dp
            val collapsedCastButtonY = collapsedPlayButtonY
            val expandedCastButtonX = screenWidth - 20.dp - 40.dp
            val expandedCastButtonY = statusBarTopPadding + 8.dp
            val swipeProgress = (abs(currentSwipeOffset) / swipeThresholdPx).coerceIn(0f, 1f)
            val swipeScale = if (clampedExpansionFraction < 0.1f) 1f - swipeProgress * 0.025f else 1f
            val playButtonAlpha = when {
                track == null -> fullPlayerElementsAlpha
                clampedExpansionFraction < 0.1f -> miniPlayerAlpha * (1f - swipeProgress * 0.14f)
                else -> 1f
            }
            val castButtonAlpha = when {
                track == null -> fullPlayerElementsAlpha
                clampedExpansionFraction < 0.1f -> miniPlayerAlpha * (1f - swipeProgress * 0.14f)
                else -> 1f
            }

            if (castState.isConnected) {
                CastIcon(
                    active = true,
                    loading = castState.isBuffering,
                    enabled = true,
                    onClick = onCast,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = lerp(collapsedCastButtonX, expandedCastButtonX, clampedExpansionFraction).roundToPx(),
                                y = lerp(collapsedCastButtonY, expandedCastButtonY, clampedExpansionFraction).roundToPx(),
                            )
                        }
                        .graphicsLayer {
                            alpha = castButtonAlpha
                            translationX = if (clampedExpansionFraction < 0.1f) currentSwipeOffset else 0f
                            scaleX = swipeScale
                            scaleY = swipeScale
                        }
                        .then(if (clampedExpansionFraction < 0.1f) horizontalDragModifier else Modifier),
                )
            }

            PlayButton(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                size = playButtonSize,
                onClick = onToggle,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = lerp(collapsedPlayButtonX, expandedPlayButtonX, clampedExpansionFraction).roundToPx(),
                            y = lerp(collapsedPlayButtonY, expandedPlayButtonY, clampedExpansionFraction).roundToPx(),
                        )
                    }
                    .graphicsLayer {
                        alpha = playButtonAlpha
                        translationX = if (clampedExpansionFraction < 0.1f) currentSwipeOffset else 0f
                        scaleX = swipeScale
                        scaleY = swipeScale
                    }
                    .then(if (clampedExpansionFraction < 0.1f) horizontalDragModifier else Modifier),
                enabled = track != null,
            )
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
