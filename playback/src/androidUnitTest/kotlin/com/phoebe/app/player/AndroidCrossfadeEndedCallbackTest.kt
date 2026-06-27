package com.phoebe.app.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidCrossfadeEndedCallbackTest {
    @Test
    fun staleServiceEndedCallbackIsIgnoredAfterCrossfadeCommit() {
        assertTrue(
            shouldIgnoreAndroidServiceEndedCallback(
                crossfadeOwnedTrackId = "incoming-track",
                hasCrossfadePlayer = true,
            ),
        )
    }

    @Test
    fun normalServiceEndedCallbackIsHandledWithoutOwnedCrossfade() {
        assertFalse(
            shouldIgnoreAndroidServiceEndedCallback(
                crossfadeOwnedTrackId = null,
                hasCrossfadePlayer = false,
            ),
        )
        assertFalse(
            shouldIgnoreAndroidServiceEndedCallback(
                crossfadeOwnedTrackId = null,
                hasCrossfadePlayer = true,
            ),
        )
    }
}
