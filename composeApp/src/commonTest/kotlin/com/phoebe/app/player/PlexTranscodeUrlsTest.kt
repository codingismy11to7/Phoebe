package com.phoebe.app.player

import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlexTranscodeUrlsTest {
    @Test
    fun jellyfinFamilyMp3TranscodeUrlRewritesAudioStreamEndpoint() {
        val track = Track(
            id = "550e8400-e29b-41d4-a716-446655440000",
            title = "Track",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000,
            streamUrl = "https://jellyfin.example/Audio/550e8400-e29b-41d4-a716-446655440000/stream?static=true&api_key=token",
            downloadUrl = "",
            audioCodec = "AAC",
        )
        assertEquals(
            "https://jellyfin.example/Audio/550e8400-e29b-41d4-a716-446655440000/stream.mp3?static=true&audioCodec=mp3&api_key=token",
            track.jellyfinFamilyMp3TranscodeUrl(),
        )
    }

    @Test
    fun jellyfinFamilyMp3TranscodeUrlRequiresApiKey() {
        val track = Track(
            id = "1",
            title = "Track",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000,
            streamUrl = "https://jellyfin.example/Audio/1/stream?static=true",
            downloadUrl = "",
        )
        assertNull(track.jellyfinFamilyMp3TranscodeUrl())
    }

    @Test
    fun flatpakSandboxTranscodeUrlPrefersPlexBeforeJellyfin() {
        val plexTrack = Track(
            id = "plex:124",
            title = "Track",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000,
            streamUrl = "https://plex.example:32400/library/parts/2.m4a?X-Plex-Token=token",
            downloadUrl = "",
            audioCodec = "aac",
        )
        assertEquals(
            "https://plex.example:32400/music/:/transcode/universal/start.mp3?path=%2Flibrary%2Fmetadata%2F124&mediaIndex=0&partIndex=0&protocol=http&format=mp3&audioCodec=mp3&directPlay=0&directStream=0&X-Plex-Token=token",
            plexTrack.flatpakSandboxTranscodeUrl(),
        )
    }
}
