package com.phoebe.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoebe.app.domain.DownloadState
import com.phoebe.app.domain.Track

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlippableSongArtwork(
    track: Track,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    shape: Shape = RoundedCornerShape(radius),
    maxDecodeDimension: Int = ListArtworkMaxDecodeDimension,
    onFlipRotationChange: (Float) -> Unit = {},
    frontOverlay: @Composable BoxScope.() -> Unit = {},
) {
    var showingDetails by remember(track.id) { mutableStateOf(false) }
    val latestOnFlipRotationChange by rememberUpdatedState(onFlipRotationChange)
    val rotation by animateFloatAsState(
        targetValue = if (showingDetails) 180f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "songArtworkFlip",
    )
    val density = LocalDensity.current
    LaunchedEffect(rotation) {
        latestOnFlipRotationChange(rotation)
    }
    DisposableEffect(track.id) {
        onDispose {
            latestOnFlipRotationChange(0f)
        }
    }

    Box(
        modifier
            .aspectRatio(1f)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density.density
            }
            .clip(shape),
    ) {
        if (rotation <= 90f) {
            TrackArtworkImage(
                track = track,
                modifier = Modifier
                    .fillMaxSize()
                    .then(artworkModifier)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showingDetails = true },
                    ),
                radius = radius,
                shape = shape,
                maxDecodeDimension = maxDecodeDimension,
            )
            frontOverlay()
        } else {
            SongArtworkDetailBack(
                track = track,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .clickable { showingDetails = false },
                shape = shape,
            )
        }
    }
}

@Composable
private fun SongArtworkDetailBack(
    track: Track,
    modifier: Modifier,
    shape: Shape,
) {
    val nowMs = LocalNowMs.current
    val playHistory = LocalPlayHistory.current
    val lastPlayed = playHistory.byTrack[track.id]

    Column(
        modifier
            .clip(shape)
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), shape)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            track.title,
            color = PhoebeUi.primaryText,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SongDetailMetadataRows(
            track = track,
            nowMs = nowMs,
            lastPlayed = lastPlayed,
            playCount = playHistory.playCountByTrack[track.id] ?: 0L,
            labelWidth = 72.dp,
            labelFontSize = 10.sp,
            valueFontSize = 11.sp,
        )
        Text("Tap to show artwork", color = PhoebeUi.mutedText, fontSize = 11.sp)
    }
}

@Composable
fun SongDetailMetadataRows(
    track: Track,
    nowMs: Long,
    lastPlayed: Long?,
    playCount: Long,
    labelWidth: Dp = 108.dp,
    labelFontSize: TextUnit = 12.sp,
    valueFontSize: TextUnit = 13.sp,
) {
    val downloads = LocalDownloadStatus.current
    val downloadedFile = downloads.itemFor(track)
        ?.takeIf { it.state == DownloadState.Complete && !it.localUri.isNullOrBlank() }
        ?.localUri
    DetailMetaRow("Artist", track.artist, labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Album", track.album, labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Duration", formatDuration(track.durationMs), labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Year", track.year?.toString() ?: "Unknown", labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Genre", track.genre ?: "Unknown", labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Date Added", track.dateAddedMs?.let { formatLastPlayed(it, nowMs) } ?: "Unknown", labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Last Played", lastPlayed?.let { formatLastPlayed(it, nowMs) } ?: "Never", labelWidth, labelFontSize, valueFontSize)
    DetailMetaRow("Plays", playCount.toString(), labelWidth, labelFontSize, valueFontSize)
    track.audioCodec?.let { DetailMetaRow("Codec", it.uppercase(), labelWidth, labelFontSize, valueFontSize) }
    track.bitrateKbps?.let { DetailMetaRow("Bitrate", "$it kbps", labelWidth, labelFontSize, valueFontSize) }
    downloadedFile?.let { DetailMetaRow("Downloaded File", displayFileUri(it), labelWidth, labelFontSize, valueFontSize) }
    track.filepath?.let { DetailMetaRow("File", it, labelWidth, labelFontSize, valueFontSize) }
}

private fun displayFileUri(uri: String): String =
    uri.removePrefix("file:")
        .removePrefix("//")
        .replace("%20", " ")
        .ifBlank { uri }

@Composable
private fun DetailMetaRow(
    label: String,
    value: String,
    labelWidth: Dp = 108.dp,
    labelFontSize: TextUnit = 12.sp,
    valueFontSize: TextUnit = 13.sp,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PhoebeUi.mutedText, fontSize = labelFontSize, modifier = Modifier.width(labelWidth))
        Text(value, color = PhoebeUi.primaryText, fontSize = valueFontSize, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
