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
import phoebe.composeapp.generated.resources.drama_masks
import phoebe.composeapp.generated.resources.mood_very_good
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.phoebe.app.domain.isRemoteLibraryTrack
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
internal fun PhoebeIconView(
    icon: PhoebeIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    when (icon) {
        PhoebeIcon.MoodFace -> {
            Image(
                painter = painterResource(Res.drawable.mood_very_good),
                contentDescription = null,
                modifier = modifier,
                colorFilter = ColorFilter.tint(tint),
            )
            return
        }
        PhoebeIcon.GenreMasks -> {
            Image(
                painter = painterResource(Res.drawable.drama_masks),
                contentDescription = null,
                modifier = modifier,
                colorFilter = ColorFilter.tint(tint),
            )
            return
        }
        else -> Unit
    }
    Canvas(modifier) {
        val s = size.minDimension
        val strokeWidth = (s * 0.073f).coerceAtLeast(1.35f)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(s * x, s * y)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)

        when (icon) {
            PhoebeIcon.Home -> {
                val roof = Path().apply {
                    moveTo(s * 0.18f, s * 0.50f)
                    lineTo(s * 0.50f, s * 0.22f)
                    lineTo(s * 0.82f, s * 0.50f)
                }
                drawPath(roof, tint, style = stroke)
                line(0.23f, 0.48f, 0.23f, 0.78f)
                line(0.77f, 0.48f, 0.77f, 0.78f)
                line(0.23f, 0.78f, 0.42f, 0.78f)
                line(0.58f, 0.78f, 0.77f, 0.78f)
                line(0.42f, 0.78f, 0.42f, 0.62f)
                line(0.58f, 0.78f, 0.58f, 0.62f)
                line(0.42f, 0.62f, 0.58f, 0.62f)
            }
            PhoebeIcon.Search -> {
                drawCircle(tint, radius = s * 0.25f, center = p(0.43f, 0.41f), style = stroke)
                line(0.61f, 0.60f, 0.80f, 0.79f)
            }
            PhoebeIcon.Library -> {
                line(0.30f, 0.22f, 0.30f, 0.78f)
                line(0.50f, 0.22f, 0.50f, 0.78f)
                line(0.68f, 0.26f, 0.82f, 0.76f)
            }
            PhoebeIcon.Person -> {
                drawCircle(tint, radius = s * 0.15f, center = p(0.50f, 0.33f), style = stroke)
                val shoulders = Path().apply {
                    moveTo(s * 0.23f, s * 0.80f)
                    cubicTo(s * 0.28f, s * 0.58f, s * 0.72f, s * 0.58f, s * 0.77f, s * 0.80f)
                }
                drawPath(shoulders, tint, style = stroke)
            }
            PhoebeIcon.Calendar -> {
                val heavyStroke = Stroke(width = strokeWidth * 1.35f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.19f, s * 0.25f),
                    size = Size(s * 0.62f, s * 0.58f),
                    cornerRadius = CornerRadius(s * 0.11f, s * 0.11f),
                    style = heavyStroke,
                )
                drawLine(tint, p(0.20f, 0.42f), p(0.80f, 0.42f), strokeWidth = strokeWidth * 1.55f, cap = StrokeCap.Butt)
                listOf(0.32f, 0.44f, 0.56f, 0.68f).forEach { x ->
                    drawLine(tint, p(x, 0.17f), p(x, 0.30f), strokeWidth = strokeWidth * 1.55f, cap = StrokeCap.Round)
                }
                listOf(0.37f to 0.56f, 0.50f to 0.56f, 0.63f to 0.56f, 0.37f to 0.69f, 0.50f to 0.69f, 0.63f to 0.69f).forEach { (x, y) ->
                    drawRoundRect(
                        tint,
                        topLeft = Offset(s * (x - 0.032f), s * (y - 0.032f)),
                        size = Size(s * 0.064f, s * 0.064f),
                        cornerRadius = CornerRadius(s * 0.006f, s * 0.006f),
                    )
                }
            }
            PhoebeIcon.Book -> {
                val left = Path().apply {
                    moveTo(s * 0.18f, s * 0.25f)
                    cubicTo(s * 0.30f, s * 0.20f, s * 0.42f, s * 0.24f, s * 0.50f, s * 0.32f)
                    lineTo(s * 0.50f, s * 0.78f)
                    cubicTo(s * 0.40f, s * 0.70f, s * 0.29f, s * 0.67f, s * 0.18f, s * 0.72f)
                    close()
                }
                val right = Path().apply {
                    moveTo(s * 0.82f, s * 0.25f)
                    cubicTo(s * 0.70f, s * 0.20f, s * 0.58f, s * 0.24f, s * 0.50f, s * 0.32f)
                    lineTo(s * 0.50f, s * 0.78f)
                    cubicTo(s * 0.60f, s * 0.70f, s * 0.71f, s * 0.67f, s * 0.82f, s * 0.72f)
                    close()
                }
                drawPath(left, tint, style = stroke)
                drawPath(right, tint, style = stroke)
                line(0.50f, 0.32f, 0.50f, 0.78f)
            }
            PhoebeIcon.Knife -> {
                val heavyStroke = Stroke(width = strokeWidth * 1.35f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val handle = Path().apply {
                    moveTo(s * 0.28f, s * 0.13f)
                    lineTo(s * 0.55f, s * 0.40f)
                    lineTo(s * 0.47f, s * 0.48f)
                    lineTo(s * 0.18f, s * 0.20f)
                    lineTo(s * 0.25f, s * 0.13f)
                    close()
                }
                val blade = Path().apply {
                    moveTo(s * 0.53f, s * 0.42f)
                    cubicTo(s * 0.66f, s * 0.52f, s * 0.82f, s * 0.71f, s * 0.90f, s * 0.90f)
                    cubicTo(s * 0.65f, s * 0.85f, s * 0.45f, s * 0.72f, s * 0.34f, s * 0.58f)
                    lineTo(s * 0.46f, s * 0.46f)
                    close()
                }
                drawPath(handle, tint)
                drawPath(blade, tint, style = heavyStroke)
                line(0.50f, 0.43f, 0.87f, 0.86f)
            }
            PhoebeIcon.InterwovenArrows -> {
                val shuffleStroke = Stroke(width = strokeWidth * 1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val upper = Path().apply {
                    moveTo(s * 0.15f, s * 0.32f)
                    lineTo(s * 0.26f, s * 0.32f)
                    cubicTo(s * 0.42f, s * 0.32f, s * 0.44f, s * 0.68f, s * 0.61f, s * 0.68f)
                    lineTo(s * 0.77f, s * 0.68f)
                }
                val lower = Path().apply {
                    moveTo(s * 0.15f, s * 0.68f)
                    lineTo(s * 0.26f, s * 0.68f)
                    cubicTo(s * 0.42f, s * 0.68f, s * 0.44f, s * 0.32f, s * 0.61f, s * 0.32f)
                    lineTo(s * 0.77f, s * 0.32f)
                }
                val upperArrow = Path().apply {
                    moveTo(s * 0.77f, s * 0.56f)
                    lineTo(s * 0.91f, s * 0.68f)
                    lineTo(s * 0.77f, s * 0.80f)
                    close()
                }
                val lowerArrow = Path().apply {
                    moveTo(s * 0.77f, s * 0.20f)
                    lineTo(s * 0.91f, s * 0.32f)
                    lineTo(s * 0.77f, s * 0.44f)
                    close()
                }
                drawPath(upper, tint, style = shuffleStroke)
                drawPath(lower, tint, style = shuffleStroke)
                drawPath(upperArrow, tint)
                drawPath(lowerArrow, tint)
            }
            PhoebeIcon.MoodFace -> {
                val faceStroke = Stroke(width = strokeWidth * 1.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircle(tint, radius = s * 0.23f, center = p(0.32f, 0.66f), style = faceStroke)
                drawCircle(tint, radius = s * 0.23f, center = p(0.70f, 0.30f), style = faceStroke)
                drawArc(tint, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(s * 0.62f, s * 0.26f), size = Size(s * 0.08f, s * 0.07f), style = faceStroke)
                drawArc(tint, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(s * 0.78f, s * 0.26f), size = Size(s * 0.08f, s * 0.07f), style = faceStroke)
                drawArc(tint, startAngle = 25f, sweepAngle = 130f, useCenter = false, topLeft = Offset(s * 0.61f, s * 0.33f), size = Size(s * 0.19f, s * 0.15f), style = faceStroke)
                drawArc(tint, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(s * 0.22f, s * 0.61f), size = Size(s * 0.08f, s * 0.07f), style = faceStroke)
                drawArc(tint, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(s * 0.38f, s * 0.61f), size = Size(s * 0.08f, s * 0.07f), style = faceStroke)
                drawArc(tint, startAngle = 205f, sweepAngle = 130f, useCenter = false, topLeft = Offset(s * 0.23f, s * 0.72f), size = Size(s * 0.19f, s * 0.15f), style = faceStroke)
                val upperArrow = Path().apply {
                    moveTo(s * 0.27f, s * 0.36f)
                    cubicTo(s * 0.27f, s * 0.24f, s * 0.38f, s * 0.20f, s * 0.48f, s * 0.20f)
                    lineTo(s * 0.52f, s * 0.20f)
                }
                val lowerArrow = Path().apply {
                    moveTo(s * 0.74f, s * 0.57f)
                    cubicTo(s * 0.74f, s * 0.70f, s * 0.61f, s * 0.75f, s * 0.50f, s * 0.75f)
                    lineTo(s * 0.47f, s * 0.75f)
                }
                drawPath(upperArrow, tint, style = faceStroke)
                drawPath(lowerArrow, tint, style = faceStroke)
                line(0.52f, 0.20f, 0.46f, 0.14f)
                line(0.52f, 0.20f, 0.46f, 0.26f)
                line(0.47f, 0.75f, 0.53f, 0.69f)
                line(0.47f, 0.75f, 0.53f, 0.81f)
            }
            PhoebeIcon.SunglassesFace -> {
                drawCircle(tint, radius = s * 0.34f, center = p(0.50f, 0.50f), style = stroke)
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.23f, s * 0.36f),
                    size = Size(s * 0.24f, s * 0.15f),
                    cornerRadius = CornerRadius(s * 0.035f, s * 0.035f),
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.53f, s * 0.36f),
                    size = Size(s * 0.24f, s * 0.15f),
                    cornerRadius = CornerRadius(s * 0.035f, s * 0.035f),
                )
                drawLine(tint, p(0.18f, 0.36f), p(0.82f, 0.36f), strokeWidth = strokeWidth * 1.45f, cap = StrokeCap.Round)
                drawArc(tint, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(s * 0.35f, s * 0.48f), size = Size(s * 0.30f, s * 0.22f), style = Stroke(width = strokeWidth * 1.15f, cap = StrokeCap.Round))
            }
            PhoebeIcon.GenreMasks -> {
                val sad = Path().apply {
                    moveTo(s * 0.23f, s * 0.17f)
                    lineTo(s * 0.56f, s * 0.10f)
                    cubicTo(s * 0.62f, s * 0.27f, s * 0.60f, s * 0.49f, s * 0.46f, s * 0.65f)
                    cubicTo(s * 0.34f, s * 0.59f, s * 0.22f, s * 0.45f, s * 0.16f, s * 0.26f)
                    cubicTo(s * 0.14f, s * 0.21f, s * 0.17f, s * 0.18f, s * 0.23f, s * 0.17f)
                    close()
                }
                val happy = Path().apply {
                    moveTo(s * 0.54f, s * 0.40f)
                    lineTo(s * 0.86f, s * 0.52f)
                    cubicTo(s * 0.80f, s * 0.75f, s * 0.63f, s * 0.90f, s * 0.45f, s * 0.86f)
                    cubicTo(s * 0.38f, s * 0.70f, s * 0.42f, s * 0.52f, s * 0.48f, s * 0.42f)
                    cubicTo(s * 0.50f, s * 0.39f, s * 0.52f, s * 0.39f, s * 0.54f, s * 0.40f)
                    close()
                }
                drawPath(sad, tint, style = stroke)
                drawPath(happy, tint, style = stroke)
                val leftEye = Path().apply {
                    moveTo(s * 0.28f, s * 0.35f)
                    cubicTo(s * 0.35f, s * 0.30f, s * 0.40f, s * 0.30f, s * 0.45f, s * 0.34f)
                    cubicTo(s * 0.40f, s * 0.39f, s * 0.34f, s * 0.40f, s * 0.28f, s * 0.35f)
                    close()
                }
                val rightEye = Path().apply {
                    moveTo(s * 0.52f, s * 0.29f)
                    cubicTo(s * 0.58f, s * 0.24f, s * 0.65f, s * 0.25f, s * 0.70f, s * 0.31f)
                    cubicTo(s * 0.64f, s * 0.36f, s * 0.58f, s * 0.36f, s * 0.52f, s * 0.29f)
                    close()
                }
                val happyLeftEye = Path().apply {
                    moveTo(s * 0.55f, s * 0.59f)
                    cubicTo(s * 0.62f, s * 0.56f, s * 0.68f, s * 0.59f, s * 0.71f, s * 0.65f)
                    cubicTo(s * 0.64f, s * 0.67f, s * 0.59f, s * 0.65f, s * 0.55f, s * 0.59f)
                    close()
                }
                val happyRightEye = Path().apply {
                    moveTo(s * 0.72f, s * 0.64f)
                    cubicTo(s * 0.78f, s * 0.62f, s * 0.84f, s * 0.66f, s * 0.86f, s * 0.72f)
                    cubicTo(s * 0.79f, s * 0.72f, s * 0.75f, s * 0.69f, s * 0.72f, s * 0.64f)
                    close()
                }
                drawPath(leftEye, tint)
                drawPath(rightEye, tint)
                drawPath(happyLeftEye, tint)
                drawPath(happyRightEye, tint)
                drawArc(tint, startAngle = 205f, sweepAngle = 115f, useCenter = false, topLeft = Offset(s * 0.30f, s * 0.48f), size = Size(s * 0.18f, s * 0.17f), style = stroke)
                drawArc(tint, startAngle = 25f, sweepAngle = 125f, useCenter = false, topLeft = Offset(s * 0.52f, s * 0.67f), size = Size(s * 0.27f, s * 0.15f), style = stroke)
            }
            PhoebeIcon.PlaylistPlay -> {
                line(0.18f, 0.26f, 0.82f, 0.26f)
                line(0.18f, 0.44f, 0.48f, 0.44f)
                line(0.18f, 0.62f, 0.48f, 0.62f)
                line(0.18f, 0.80f, 0.48f, 0.80f)
                val play = Path().apply {
                    moveTo(s * 0.62f, s * 0.45f)
                    lineTo(s * 0.62f, s * 0.80f)
                    lineTo(s * 0.86f, s * 0.63f)
                    close()
                }
                drawPath(play, tint, style = stroke)
            }
            PhoebeIcon.Queue -> {
                val play = Path().apply {
                    moveTo(s * 0.24f, s * 0.54f)
                    lineTo(s * 0.24f, s * 0.72f)
                    lineTo(s * 0.38f, s * 0.63f)
                    close()
                }
                drawPath(play, tint, style = androidx.compose.ui.graphics.drawscope.Fill)
                drawCircle(tint, radius = s * 0.035f, center = p(0.25f, 0.31f))
                line(0.42f, 0.30f, 0.78f, 0.30f)
                line(0.42f, 0.47f, 0.78f, 0.47f)
                line(0.42f, 0.64f, 0.78f, 0.64f)
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
            PhoebeIcon.ChevronRight -> {
                line(0.38f, 0.25f, 0.62f, 0.50f)
                line(0.62f, 0.50f, 0.38f, 0.75f)
            }
            PhoebeIcon.Bell -> {
                val body = Path().apply {
                    moveTo(s * 0.30f, s * 0.68f)
                    cubicTo(s * 0.36f, s * 0.62f, s * 0.35f, s * 0.50f, s * 0.35f, s * 0.42f)
                    cubicTo(s * 0.35f, s * 0.25f, s * 0.45f, s * 0.20f, s * 0.50f, s * 0.20f)
                    cubicTo(s * 0.55f, s * 0.20f, s * 0.65f, s * 0.25f, s * 0.65f, s * 0.42f)
                    cubicTo(s * 0.65f, s * 0.50f, s * 0.64f, s * 0.62f, s * 0.70f, s * 0.68f)
                    lineTo(s * 0.30f, s * 0.68f)
                }
                drawPath(body, tint, style = stroke)
                line(0.46f, 0.78f, 0.54f, 0.78f)
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
                line(0.48f, 0.30f, 0.48f, 0.72f)
                line(0.76f, 0.22f, 0.76f, 0.64f)
                line(0.48f, 0.30f, 0.76f, 0.22f)
                line(0.48f, 0.42f, 0.76f, 0.34f)
                drawCircle(tint, radius = s * 0.095f, center = p(0.36f, 0.75f), style = stroke)
                drawCircle(tint, radius = s * 0.095f, center = p(0.64f, 0.67f), style = stroke)
            }
            PhoebeIcon.Lyrics -> {
                line(0.25f, 0.28f, 0.75f, 0.28f)
                line(0.20f, 0.45f, 0.80f, 0.45f)
                line(0.28f, 0.62f, 0.72f, 0.62f)
                drawCircle(tint, radius = s * 0.035f, center = p(0.18f, 0.28f))
                drawCircle(tint, radius = s * 0.035f, center = p(0.82f, 0.62f))
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
                    lineTo(s * 0.33f, s * 0.42f)
                    lineTo(s * 0.53f, s * 0.27f)
                    lineTo(s * 0.53f, s * 0.73f)
                    lineTo(s * 0.33f, s * 0.58f)
                    lineTo(s * 0.18f, s * 0.58f)
                    close()
                }
                drawPath(speaker, tint, style = stroke)
                drawArc(tint, startAngle = -38f, sweepAngle = 76f, useCenter = false, topLeft = Offset(s * 0.54f, s * 0.38f), size = Size(s * 0.20f, s * 0.24f), style = stroke)
                drawArc(tint, startAngle = -43f, sweepAngle = 86f, useCenter = false, topLeft = Offset(s * 0.58f, s * 0.28f), size = Size(s * 0.30f, s * 0.44f), style = stroke)
            }
            PhoebeIcon.Cast -> {
                line(0.20f, 0.28f, 0.80f, 0.28f)
                line(0.80f, 0.28f, 0.80f, 0.70f)
                line(0.20f, 0.70f, 0.80f, 0.70f)
                drawCircle(tint, radius = s * 0.025f, center = p(0.22f, 0.78f))
                drawArc(tint, startAngle = -90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(s * 0.12f, s * 0.58f), size = Size(s * 0.28f, s * 0.28f), style = stroke)
                drawArc(tint, startAngle = -90f, sweepAngle = 90f, useCenter = false, topLeft = Offset(s * 0.02f, s * 0.48f), size = Size(s * 0.48f, s * 0.48f), style = stroke)
            }
            PhoebeIcon.Download -> {
                line(0.50f, 0.18f, 0.50f, 0.62f)
                line(0.32f, 0.46f, 0.50f, 0.64f)
                line(0.68f, 0.46f, 0.50f, 0.64f)
                line(0.24f, 0.78f, 0.76f, 0.78f)
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
            PhoebeIcon.Check -> {
                line(0.22f, 0.52f, 0.42f, 0.72f)
                line(0.42f, 0.72f, 0.80f, 0.30f)
            }
            PhoebeIcon.Settings -> {
                val center = p(0.5f, 0.5f)
                val gear = Path()
                repeat(16) { i ->
                    val radius = if (i % 2 == 0) s * 0.36f else s * 0.27f
                    val angle = -kotlin.math.PI.toFloat() / 2f + i * kotlin.math.PI.toFloat() / 8f
                    val point = Offset(
                        center.x + radius * kotlin.math.cos(angle.toDouble()).toFloat(),
                        center.y + radius * kotlin.math.sin(angle.toDouble()).toFloat(),
                    )
                    if (i == 0) {
                        gear.moveTo(point.x, point.y)
                    } else {
                        gear.lineTo(point.x, point.y)
                    }
                }
                gear.close()
                drawPath(gear, tint, style = stroke)
                drawCircle(tint, radius = s * 0.12f, center = center, style = stroke)
            }
        }
    }
}
