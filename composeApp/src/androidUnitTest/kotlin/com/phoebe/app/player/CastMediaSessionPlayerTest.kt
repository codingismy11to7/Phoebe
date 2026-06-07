package com.phoebe.app.player

import android.app.Application
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.phoebe.app.domain.Track
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CastMediaSessionPlayerTest {
    @Test
    fun localStateOverridesPausedDelegateForAndroidAuto() {
        val player = CastMediaSessionPlayer(FakeSessionDelegate())
        val track = testTrack("track-crossfade")

        try {
            player.updateLocalState(
                LocalMediaSessionState(
                    track = track,
                    isPlaying = true,
                    isBuffering = false,
                    positionMs = 42_000,
                    bufferedPositionMs = 60_000,
                    durationMs = 180_000,
                ),
            )

            assertEquals("track-crossfade", player.currentMediaItem?.mediaId)
            assertTrue(player.playWhenReady)
            assertTrue(player.isPlaying)
            assertTrue(player.currentPosition >= 42_000)
            assertEquals(180_000, player.duration)
        } finally {
            player.release()
        }
    }

    @Test
    fun localStateRoutesSessionControlsToLocalCallbacks() {
        val player = CastMediaSessionPlayer(FakeSessionDelegate())
        var pauseCalls = 0
        var seekPositionMs: Long? = null
        val previousPause = AndroidPlaybackBridge.onLocalMediaSessionPause
        val previousSeek = AndroidPlaybackBridge.onLocalMediaSessionSeekTo
        try {
            AndroidPlaybackBridge.onLocalMediaSessionPause = { pauseCalls++ }
            AndroidPlaybackBridge.onLocalMediaSessionSeekTo = { seekPositionMs = it }
            player.updateLocalState(
                LocalMediaSessionState(
                    track = testTrack("track-control"),
                    isPlaying = true,
                    isBuffering = false,
                    positionMs = 10_000,
                    bufferedPositionMs = 20_000,
                    durationMs = 180_000,
                ),
            )

            player.pause()
            player.seekTo(55_000)

            assertEquals(1, pauseCalls)
            assertEquals(55_000, seekPositionMs)
        } finally {
            AndroidPlaybackBridge.onLocalMediaSessionPause = previousPause
            AndroidPlaybackBridge.onLocalMediaSessionSeekTo = previousSeek
            player.release()
        }
    }

    private class FakeSessionDelegate : SimpleBasePlayer(Looper.getMainLooper()) {
        private var state = SimpleBasePlayer.State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaybackState(Player.STATE_IDLE)
            .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(0L)
            .setContentBufferedPositionMs(SimpleBasePlayer.PositionSupplier.getConstant(0L))
            .setTotalBufferedDurationMs(SimpleBasePlayer.PositionSupplier.ZERO)
            .build()

        override fun getState(): SimpleBasePlayer.State = state

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            state = state.buildUpon()
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int,
        ): ListenableFuture<*> {
            state = state.buildUpon()
                .setContentPositionMs(positionMs.coerceAtLeast(0L))
                .build()
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            state = state.buildUpon()
                .setPlaybackState(Player.STATE_IDLE)
                .setContentPositionMs(C.TIME_UNSET)
                .build()
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> =
            Futures.immediateVoidFuture()
    }

    private fun testTrack(id: String): Track =
        Track(
            id = id,
            title = "Track",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000,
            streamUrl = "https://example.test/$id.mp3",
            downloadUrl = "",
        )
}
