package com.phoebe.app

import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phoebe.app.domain.Track
import com.phoebe.app.player.SimpleAudioPlayer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PlaybackComposeInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun playButtonUsesLocalUriWhenPresent() {
        val player = object : SimpleAudioPlayer() {
            var lastUri: String? = null
            override fun playUri(uri: String) {
                lastUri = uri
            }
        }
        val track = Track(
            id = "1",
            title = "Song",
            artist = "A",
            album = "B",
            durationMs = 1000L,
            streamUrl = "https://stream.example/track",
            downloadUrl = "",
            localUri = "file:///tmp/local.mp3",
        )
        composeRule.setContent {
            MaterialTheme {
                Button(
                    onClick = { player.play(listOf(track), 0) },
                    modifier = Modifier.testTag("play"),
                ) {
                    Text("Play")
                }
            }
        }
        composeRule.onNodeWithTag("play").performClick()
        assertEquals("file:///tmp/local.mp3", player.lastUri)
    }
}
