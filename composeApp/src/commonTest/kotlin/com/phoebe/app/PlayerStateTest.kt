package com.phoebe.app

import com.phoebe.app.domain.Track
import com.phoebe.app.domain.RepeatMode
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
    fun clickingCurrentBufferingStreamTrackReassertsPlaybackIntent() {
        val player = SlowTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
        )

        player.play(tracks, 0)
        player.platformPlayWhenReady(false)
        player.play(tracks, 0)
        player.finishPendingLoad()

        assertTrue(player.state.value.isPlaying)
        assertEquals(1, player.resumeCalls)
    }

    @Test
    fun clickingCurrentBufferingDownloadedTrackReassertsPlaybackIntent() {
        val player = SlowTestPlayer()
        val tracks = listOf(
            Track(
                id = "t1",
                title = "One",
                artist = "Artist",
                album = "Album",
                durationMs = 60_000,
                streamUrl = "http://a",
                downloadUrl = "",
                localUri = "file:///downloads/one.mp3",
            ),
        )

        player.play(tracks, 0)
        player.platformPlayWhenReady(false)
        player.play(tracks, 0)
        player.finishPendingLoad()

        assertTrue(player.state.value.isPlaying)
        assertEquals(1, player.resumeCalls)
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

    @Test
    fun endOfQueueStopsPlayback() {
        val player = TestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
        )

        player.play(tracks, 0)
        assertTrue(player.state.value.isPlaying)

        player.next()

        assertFalse(player.state.value.isPlaying)
        assertFalse(player.state.value.isBuffering)
        assertEquals(60_000, player.state.value.positionMs)
    }

    @Test
    fun bufferedPositionIsClampedToPositionAndDuration() {
        val player = PlatformStateTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
        )

        player.play(tracks, 0)
        player.platformPlayback(positionMs = 20_000, durationMs = 60_000, bufferedPositionMs = 10_000)

        assertEquals(20_000, player.state.value.bufferedPositionMs)

        player.platformPlayback(positionMs = 25_000, durationMs = 60_000, bufferedPositionMs = 90_000)

        assertEquals(60_000, player.state.value.bufferedPositionMs)
    }

    @Test
    fun platformPlayIntentCanResumeAfterAppPause() {
        val player = PlatformStateTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
        )

        player.play(tracks, 0)
        player.platformPlayback(positionMs = 10_000, durationMs = 60_000, bufferedPositionMs = 20_000)
        assertTrue(player.state.value.isPlaying)

        player.togglePlayPause()
        assertFalse(player.state.value.isPlaying)

        player.platformPlayback(positionMs = 11_000, durationMs = 60_000, bufferedPositionMs = 20_000)
        assertFalse(player.state.value.isPlaying)

        player.platformPlayWhenReady(true)
        player.platformPlayback(positionMs = 12_000, durationMs = 60_000, bufferedPositionMs = 21_000)

        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun newTrackResetsBufferedPosition() {
        val player = PlatformStateTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.platformPlayback(positionMs = 10_000, durationMs = 60_000, bufferedPositionMs = 50_000)
        assertEquals(50_000, player.state.value.bufferedPositionMs)

        player.play(tracks, 1)

        assertEquals(0L, player.state.value.bufferedPositionMs)
    }

    @Test
    fun bufferedPositionDoesNotMoveBackwardForCurrentTrack() {
        val player = PlatformStateTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
        )

        player.play(tracks, 0)
        player.platformPlayback(positionMs = 10_000, durationMs = 60_000, bufferedPositionMs = 50_000)
        player.platformPlayback(positionMs = 20_000, durationMs = 60_000, bufferedPositionMs = 30_000)

        assertEquals(50_000, player.state.value.bufferedPositionMs)
    }

    @Test
    fun playbackFailurePublishesOneShotErrorSignal() {
        val player = PlatformStateTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
        )

        player.play(tracks, 0)
        player.failPlayback("Nope")

        assertFalse(player.state.value.isPlaying)
        assertFalse(player.state.value.isBuffering)
        assertEquals(1, player.state.value.playbackErrorSerial)
        assertEquals("Nope", player.state.value.playbackErrorMessage)

        player.play(tracks, 0)

        assertEquals(1, player.state.value.playbackErrorSerial)
        assertEquals(null, player.state.value.playbackErrorMessage)
    }

    @Test
    fun platformCrossfadeDoesNotChangeTimelineUntilCommit() {
        val player = CrossfadeTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.setCrossfadeDurationMs(6_000)
        player.platformPlayback(positionMs = 55_000, durationMs = 60_000, bufferedPositionMs = 60_000)

        assertEquals(1, player.crossfadeStarts)
        assertEquals(tracks[0], player.state.value.currentTrack)
        assertEquals(55_000, player.state.value.positionMs)

        player.commitCrossfade(positionMs = 6_000)

        assertEquals(tracks[1], player.state.value.currentTrack)
        assertEquals(6_000, player.state.value.positionMs)
        assertEquals(90_000, player.state.value.durationMs)
    }

    @Test
    fun repeatedCrossfadeRequestsForSameTargetAreIgnoredUntilCommit() {
        val player = CrossfadeTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.setCrossfadeDurationMs(6_000)
        player.platformPlayback(positionMs = 55_000, durationMs = 60_000, bufferedPositionMs = 60_000)
        player.platformPlayback(positionMs = 56_000, durationMs = 60_000, bufferedPositionMs = 60_000)

        assertEquals(1, player.crossfadeStarts)
        assertEquals(tracks[0], player.state.value.currentTrack)
    }

    @Test
    fun automaticCrossfadeOnlyStartsInsideRemainingWindow() {
        val player = CrossfadeTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.setCrossfadeDurationMs(6_000)
        player.platformPlayback(positionMs = 53_000, durationMs = 60_000, bufferedPositionMs = 60_000)

        assertEquals(0, player.crossfadeStarts)

        player.platformPlayback(positionMs = 54_000, durationMs = 60_000, bufferedPositionMs = 60_000)

        assertEquals(1, player.crossfadeStarts)
        assertEquals(1, player.lastTargetIndex)
    }

    @Test
    fun pausedPlaybackDoesNotStartAutomaticCrossfade() {
        val player = CrossfadeTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.setCrossfadeDurationMs(6_000)
        player.platformPlayback(
            positionMs = 55_000,
            durationMs = 60_000,
            bufferedPositionMs = 60_000,
            isPlaying = false,
        )

        assertEquals(0, player.crossfadeStarts)
        assertEquals(tracks[0], player.state.value.currentTrack)
    }

    @Test
    fun repeatAllCrossfadeTargetsFirstTrackFromQueueEnd() {
        val player = CrossfadeTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 1)
        player.setRepeat(RepeatMode.All)
        player.setCrossfadeDurationMs(6_000)
        player.platformPlayback(positionMs = 85_000, durationMs = 90_000, bufferedPositionMs = 90_000)

        assertEquals(1, player.crossfadeStarts)
        assertEquals(0, player.lastTargetIndex)

        player.commitCrossfade(positionMs = 4_000)

        assertEquals(tracks[0], player.state.value.currentTrack)
        assertEquals(4_000, player.state.value.positionMs)
    }

    @Test
    fun manualNextSkipsImmediatelyWhenCrossfadeIsEnabled() {
        val player = CrossfadeTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.setCrossfadeDurationMs(6_000)
        player.next()

        assertEquals(0, player.crossfadeStarts)
        assertEquals(tracks[1], player.state.value.currentTrack)
    }

    @Test
    fun unsupportedAutomaticCrossfadeDoesNotSkipEarly() {
        val player = PlatformStateTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.setCrossfadeDurationMs(6_000)
        player.platformPlayback(positionMs = 55_000, durationMs = 60_000, bufferedPositionMs = 60_000)

        assertEquals(tracks[0], player.state.value.currentTrack)
        assertEquals(55_000, player.state.value.positionMs)
    }

    @Test
    fun crossfadeCanRunAgainAfterCommitInSameQueue() {
        val player = CrossfadeTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
            Track("t3", "Three", "Artist", "Album", 120_000, "http://c", ""),
        )

        player.play(tracks, 0)
        player.setCrossfadeDurationMs(6_000)
        player.platformPlayback(positionMs = 55_000, durationMs = 60_000, bufferedPositionMs = 60_000)
        player.commitCrossfade(positionMs = 6_000)
        player.platformPlayback(positionMs = 85_000, durationMs = 90_000, bufferedPositionMs = 90_000)

        assertEquals(2, player.crossfadeStarts)
        assertEquals(tracks[1], player.state.value.currentTrack)

        player.commitCrossfade(positionMs = 6_000)

        assertEquals(tracks[2], player.state.value.currentTrack)
        assertEquals(6_000, player.state.value.positionMs)
    }

    @Test
    fun zeroSecondCrossfadeKeepsNormalNextBehavior() {
        val player = CrossfadeTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.setCrossfadeDurationMs(0)
        player.next()

        assertEquals(0, player.crossfadeStarts)
        assertEquals(tracks[1], player.state.value.currentTrack)
    }

    @Test
    fun clearQueueKeepsCurrentTrackButRemovesUpNext() {
        val player = TestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
            Track("t3", "Three", "Artist", "Album", 120_000, "http://c", ""),
        )

        player.play(tracks, 0)
        player.clearQueue()

        assertEquals(tracks[0], player.state.value.currentTrack)
        assertTrue(player.state.value.upNext.isEmpty())
    }

    @Test
    fun stopPlaybackClearsCurrentTrackAndUpNext() {
        val player = TestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        player.setVolume(0.5f)
        player.stopPlayback()

        assertEquals(null, player.state.value.currentTrack)
        assertTrue(player.state.value.queue.isEmpty())
        assertFalse(player.state.value.isPlaying)
        assertEquals(0.5f, player.state.value.volume)
    }

    @Test
    fun suspendPlaybackKeepsQueueWithoutReloadingPlatformOutput() {
        val player = SuspendTrackingTestPlayer()
        val tracks = listOf(
            Track("t1", "One", "Artist", "Album", 60_000, "http://a", ""),
            Track("t2", "Two", "Artist", "Album", 90_000, "http://b", ""),
        )

        player.play(tracks, 0)
        val loadsAfterPlay = player.playUriCalls
        val stopsAfterPlay = player.stopCalls

        player.suspendPlayback(tracks, 1, positionMs = 12_000)

        assertEquals(tracks[1], player.state.value.currentTrack)
        assertEquals(12_000, player.state.value.positionMs)
        assertFalse(player.state.value.isPlaying)
        assertFalse(player.state.value.isBuffering)
        assertEquals(loadsAfterPlay, player.playUriCalls)
        assertEquals(stopsAfterPlay + 1, player.stopCalls)
    }
}

private class TestPlayer : SimpleAudioPlayer() {
    var lastUri: String? = null

    override fun playUri(uri: String) {
        lastUri = uri
        markPlaybackReady()
    }
}

private class SuspendTrackingTestPlayer : SimpleAudioPlayer() {
    var playUriCalls = 0
    var stopCalls = 0

    override fun playUri(uri: String) {
        playUriCalls++
        markPlaybackReady()
    }

    override fun stopCurrentPlaybackImmediately() {
        stopCalls++
    }
}

private class SlowTestPlayer : SimpleAudioPlayer() {
    private val pendingLoads = mutableSetOf<Int>()
    var resumeCalls = 0

    override fun playUri(uri: String) = Unit

    override fun resume() {
        resumeCalls++
    }

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

    fun platformPlayWhenReady(playWhenReady: Boolean) {
        adoptPlatformPlayIntent(playWhenReady)
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

private class PlatformStateTestPlayer : SimpleAudioPlayer() {
    override fun playUri(uri: String) = Unit

    fun platformPlayback(
        positionMs: Long,
        durationMs: Long,
        bufferedPositionMs: Long,
        isPlaying: Boolean = true,
    ) {
        applyPlatformPlayback(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            isBuffering = false,
            bufferedPositionMs = bufferedPositionMs,
        )
    }

    fun platformPlayWhenReady(playWhenReady: Boolean) {
        adoptPlatformPlayIntent(playWhenReady)
    }

    fun failPlayback(message: String? = null) {
        markPlaybackFailed(message = message)
    }
}

private class CrossfadeTestPlayer : SimpleAudioPlayer() {
    var crossfadeStarts = 0
    var lastTargetIndex = -1
    private var pendingQueue: List<Track> = emptyList()
    private var pendingTargetIndex = -1
    private var pendingGeneration = -1

    override fun playUri(uri: String) {
        markPlaybackReady()
    }

    override fun startCrossfadeOnPlatform(
        queue: List<Track>,
        targetIndex: Int,
        track: Track,
        durationMs: Long,
        baseVolume: Float,
        generation: Int,
    ): Boolean {
        crossfadeStarts++
        lastTargetIndex = targetIndex
        pendingQueue = queue
        pendingTargetIndex = targetIndex
        pendingGeneration = generation
        return true
    }

    fun platformPlayback(
        positionMs: Long,
        durationMs: Long,
        bufferedPositionMs: Long,
        isPlaying: Boolean = true,
    ) {
        applyPlatformPlayback(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            isBuffering = false,
            bufferedPositionMs = bufferedPositionMs,
        )
    }

    fun commitCrossfade(positionMs: Long) {
        adoptCrossfadeTarget(pendingQueue, pendingTargetIndex, positionMs, pendingGeneration)
    }
}
