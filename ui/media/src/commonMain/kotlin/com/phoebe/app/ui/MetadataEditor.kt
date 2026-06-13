package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import kotlin.math.roundToInt

private val MetadataEditorDismissDragThreshold = 96.dp

@Composable
fun MetadataEditorOverlay(
    track: Track,
    compact: Boolean,
    onDismiss: () -> Unit,
    onSave: (TrackMetadataUpdate) -> Unit,
) {
    var form by remember(track.id) { mutableStateOf(MetadataEditorForm.from(track)) }
    val saveEnabled = form.title.isNotBlank() && form.artist.isNotBlank() && form.album.isNotBlank()
    val content: @Composable (Modifier) -> Unit = { modifier ->
        MetadataEditorPanel(
            track = track,
            form = form,
            onForm = { form = it },
            saveEnabled = saveEnabled,
            compact = compact,
            modifier = modifier,
            onDismiss = onDismiss,
            onSave = { onSave(form.toUpdate(track.id)) },
        )
    }

    if (compact) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            content(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {}),
            )
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            content(Modifier.widthIn(min = 760.dp, max = 860.dp))
        }
    }
}

@Composable
private fun MetadataEditorPanel(
    track: Track,
    form: MetadataEditorForm,
    onForm: (MetadataEditorForm) -> Unit,
    saveEnabled: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val shape = if (compact) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    } else {
        RoundedCornerShape(18.dp)
    }
    val density = LocalDensity.current
    val dismissDragThresholdPx = with(density) { MetadataEditorDismissDragThreshold.toPx() }
    var dragOffsetPx by remember(track.id, compact) { mutableFloatStateOf(0f) }
    Column(
        modifier
            .then(
                if (compact) {
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, dragOffsetPx.roundToInt())
                        }
                    }
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(PhoebeUi.panel)
            .border(BorderStroke(1.dp, PhoebeUi.border), shape)
            .padding(if (compact) 18.dp else 24.dp)
            .heightIn(max = if (compact) 680.dp else 640.dp),
    ) {
        if (compact) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .semantics { contentDescription = "Dismiss metadata editor" }
                    .pointerInput(dismissDragThresholdPx, onDismiss) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                if (dragOffsetPx >= dismissDragThresholdPx) {
                                    onDismiss()
                                } else {
                                    dragOffsetPx = 0f
                                }
                            },
                            onDragCancel = {
                                dragOffsetPx = 0f
                            },
                        )
                    }
                    .clickable {
                        if (dragOffsetPx >= dismissDragThresholdPx) {
                            onDismiss()
                        } else {
                            dragOffsetPx = 0f
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 42.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.weight(1f, fill = false) else Modifier)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MetadataEditorHeader(compact = compact, onDismiss = onDismiss)
            if (compact) {
                MetadataEditorArtwork(track, Modifier.fillMaxWidth())
                MetadataEditorFields(form, onForm)
                TechnicalMetadata(track)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    MetadataEditorArtwork(track, Modifier.width(230.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        MetadataEditorFields(form, onForm)
                        TechnicalMetadata(track)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataEditorButton("Cancel", onDismiss, primary = false)
                MetadataEditorButton("Save Changes", onSave, primary = true, enabled = saveEnabled)
            }
            if (compact) {
                Spacer(
                    Modifier
                        .height(6.dp)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun MetadataEditorHeader(compact: Boolean, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Edit Metadata",
                color = PhoebeUi.primaryText,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Update library fields and sync supported streaming songs.",
                color = PhoebeUi.secondaryText,
                fontSize = 12.sp,
            )
        }
        Text(
            "✕",
            color = PhoebeUi.mutedText,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onDismiss)
                .padding(10.dp),
        )
    }
}

@Composable
private fun MetadataEditorArtwork(track: Track, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Artwork", color = PhoebeUi.mutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackArtworkImage(track, Modifier.size(132.dp), radius = 12.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AutoScrollingText(track.title, color = PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                AutoScrollingText(track.artist, color = PhoebeUi.secondaryText, fontSize = 12.sp)
                Text("Artwork changes are not editable yet.", color = PhoebeUi.mutedText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MetadataEditorFields(
    form: MetadataEditorForm,
    onForm: (MetadataEditorForm) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetadataTextField("Title", form.title) { onForm(form.copy(title = it)) }
        MetadataTextField("Artist", form.artist) { onForm(form.copy(artist = it)) }
        MetadataTextField("Album", form.album) { onForm(form.copy(album = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetadataTextField("Genre", form.genre, Modifier.weight(1f)) { onForm(form.copy(genre = it)) }
            MetadataTextField("Year", form.year, Modifier.weight(1f)) { onForm(form.copy(year = it.filter { char -> char.isDigit() }.take(4))) }
        }
    }
}

@Composable
private fun MetadataTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = PhoebeUi.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = PhoebeUi.primaryText, fontSize = 13.sp),
            cursorBrush = SolidColor(PhoebeUi.primaryText),
            modifier = Modifier
                .fillMaxWidth()
                .trackDesktopTextInputFocus()
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.035f))
                .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TechnicalMetadata(track: Track) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Technical Metadata", color = PhoebeUi.primaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text("Read only", color = PhoebeUi.mutedText, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetadataReadOnlyCell("Codec", track.audioCodec?.uppercase() ?: "—", Modifier.weight(1f))
            MetadataReadOnlyCell("Bitrate", displayBitrateLabel(track), Modifier.weight(1f))
            MetadataReadOnlyCell("Sample Rate", displaySampleRateLabel(track), Modifier.weight(1f))
        }
        MetadataReadOnlyCell("File Path", track.filepath ?: "—")
    }
}

@Composable
private fun MetadataReadOnlyCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.028f))
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, color = PhoebeUi.mutedText, fontSize = 10.sp)
        Text(value, color = PhoebeUi.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MetadataEditorButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean = true,
) {
    val background = when {
        primary && enabled -> PhoebeUi.accent
        primary -> PhoebeUi.accent.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.045f)
    }
    val textColor = if (enabled) PhoebeUi.primaryText else PhoebeUi.secondaryText
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private data class MetadataEditorForm(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String,
) {
    fun toUpdate(trackId: String) = TrackMetadataUpdate(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        genre = genre.trim().takeIf { it.isNotBlank() },
        year = year.toIntOrNull(),
    )

    companion object {
        fun from(track: Track) = MetadataEditorForm(
            title = track.title,
            artist = track.artist,
            album = track.album,
            genre = track.genre.orEmpty(),
            year = track.year?.toString().orEmpty(),
        )
    }
}
