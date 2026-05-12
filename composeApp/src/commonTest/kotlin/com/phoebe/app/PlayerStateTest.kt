package com.phoebe.app

import com.phoebe.app.domain.Track
import com.phoebe.app.player.SimpleAudioPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerStateTest {
    @Test
    fun playAndToggleUpdatesSharedState() {
        val player = TestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 1)

        assertEquals(tracks[1], player.state.value.currentTrack)
        assertTrue(player.state.value.isPlaying)
        assertEquals(tracks[1].streamUrl, player.lastUri)

        player.togglePlayPause()
        assertFalse(player.state.value.isPlaying)
    }
}

private class TestPlayer : SimpleAudioPlayer() {
    var lastUri: String? = null

    override fun playUri(uri: String) {
        lastUri = uri
    }
}
