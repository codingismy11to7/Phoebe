package com.phoebe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertTrue

class MetadataEditorOverlayDesktopTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun compactEditorHandleSwipeDownInvokesDismiss() = runDesktopComposeUiTest(width = 430, height = 760) {
        var dismissed = false

        setContent {
            PhoebeTheme {
                Box(Modifier.size(430.dp, 760.dp)) {
                    MetadataEditorOverlay(
                        track = metadataEditorTrack(),
                        compact = true,
                        onDismiss = { dismissed = true },
                        onSave = {},
                    )
                }
            }
        }

        onNodeWithContentDescription("Dismiss metadata editor").performTouchInput {
            swipe(
                start = center,
                end = center.copy(y = center.y + 180f),
                durationMillis = 200,
            )
        }

        assertTrue(dismissed)
    }
}

private fun metadataEditorTrack(): Track =
    Track(
        id = "metadata-track",
        title = "Editable Song",
        artist = "Fixture Artist",
        album = "Fixture Album",
        durationMs = 60_000L,
        streamUrl = "https://stream.example/metadata-track.mp3",
        downloadUrl = "",
    )
