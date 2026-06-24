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
    fun flatpakSandboxUsesPlexMp3TranscodeForAacStreams() {
        DesktopSandboxPlayback.flatpakSandboxOverride = { true }
        try {
            val track = playbackTrack(
                streamUrl = "https://plex.example:32400/library/parts/2.m4a?X-Plex-Token=token",
                localUri = null,
            ).copy(
                id = "plex:124",
                audioCodec = "aac",
                filepath = "/music/Artist/Album/02 Track.m4a",
            )
            assertEquals(
                "https://plex.example:32400/music/:/transcode/universal/start.mp3?path=%2Flibrary%2Fmetadata%2F124&mediaIndex=0&partIndex=0&protocol=http&format=mp3&audioCodec=mp3&directPlay=0&directStream=0&X-Plex-Token=token",
                DesktopSandboxPlayback.playbackStreamUrlForTrack(track),
            )
        } finally {
            DesktopSandboxPlayback.flatpakSandboxOverride = null
        }
    }

    @Test
    fun flatpakSandboxUsesJellyfinMp3TranscodeForAacStreams() {
        DesktopSandboxPlayback.flatpakSandboxOverride = { true }
        try {
            val track = playbackTrack(
                streamUrl = "https://jellyfin.example/Audio/550e8400-e29b-41d4-a716-446655440000/stream?static=true&api_key=token",
                localUri = null,
            ).copy(
                audioCodec = "M4A",
                filepath = "/music/Artist/Album/02 Track.m4a",
            )
            assertEquals(
                "https://jellyfin.example/Audio/550e8400-e29b-41d4-a716-446655440000/stream.mp3?static=true&audioCodec=mp3&api_key=token",
                DesktopSandboxPlayback.playbackStreamUrlForTrack(track),
            )
        } finally {
            DesktopSandboxPlayback.flatpakSandboxOverride = null
        }
    }

    @Test
    fun flatpakSandboxStreamsRemoteHttpBeforeBufferedFallback() {
        DesktopSandboxPlayback.flatpakSandboxOverride = { true }
        try {
            assertTrue(
                DesktopSandboxPlayback.shouldStreamRemoteSampledPlayback(
                    "https://plex.example:32400/library/parts/2.mp3?X-Plex-Token=token",
                ),
            )
            assertTrue(
                DesktopSandboxPlayback.shouldEagerlyBufferRemotePlayback(
                    "https://plex.example:32400/library/parts/2.mp3?X-Plex-Token=token",
                    preferredSampledExtension = null,
                ),
            )
        } finally {
            DesktopSandboxPlayback.flatpakSandboxOverride = null
        }
    }

    @Test
    fun flatpakSandboxBuffersTranscodeUrlInsteadOfDirectDownload() {
        DesktopSandboxPlayback.flatpakSandboxOverride = { true }
        try {
            val transcodeUrl =
                "https://plex.example:32400/music/:/transcode/universal/start.mp3?path=%2Flibrary%2Fmetadata%2F124&X-Plex-Token=token"
            val directDownload =
                "https://plex.example:32400/library/parts/2.m4a?X-Plex-Token=token&download=1"
            assertEquals(
                transcodeUrl,
                DesktopSandboxPlayback.bufferedRemotePlaybackUri(transcodeUrl, directDownload),
            )
        } finally {
            DesktopSandboxPlayback.flatpakSandboxOverride = null
        }
    }

    @Test
    fun flatpakSandboxBuffersRemoteMp3WithSampledPlayback() {
        DesktopSandboxPlayback.flatpakSandboxOverride = { true }
        try {
            assertTrue(
                DesktopSandboxPlayback.shouldEagerlyBufferRemotePlayback(
                    uri = "https://music.example.test/library/track.mp3?token=abc",
                    preferredSampledExtension = null,
                ),
            )
            assertEquals("mp3", DesktopSandboxPlayback.sampledPlaybackExtensionFromSuffix("mp3"))
            assertEquals("mp3", DesktopSandboxPlayback.streamingSampledExtensionFromSuffix("mp3"))
        } finally {
            DesktopSandboxPlayback.flatpakSandboxOverride = null
        }
    }

    @Test
    fun nonSandboxMp3StillAvoidsJavaSoundStartupPaths() {
        DesktopSandboxPlayback.flatpakSandboxOverride = { false }
        try {
            assertEquals(null, DesktopSandboxPlayback.sampledPlaybackExtensionFromSuffix("mp3"))
            assertEquals(null, DesktopSandboxPlayback.streamingSampledExtensionFromSuffix("mp3"))
        } finally {
            DesktopSandboxPlayback.flatpakSandboxOverride = null
        }
    }

    @Test
    fun remoteNonJavaFxFormatsStreamBeforeBufferedFallback() {
        assertEquals("flac", DesktopPlaybackStartupPolicy.streamingSampledExtensionFromSuffix("flac"))
        assertTrue(
            DesktopSandboxPlayback.shouldStreamRemoteSampledPlayback(
                "https://music.example.test/library/track.flac?token=abc",
            ),
        )
        assertTrue(
            DesktopSandboxPlayback.shouldEagerlyBufferRemotePlayback(
                uri = "https://music.example.test/library/track.flac?token=abc",
                preferredSampledExtension = null,
            ),
        )
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
