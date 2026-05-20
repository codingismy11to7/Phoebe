package com.phoebe.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.phoebe.app.domain.Track
import com.phoebe.app.player.SimpleAudioPlayer
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class PlaybackComposeInstrumentedTest {
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

        player.play(listOf(track), 0)

        assertEquals("file:///tmp/local.mp3", player.lastUri)
    }
}
