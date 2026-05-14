package com.phoebe.app

import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.Track
import com.phoebe.app.player.CastState
import com.phoebe.app.player.asPlayerState
import com.phoebe.app.player.isChromecastPlayable
import com.phoebe.app.player.isChromecastPlayableQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CastControllerTest {
    @Test
    fun plexStreamTrackIsChromecastPlayable() {
        val track = Track(
            id = "plex:track:1",
            title = "One",
            artist = "Artist",
            album = "Album",
            durationMs = 60_000,
            streamUrl = "https://plex.example/library/parts/1?X-Plex-Token=token",
            downloadUrl = "",
        )

        assertTrue(track.isChromecastPlayable())
        assertTrue(listOf(track).isChromecastPlayableQueue())
    }

    @Test
    fun localOrNonPlexTracksAreNotChromecastPlayable() {
        val plexDownload = Track(
            id = "plex:track:1",
            title = "One",
            artist = "Artist",
            album = "Album",
            durationMs = 60_000,
            streamUrl = "https://plex.example/library/parts/1?X-Plex-Token=token",
            downloadUrl = "",
            localUri = "file:///music/one.flac",
        )
        val localFolderTrack = Track(
            id = "local:track:1",
            title = "Two",
            artist = "Artist",
            album = "Album",
            durationMs = 60_000,
            streamUrl = "",
            downloadUrl = "",
        )

        assertFalse(plexDownload.isChromecastPlayable())
        assertFalse(localFolderTrack.isChromecastPlayable())
        assertFalse(listOf(plexDownload, localFolderTrack).isChromecastPlayableQueue())
    }

    @Test
    fun connectedCastStateMapsToPlayerStateForSharedTransportUi() {
        val track = Track(
            id = "plex:track:1",
            title = "One",
            artist = "Artist",
            album = "Album",
            durationMs = 60_000,
            streamUrl = "https://plex.example/library/parts/1?X-Plex-Token=token",
            downloadUrl = "",
        )
        val fallback = PlayerState(volume = 0.42f)
        val castState = CastState(
            isAvailable = true,
            isConnected = true,
            queue = listOf(track),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 12_000,
            durationMs = 60_000,
        )

        val playerState = castState.asPlayerState(fallback)

        assertEquals(track, playerState.currentTrack)
        assertTrue(playerState.isPlaying)
        assertEquals(12_000, playerState.positionMs)
        assertEquals(0.42f, playerState.volume)
    }
}
