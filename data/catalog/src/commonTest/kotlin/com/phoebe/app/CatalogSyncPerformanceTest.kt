package com.phoebe.app

import com.phoebe.app.data.metadataFetchProgress
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CatalogSyncPhase
import com.phoebe.app.domain.CatalogSyncState
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CatalogSyncPerformanceTest {

    @Test
    fun metadataFetchProgressShowsWaitingPlaylistStep() {
        val waitingPlaylists = metadataFetchProgress(
            albumsDone = true,
            albumCount = 1200,
            artistsDone = true,
            artistCount = 400,
            playlistsDone = false,
            playlistCount = 0,
        )
        assertEquals("Fetching playlists…", waitingPlaylists.message)
        assertEquals("1200 albums · 400 artists · waiting for playlists", waitingPlaylists.detail)
    }

    @Test
    fun metadataFetchProgressShowsAllPendingInitially() {
        val initial = metadataFetchProgress(
            albumsDone = false,
            albumCount = 0,
            artistsDone = false,
            artistCount = 0,
            playlistsDone = false,
            playlistCount = 0,
        )
        assertEquals("Fetching albums, artists, playlists…", initial.message)
    }

    @Test
    fun catalogSyncStateCarriesProgressFields() {
        val state = CatalogSyncState(
            phase = CatalogSyncPhase.LoadingSongs,
            message = "Indexing songs…",
            detail = "1,000 / 5,000",
            loadedTracks = 1_000,
            totalTracks = 5_000,
            totalPlaylists = 12,
            warmedPlaylists = 3,
            progress = 0.2f,
        )
        assertEquals("1,000 / 5,000", state.detail)
        assertEquals(5_000, state.totalTracks)
        assertEquals(0.2f, state.progress)
        assertTrue(state.isActive)
    }

    @Test
    fun contentChecksumDetectsTrackChanges() {
        val base = sampleSnapshot(trackCount = 2)
        val changed = base.copy(
            tracksByParent = base.tracksByParent + ("plex:album-2" to listOf(extraTrack("plex:track-3"))),
        )
        assertNotEquals(base.contentChecksum(), changed.contentChecksum())
    }

    @Test
    fun contentChecksumIgnoresIdenticalSnapshots() {
        val snapshot = sampleSnapshot(trackCount = 2)
        assertEquals(snapshot.contentChecksum(), snapshot.copy().contentChecksum())
    }

    private fun sampleSnapshot(trackCount: Int): CatalogSnapshot {
        val album = Album(id = "plex:album-1", title = "Album", artist = "Artist")
        val tracks = (1..trackCount).map { index ->
            Track(
                id = "plex:track-$index",
                title = "Song $index",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000,
                streamUrl = "http://example/stream/$index",
                downloadUrl = "http://example/download/$index",
                parentAlbumId = album.id,
            )
        }
        return CatalogSnapshot(
            artists = listOf(Artist(id = "plex:artist-1", title = "Artist")),
            albums = listOf(album),
            playlists = listOf(Playlist(id = "plex:playlist-1", title = "Mix", trackCount = 0)),
            tracksByParent = mapOf(album.id to tracks),
        )
    }

    private fun extraTrack(id: String) = Track(
        id = id,
        title = "Extra",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        streamUrl = "http://example/stream/extra",
        downloadUrl = "http://example/download/extra",
        parentAlbumId = "plex:album-2",
    )
}

private fun CatalogSnapshot.contentChecksum(): Long {
    var hash = 17L
    hash = hash * 31 + artists.size
    hash = hash * 31 + albums.size
    hash = hash * 31 + playlists.size
    hash = hash * 31 + tracksByParent.values.sumOf { it.size }
    artists.forEach { hash = hash * 31 + it.id.hashCode() }
    albums.forEach { hash = hash * 31 + it.id.hashCode() }
    playlists.forEach { hash = hash * 31 + it.id.hashCode() }
    tracksByParent.values.flatten().forEach { hash = hash * 31 + it.id.hashCode() }
    return hash
}
