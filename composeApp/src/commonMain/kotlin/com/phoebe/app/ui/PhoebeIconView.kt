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
import phoebe.composeapp.generated.resources.phoebe_icon_back
import phoebe.composeapp.generated.resources.phoebe_icon_bell
import phoebe.composeapp.generated.resources.phoebe_icon_book
import phoebe.composeapp.generated.resources.phoebe_icon_calendar
import phoebe.composeapp.generated.resources.phoebe_icon_cast
import phoebe.composeapp.generated.resources.phoebe_icon_chevron_down
import phoebe.composeapp.generated.resources.phoebe_icon_chevron_right
import phoebe.composeapp.generated.resources.phoebe_icon_chevron_up
import phoebe.composeapp.generated.resources.phoebe_icon_download
import phoebe.composeapp.generated.resources.phoebe_icon_drag
import phoebe.composeapp.generated.resources.phoebe_icon_equalizer
import phoebe.composeapp.generated.resources.phoebe_icon_forward
import phoebe.composeapp.generated.resources.phoebe_icon_heart_filled
import phoebe.composeapp.generated.resources.phoebe_icon_heart_outline
import phoebe.composeapp.generated.resources.phoebe_icon_home
import phoebe.composeapp.generated.resources.phoebe_icon_interwoven_arrows
import phoebe.composeapp.generated.resources.phoebe_icon_knife
import phoebe.composeapp.generated.resources.phoebe_icon_library
import phoebe.composeapp.generated.resources.phoebe_icon_lyrics
import phoebe.composeapp.generated.resources.phoebe_icon_music
import phoebe.composeapp.generated.resources.phoebe_icon_next
import phoebe.composeapp.generated.resources.phoebe_icon_person
import phoebe.composeapp.generated.resources.phoebe_icon_plus
import phoebe.composeapp.generated.resources.phoebe_icon_previous
import phoebe.composeapp.generated.resources.phoebe_icon_queue
import phoebe.composeapp.generated.resources.phoebe_icon_repeat
import phoebe.composeapp.generated.resources.phoebe_icon_rounded
import phoebe.composeapp.generated.resources.phoebe_icon_search
import phoebe.composeapp.generated.resources.phoebe_icon_settings
import phoebe.composeapp.generated.resources.phoebe_icon_sunglasses_face
import phoebe.composeapp.generated.resources.phoebe_icon_thumbs_down
import phoebe.composeapp.generated.resources.phoebe_icon_thumbs_up
import phoebe.composeapp.generated.resources.phoebe_icon_volume
import org.jetbrains.compose.resources.DrawableResource
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
    icon.drawableResource(filled)?.let { resource ->
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = modifier,
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
        )
        return
    }

    Canvas(modifier) {
        val s = size.minDimension
        val strokeWidth = (s * 0.073f).coerceAtLeast(1.35f)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(s * x, s * y)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)

        when (icon) {
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
            PhoebeIcon.Visualizer -> {
                val outerRadius = s * 0.42f
                val strokeW = (s * 0.065f).coerceAtLeast(1.5f)
                val barW = (s * 0.065f).coerceAtLeast(1.5f)
                // Draw outer circle
                drawCircle(
                    color = tint,
                    radius = outerRadius,
                    center = p(0.50f, 0.50f),
                    style = Stroke(width = strokeW),
                )
                // Draw five vertical bars with round caps
                val spacing = 0.11f
                val barHeights = listOf(0.26f, 0.42f, 0.56f, 0.42f, 0.26f)
                repeat(5) { i ->
                    val x = 0.50f + (i - 2) * spacing
                    val h = barHeights[i]
                    drawLine(
                        color = tint,
                        start = p(x, 0.50f - h / 2f),
                        end = p(x, 0.50f + h / 2f),
                        strokeWidth = barW,
                        cap = StrokeCap.Round,
                    )
                }
            }
            else -> Unit
        }
    }
}

private fun PhoebeIcon.drawableResource(filled: Boolean): DrawableResource? =
    when (this) {
        PhoebeIcon.Home -> Res.drawable.phoebe_icon_home
        PhoebeIcon.Search -> Res.drawable.phoebe_icon_search
        PhoebeIcon.Library -> Res.drawable.phoebe_icon_library
        PhoebeIcon.Person -> Res.drawable.phoebe_icon_person
        PhoebeIcon.Calendar -> Res.drawable.phoebe_icon_calendar
        PhoebeIcon.Book -> Res.drawable.phoebe_icon_book
        PhoebeIcon.Knife -> Res.drawable.phoebe_icon_knife
        PhoebeIcon.InterwovenArrows -> Res.drawable.phoebe_icon_interwoven_arrows
        PhoebeIcon.MoodFace -> Res.drawable.mood_very_good
        PhoebeIcon.SunglassesFace -> Res.drawable.phoebe_icon_sunglasses_face
        PhoebeIcon.GenreMasks -> Res.drawable.drama_masks
        PhoebeIcon.Settings -> Res.drawable.phoebe_icon_settings
        PhoebeIcon.Plus -> Res.drawable.phoebe_icon_plus
        PhoebeIcon.Heart -> if (filled) Res.drawable.phoebe_icon_heart_filled else Res.drawable.phoebe_icon_heart_outline
        PhoebeIcon.ThumbsUp -> Res.drawable.phoebe_icon_thumbs_up
        PhoebeIcon.ThumbsDown -> Res.drawable.phoebe_icon_thumbs_down
        PhoebeIcon.ChevronUp -> Res.drawable.phoebe_icon_chevron_up
        PhoebeIcon.ChevronDown -> Res.drawable.phoebe_icon_chevron_down
        PhoebeIcon.ChevronRight -> Res.drawable.phoebe_icon_chevron_right
        PhoebeIcon.Bell -> Res.drawable.phoebe_icon_bell
        PhoebeIcon.Back -> Res.drawable.phoebe_icon_back
        PhoebeIcon.Forward -> Res.drawable.phoebe_icon_forward
        PhoebeIcon.Music -> Res.drawable.phoebe_icon_music
        PhoebeIcon.Lyrics -> Res.drawable.phoebe_icon_lyrics
        PhoebeIcon.Previous -> Res.drawable.phoebe_icon_previous
        PhoebeIcon.Next -> Res.drawable.phoebe_icon_next
        PhoebeIcon.Volume -> Res.drawable.phoebe_icon_volume
        PhoebeIcon.Equalizer -> Res.drawable.phoebe_icon_equalizer
        PhoebeIcon.Queue -> Res.drawable.phoebe_icon_queue
        PhoebeIcon.Cast -> Res.drawable.phoebe_icon_cast
        PhoebeIcon.Download -> Res.drawable.phoebe_icon_download
        PhoebeIcon.Repeat -> Res.drawable.phoebe_icon_repeat
        PhoebeIcon.Drag -> Res.drawable.phoebe_icon_drag
        PhoebeIcon.PlaylistPlay,
        PhoebeIcon.Play,
        PhoebeIcon.Pause,
        PhoebeIcon.More,
        PhoebeIcon.ActiveDot,
        PhoebeIcon.Grid,
        PhoebeIcon.Close,
        PhoebeIcon.Check,
        PhoebeIcon.Visualizer,
        -> null
    }
