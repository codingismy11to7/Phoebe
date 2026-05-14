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

    @Test
    fun supersededPlayRequestDoesNotStartStaleTrack() {
        val player = SlowTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
            Track("t3", "Three", "Artist", "Album", 120_000, "http://c", ""),
        )

        player.play(tracks, 0)
        player.play(tracks, 2)

        assertEquals(tracks[2], player.state.value.currentTrack)
        assertFalse(player.state.value.isPlaying)

        player.finishLoad(1)
        assertFalse(player.state.value.isPlaying)

        player.finishLoad(2)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun pauseDuringBufferingCancelsAutoplay() {
        val player = SlowTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
        )

        player.play(tracks, 0)
        assertTrue(player.state.value.isBuffering)
        assertFalse(player.state.value.isPlaying)

        player.togglePlayPause()
        assertFalse(player.state.value.isBuffering)
        assertFalse(player.state.value.isPlaying)

        player.finishPendingLoad()

        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun rapidSameQueueSkipsOnlyPlayFinalTrack() {
        val player = SlowTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
            Track("t3", "Three", "Artist", "Album", 120_000, "http://c", ""),
            Track("t4", "Four", "Artist", "Album", 150_000, "http://d", ""),
        )

        player.play(tracks, 0)
        player.play(tracks, 1)
        player.play(tracks, 2)
        player.play(tracks, 3)

        assertEquals(tracks[3], player.state.value.currentTrack)
        assertTrue(player.state.value.isBuffering)
        assertFalse(player.state.value.isPlaying)

        player.finishLoad(1)
        player.finishLoad(2)
        player.finishLoad(3)
        assertEquals(tracks[3], player.state.value.currentTrack)
        assertFalse(player.state.value.isPlaying)

        player.finishLoad(4)
        assertEquals(tracks[3], player.state.value.currentTrack)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun playResetsPositionWhenSkippingTracks() {
        val player = PositionTrackingTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 180_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 180_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.finishPendingLoad()
        player.seekTo(120_000)
        assertEquals(120_000, player.state.value.positionMs)

        player.play(tracks, 1)
        player.finishPendingLoad()

        assertEquals(0L, player.state.value.positionMs)
        assertEquals(0L, player.lastSeekPositionMs)
    }

    @Test
    fun sameQueueSkipDoesNotReloadFromScratch() {
        val player = QueueAwareTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.finishPendingLoad()
        assertEquals(1, player.fullLoads)
        assertEquals(0, player.queueSkips)

        player.play(tracks, 1)
        player.finishPendingLoad()

        assertEquals(tracks[1], player.state.value.currentTrack)
        assertEquals(1, player.fullLoads)
        assertEquals(1, player.queueSkips)
    }
}

private class TestPlayer : SimpleAudioPlayer() {
    var lastUri: String? = null

    override fun playUri(uri: String) {
        lastUri = uri
        markPlaybackReady()
    }
}

private class SlowTestPlayer : SimpleAudioPlayer() {
    private val pendingLoads = mutableSetOf<Int>()

    override fun playUri(uri: String) = Unit

    override fun playQueueOnPlatform(queue: List<Track>, startIndex: Int, track: Track, generation: Int) {
        pendingLoads += generation
    }

    override fun skipToInQueueOnPlatform(queue: List<Track>, startIndex: Int, track: Track, generation: Int) {
        pendingLoads += generation
    }

    fun finishLoad(generation: Int) {
        if (generation in pendingLoads) {
            markPlaybackReady(generation = generation)
        }
    }

    fun finishPendingLoad() {
        finishLoad(activePlayGeneration)
    }
}

private class QueueAwareTestPlayer : SimpleAudioPlayer() {
    var fullLoads = 0
    var queueSkips = 0

    override fun playUri(uri: String) = Unit

    override fun playQueueOnPlatform(queue: List<Track>, startIndex: Int, track: Track, generation: Int) {
        fullLoads++
    }

    override fun skipToInQueueOnPlatform(queue: List<Track>, startIndex: Int, track: Track, generation: Int) {
        queueSkips++
    }

    fun finishPendingLoad() {
        markPlaybackReady(generation = activePlayGeneration)
    }
}

private class PositionTrackingTestPlayer : SimpleAudioPlayer() {
    var lastSeekPositionMs: Long = -1

    override fun playUri(uri: String) = Unit

    override fun skipToInQueueOnPlatform(queue: List<Track>, startIndex: Int, track: Track, generation: Int) {
        lastSeekPositionMs = 0L
        markPlaybackReady(generation = generation)
    }

    override fun playQueueOnPlatform(queue: List<Track>, startIndex: Int, track: Track, generation: Int) {
        lastSeekPositionMs = 0L
        markPlaybackReady(generation = generation)
    }

    override fun seek(positionMs: Long) {
        lastSeekPositionMs = positionMs
    }

    fun finishPendingLoad() {
        markPlaybackReady(generation = activePlayGeneration)
    }
}
