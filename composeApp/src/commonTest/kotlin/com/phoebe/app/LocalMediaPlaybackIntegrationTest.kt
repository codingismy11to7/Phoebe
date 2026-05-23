package com.phoebe.app

import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMediaPlaybackIntegrationTest {
    @Test
    fun playUsesStreamUriWhenTrackIsNotDownloaded() {
        val player = RecordingAudioPlayer()
        val track = Track(
            id = "remote:alpha",
            title = "alpha",
            artist = "Test",
            album = "Folder",
            durationMs = 1_000,
            streamUrl = "https://stream.example/alpha",
            downloadUrl = "",
        )

        player.play(listOf(track), 0)

        assertEquals("https://stream.example/alpha", player.lastUri)
        assertTrue(player.state.value.isPlaying)
        assertEquals(track, player.state.value.currentTrack)
    }

    @Test
    fun playPrefersLocalUriOverStreamUrl() {
        val player = RecordingAudioPlayer()
        val track = Track(
            id = "local:alpha",
            title = "alpha",
            artist = "Test",
            album = "Folder",
            durationMs = 1_000,
            streamUrl = "https://stream.example/alpha",
            downloadUrl = "",
            localUri = "file:///music/alpha.mp3",
        )

        player.play(listOf(track), 0)

        assertEquals("file:///music/alpha.mp3", player.lastUri)
        assertTrue(player.state.value.isPlaying)
        assertEquals(track, player.state.value.currentTrack)
    }
}
