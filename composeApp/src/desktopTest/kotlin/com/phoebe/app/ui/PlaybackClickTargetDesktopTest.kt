package com.phoebe.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LibraryUiPreferences
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaybackClickTargetDesktopTest {
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun trackListSongRowInvokesPlaybackRequestForTappedTrack() = runDesktopComposeUiTest(width = 800, height = 520) {
        val tracks = playbackTracks()
        var request: PlaybackRequest? = null

        setContent {
            PhoebeTheme {
                Box(Modifier.size(800.dp, 520.dp)) {
                    TrackList(
                        tracks = tracks,
                        empty = "No songs",
                        catalogRefreshing = false,
                        onPlayTracks = { queue, index -> request = PlaybackRequest(queue, index) },
                        onAddToUpNext = {},
                        onDownload = {},
                    )
                }
            }
        }

        onNodeWithTag(PlaybackTestTags.playTrack(tracks[1].id)).performClick()

        val captured = assertNotNull(request)
        assertEquals(1, captured.index)
        assertEquals(tracks, captured.tracks)
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun desktopLibrarySongRowInvokesPlaybackRequestForTappedTrack() = runDesktopComposeUiTest(width = 1100, height = 720) {
        val tracks = playbackTracks()
        var request: PlaybackRequest? = null

        setContent {
            PhoebeTheme {
                Box(Modifier.size(1100.dp, 720.dp)) {
                    LibraryDesktopView(
                        catalog = CatalogSnapshot(tracksByParent = mapOf("all" to tracks)),
                        catalogRefreshing = false,
                        filter = LibraryFilterTab.Songs,
                        libraryUi = LibraryUiPreferences(),
                        onFilter = {},
                        onLibrarySortBy = {},
                        onLibraryAscending = {},
                        onLibraryColumns = {},
                        onArtist = {},
                        onAlbum = {},
                        onPlayTracks = { queue, index -> request = PlaybackRequest(queue, index) },
                        onAddToUpNext = {},
                        onDownload = {},
                    )
                }
            }
        }

        onNodeWithTag(PlaybackTestTags.playTrack(tracks[1].id)).performClick()

        val captured = assertNotNull(request)
        assertEquals(1, captured.index)
        assertEquals(tracks, captured.tracks)
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun albumDetailTrackRowInvokesPlaybackRequestForTappedTrack() = runDesktopComposeUiTest(width = 800, height = 620) {
        val tracks = playbackTracks()
        val album = Album(id = "album-1", title = "Regression Album", artist = "Fixture Artist")
        var request: PlaybackRequest? = null

        setContent {
            PhoebeTheme {
                Box(Modifier.size(800.dp, 620.dp)) {
                    AlbumDetailPanel(
                        album = album,
                        catalog = CatalogSnapshot(albums = listOf(album), tracksByParent = mapOf(album.id to tracks)),
                        libraryUi = LibraryUiPreferences(),
                        onBack = {},
                        onPlayTracks = { queue, index -> request = PlaybackRequest(queue, index) },
                        onAddToUpNext = {},
                        onDownload = {},
                        onDownloadAlbum = {},
                        onArtist = {},
                        onLibraryColumns = {},
                    )
                }
            }
        }

        onNodeWithTag(PlaybackTestTags.playTrack(tracks[2].id)).performClick()

        val captured = assertNotNull(request)
        assertEquals(2, captured.index)
        assertEquals(tracks, captured.tracks)
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun playlistDetailTrackRowInvokesPlaybackRequestForTappedVisibleTrack() = runDesktopComposeUiTest(width = 800, height = 620) {
        val tracks = playbackTracks()
        val playlist = Playlist(id = "playlist-1", title = "Regression Playlist", trackCount = tracks.size)
        var request: PlaybackRequest? = null

        setContent {
            PhoebeTheme {
                Box(Modifier.size(800.dp, 620.dp)) {
                    PlaylistDetailPanel(
                        playlist = playlist,
                        catalog = CatalogSnapshot(playlists = listOf(playlist), tracksByParent = mapOf(playlist.id to tracks)),
                        catalogRefreshing = false,
                        libraryUi = LibraryUiPreferences(),
                        onBack = {},
                        onPlayTracks = { queue, index -> request = PlaybackRequest(queue, index) },
                        onAddToUpNext = {},
                        onDownload = {},
                        onDownloadPlaylist = {},
                        onLibraryColumns = {},
                    )
                }
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(PlaybackTestTags.playTrack(tracks[1].id)).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(PlaybackTestTags.playTrack(tracks[1].id)).performClick()

        val captured = assertNotNull(request)
        assertEquals(1, captured.index)
        assertEquals(tracks, captured.tracks)
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun songDetailPlayButtonInvokesPlaybackRequest() = runDesktopComposeUiTest(width = 700, height = 520) {
        val track = playbackTracks().first()
        var played = false

        setContent {
            PhoebeTheme {
                Box(Modifier.size(700.dp, 520.dp)) {
                    SongDetailPanel(
                        track = track,
                        onBack = {},
                        onPlay = { played = true },
                        onAddToUpNext = {},
                        onDownload = {},
                    )
                }
            }
        }

        onNodeWithTag(PlaybackTestTags.playTrack(track.id)).performClick()

        assertTrue(played)
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun phoneSongDetailEditMetadataButtonInvokesEditorRequest() = runDesktopComposeUiTest(width = 430, height = 760) {
        val track = playbackTracks().first()
        var requestedTrack: Track? = null

        setContent {
            PhoebeTheme {
                Box(Modifier.size(430.dp, 760.dp)) {
                    CompositionLocalProvider(
                        LocalMetadataEditorActions provides MetadataEditorActions(
                            onRequestEdit = { requestedTrack = it },
                        ),
                    ) {
                        SongDetailPanel(
                            track = track,
                            onBack = {},
                            onPlay = {},
                            onAddToUpNext = {},
                            onDownload = {},
                        )
                    }
                }
            }
        }

        onNodeWithText("Edit Metadata").performClick()

        assertEquals(track.id, assertNotNull(requestedTrack).id)
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun upNextRowInvokesQueuePlaybackRequestForTappedUpcomingTrack() = runDesktopComposeUiTest(width = 520, height = 360) {
        val tracks = playbackTracks()
        var playedIndex: Int? = null

        setContent {
            PhoebeTheme {
                Box(Modifier.size(520.dp, 360.dp)) {
                    UpNextList(
                        currentTrack = tracks[0],
                        upNext = tracks.drop(1),
                        repeat = RepeatMode.Off,
                        onPlayQueue = { playedIndex = it },
                        onMoveUpNext = { _, _ -> },
                        onRemoveUpNext = {},
                    )
                }
            }
        }

        onNodeWithTag(PlaybackTestTags.playTrack(tracks[2].id)).performClick()

        assertEquals(1, playedIndex)
    }
}

private data class PlaybackRequest(
    val tracks: List<Track>,
    val index: Int,
)

private fun playbackTracks(): List<Track> =
    listOf(
        playbackTrack("track-1", "First Song"),
        playbackTrack("track-2", "Second Song"),
        playbackTrack("track-3", "Third Song"),
    )

private fun playbackTrack(id: String, title: String): Track =
    Track(
        id = id,
        title = title,
        artist = "Fixture Artist",
        album = "Regression Album",
        durationMs = 60_000L,
        streamUrl = "https://stream.example/$id.mp3",
        downloadUrl = "",
    )
