package com.phoebe.app.player

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidAutoplayPolicyTest {
    @Test
    fun pendingAutoplaySurvivesInitialPlayingAtZeroPosition() {
        assertTrue(
            shouldRetainPendingAutoplay(
                pendingGeneration = 4,
                generation = 4,
                playWhenReady = true,
                hasCurrentTrack = true,
                playerIsPlaying = true,
                playbackState = Player.STATE_READY,
                positionMs = 0L,
            ),
        )
    }

    @Test
    fun pendingAutoplaySurvivesStartupStopBeforePlaybackAdvances() {
        assertTrue(
            shouldRetainPendingAutoplay(
                pendingGeneration = 4,
                generation = 4,
                playWhenReady = true,
                hasCurrentTrack = true,
                playerIsPlaying = false,
                playbackState = Player.STATE_READY,
                positionMs = 0L,
            ),
        )
    }

    @Test
    fun pendingAutoplayClearsAfterPlaybackAdvances() {
        assertFalse(
            shouldRetainPendingAutoplay(
                pendingGeneration = 4,
                generation = 4,
                playWhenReady = true,
                hasCurrentTrack = true,
                playerIsPlaying = true,
                playbackState = Player.STATE_READY,
                positionMs = 250L,
            ),
        )
    }

    @Test
    fun pendingAutoplayDoesNotOverrideExplicitPauseIntent() {
        assertFalse(
            shouldRetainPendingAutoplay(
                pendingGeneration = 4,
                generation = 4,
                playWhenReady = false,
                hasCurrentTrack = true,
                playerIsPlaying = false,
                playbackState = Player.STATE_READY,
                positionMs = 0L,
            ),
        )
    }

    @Test
    fun adoptPlatformPlayIntentOnlyWhenTracksMatchAndNoMutationInProgress() {
        assertTrue(
            shouldAdoptPlatformPlayIntent(
                appControllerMutationInProgress = false,
                platformTrackId = "track-123",
                appTrackId = "track-123",
            ),
        )

        assertFalse(
            shouldAdoptPlatformPlayIntent(
                appControllerMutationInProgress = true,
                platformTrackId = "track-123",
                appTrackId = "track-123",
            ),
        )

        assertFalse(
            shouldAdoptPlatformPlayIntent(
                appControllerMutationInProgress = false,
                platformTrackId = "track-old",
                appTrackId = "track-new",
            ),
        )

        assertFalse(
            shouldAdoptPlatformPlayIntent(
                appControllerMutationInProgress = false,
                platformTrackId = null,
                appTrackId = "track-123",
            ),
        )

        assertTrue(
            shouldAdoptPlatformPlayIntent(
                appControllerMutationInProgress = false,
                platformTrackId = null,
                appTrackId = null,
            ),
        )
    }

    @Test
    fun activeCrossfadeCancelsOnlyForExternalControllerPause() {
        assertTrue(
            shouldCancelAndroidCrossfadeForControllerPause(
                crossfadeTransitionActive = true,
                playWhenReady = true,
                controllerPlayWhenReady = false,
                appControllerMutationInProgress = false,
            ),
        )

        assertFalse(
            shouldCancelAndroidCrossfadeForControllerPause(
                crossfadeTransitionActive = true,
                playWhenReady = true,
                controllerPlayWhenReady = false,
                appControllerMutationInProgress = true,
            ),
        )

        assertFalse(
            shouldCancelAndroidCrossfadeForControllerPause(
                crossfadeTransitionActive = false,
                playWhenReady = true,
                controllerPlayWhenReady = false,
                appControllerMutationInProgress = false,
            ),
        )

        assertFalse(
            shouldCancelAndroidCrossfadeForControllerPause(
                crossfadeTransitionActive = true,
                playWhenReady = false,
                controllerPlayWhenReady = false,
                appControllerMutationInProgress = false,
            ),
        )
    }
}
