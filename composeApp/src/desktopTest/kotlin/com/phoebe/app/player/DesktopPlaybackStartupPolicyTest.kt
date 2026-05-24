package com.phoebe.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPlaybackStartupPolicyTest {
    @Test
    fun javaFxFriendlyRemoteStreamsDoNotEagerlyDownloadWholeTrack() {
        assertFalse(
            DesktopPlaybackStartupPolicy.shouldEagerlyBufferRemotePlayback(
                uri = "https://music.example.test/library/track.mp3?token=abc",
                preferredSampledExtension = null,
            ),
        )
        assertFalse(
            DesktopPlaybackStartupPolicy.shouldEagerlyBufferRemotePlayback(
                uri = "https://music.example.test/library/track.m4a",
                preferredSampledExtension = null,
            ),
        )
    }

    @Test
    fun sampledOnlyRemoteStreamsStillBufferBeforePlayback() {
        assertTrue(
            DesktopPlaybackStartupPolicy.shouldEagerlyBufferRemotePlayback(
                uri = "https://music.example.test/library/track.flac?token=abc",
                preferredSampledExtension = null,
            ),
        )
        assertTrue(
            DesktopPlaybackStartupPolicy.shouldEagerlyBufferRemotePlayback(
                uri = "https://music.example.test/stream",
                preferredSampledExtension = "ogg",
            ),
        )
    }

    @Test
    fun remoteStreamsUseSampledStreamingWhenJavaSoundCanDecodeTheCodec() {
        assertEquals(
            "mp3",
            DesktopPlaybackStartupPolicy.streamingSampledExtensionFromUri(
                "https://music.example.test/library/track.mp3?token=abc",
            ),
        )
        assertEquals("flac", DesktopPlaybackStartupPolicy.streamingSampledExtensionFromSuffix("flac"))
    }

    @Test
    fun remoteJavaFxFormatsCanStillPrefetchForCrossfade() {
        assertTrue(
            DesktopPlaybackStartupPolicy.shouldPrefetchRemoteForCrossfade(
                "https://music.example.test/library/next-track.mp3",
            ),
        )
    }
}
