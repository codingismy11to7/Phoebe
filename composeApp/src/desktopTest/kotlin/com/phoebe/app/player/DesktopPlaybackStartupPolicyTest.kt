package com.phoebe.app.player

import com.phoebe.app.domain.Track
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopPlaybackStartupPolicyTest {
    @Test
    fun remoteJavaFxHttpFormatsStreamDirectly() {
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
    fun remoteMp3UsesJavaFxInsteadOfSampledPlayback() {
        assertFalse(
            DesktopPlaybackStartupPolicy.shouldEagerlyBufferRemotePlayback(
                uri = "https://music.example.test/library/track.mp3?token=abc",
                preferredSampledExtension = null,
            ),
        )
        assertEquals(null, DesktopPlaybackStartupPolicy.sampledPlaybackExtensionFromSuffix("mp3"))
        assertEquals(
            null,
            DesktopPlaybackStartupPolicy.streamingSampledExtensionFromUri(
                "https://music.example.test/library/track.mp3?token=abc",
            ),
        )
    }

    @Test
    fun remoteNonJavaFxStreamsCanUseSampledStreamingWhenJavaSoundCanDecodeTheCodec() {
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

    @Test
    fun desktopPlaybackFallsBackToStreamWhenOfflineFileIsMissing() {
        val streamUrl = "https://music.example.test/library/track.mp3?token=abc"
        val missingOfflineUri = File("build/missing-offline-track.mp3").absoluteFile.toURI().toString()

        assertEquals(
            streamUrl,
            desktopPlaybackUriForTrack(
                playbackTrack(
                    streamUrl = streamUrl,
                    localUri = missingOfflineUri,
                ),
            ),
        )
    }

    @Test
    fun desktopPlaybackStillPrefersExistingOfflineFile() {
        val offline = File.createTempFile("phoebe-offline-playback", ".mp3")
        try {
            assertEquals(
                offline.toURI().toString(),
                desktopPlaybackUriForTrack(
                    playbackTrack(
                        streamUrl = "https://music.example.test/library/track.mp3?token=abc",
                        localUri = offline.toURI().toString(),
                    ),
                ),
            )
        } finally {
            offline.delete()
        }
    }

    private fun playbackTrack(
        streamUrl: String,
        localUri: String?,
    ): Track =
        Track(
            id = "track-1",
            title = "Track",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000,
            streamUrl = streamUrl,
            downloadUrl = "",
            localUri = localUri,
        )
}
