package com.phoebe.app.feature.playback

import com.phoebe.app.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phoebe.app.data.ListenBrainzFeedbackScore
import com.phoebe.app.data.ListenBrainzFeedbackTarget
import com.phoebe.app.domain.Track
import com.phoebe.app.platform.prefersReducedArtworkEffects
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

fun lerp(start: Dp, stop: Dp, fraction: Float): Dp {
    return Dp(start.value + fraction * (stop.value - start.value))
}

fun Modifier.playerDragGestures(
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
        },
    )
}

@Composable
fun MobileArtworkReflection(
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

fun Modifier.mobileArtworkBottomFade(fadeHeight: Dp): Modifier {
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
fun MobileArtworkMetadataScrim(
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
                        .then(
                            if (prefersReducedArtworkEffects()) {
                                Modifier
                            } else {
                                Modifier.hazeEffect {
                                    inputScale = HazeInputScale.Auto
                                    blurEffect {
                                        blurRadius = 20.dp
                                        progressive = HazeProgressive.verticalGradient(
                                            startIntensity = 1f,
                                            endIntensity = 0.12f,
                                        )
                                        noiseFactor = 0f
                                    }
                                }
                            }
                        ),
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
fun BoxScope.MobileNowPlayingOverlayActions(
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
