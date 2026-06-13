package com.phoebe.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.phoebe.app.domain.Track

object PlaybackTestTags {
    private const val PlayTrackPrefix = "play-track-"
    const val PlayAll = "play-all"

    fun playTrack(trackId: String): String = "$PlayTrackPrefix$trackId"
}

fun Modifier.playAllTarget(): Modifier =
    testTag(PlaybackTestTags.PlayAll)

fun Modifier.playTrackTarget(track: Track): Modifier =
    testTag(PlaybackTestTags.playTrack(track.id))
        .semantics {
            contentDescription = "Play ${track.title}"
            role = Role.Button
        }
