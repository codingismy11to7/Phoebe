package com.phoebe.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.phoebe.app.domain.Track
import com.phoebe.app.ui.*

@Composable
fun MainFeature(track: Track?, modifier: Modifier) {
    Column(modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassIcon(PhoebeIcon.Back, "Back")
                GlassIcon(PhoebeIcon.Forward, "Forward")
            }
            Spacer(Modifier.weight(1f))
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
                FlippableSongArtwork(track = track, modifier = Modifier.size(292.dp))
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
                            bufferedProgress = null,
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
fun HomeNothingPlayingHero() {
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
