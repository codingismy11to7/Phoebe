package com.phoebe.app.player

import kotlin.test.Test
import kotlin.test.assertEquals

class WebAudioBufferedPositionTest {

    @Test
    fun seekableRangeDoesNotPretendTrackIsBuffered() {
        val buffered = listOf(WebAudioTimeRange(startMs = 0L, endMs = 65_000L))

        assertEquals(
            65_000L,
            webAudioBufferedPositionMs(
                positionMs = 5_000L,
                durationMs = 287_000L,
                bufferedRanges = buffered,
            ),
        )
    }

    @Test
    fun prefetchProgressCanAdvanceBeyondBrowserBufferWindow() {
        assertEquals(
            180_000L,
            webAudioBufferedPositionMs(
                positionMs = 5_000L,
                durationMs = 287_000L,
                bufferedRanges = listOf(WebAudioTimeRange(startMs = 0L, endMs = 65_000L)),
                prefetchedPositionMs = 180_000L,
            ),
        )
    }

    @Test
    fun disconnectedFutureRangesDoNotAdvanceFromCurrentPosition() {
        assertEquals(
            5_000L,
            webAudioBufferedPositionMs(
                positionMs = 5_000L,
                durationMs = 287_000L,
                bufferedRanges = listOf(WebAudioTimeRange(startMs = 90_000L, endMs = 120_000L)),
            ),
        )
    }

    @Test
    fun nearEndRangeSnapsToDuration() {
        assertEquals(
            287_000L,
            webAudioBufferedPositionMs(
                positionMs = 250_000L,
                durationMs = 287_000L,
                bufferedRanges = listOf(WebAudioTimeRange(startMs = 0L, endMs = 286_500L)),
            ),
        )
    }
}
