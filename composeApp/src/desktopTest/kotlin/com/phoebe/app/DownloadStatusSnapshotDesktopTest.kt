package com.phoebe.app

import com.phoebe.app.domain.DownloadItem
import com.phoebe.app.domain.DownloadState
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.mergeDownloadCopiesById
import com.phoebe.app.ui.DownloadStatusSnapshot
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadStatusSnapshotDesktopTest {
    @Test
    fun localUriWinsOverStaleFailedDownloadRow() {
        val track = track(localUri = "file:///downloads/song.mp3")
        val snapshot = DownloadStatusSnapshot(
            itemsByTrackId = mapOf(
                track.id to DownloadItem(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    state = DownloadState.Failed,
                    progress = 0f,
                ),
            ),
        )

        assertTrue(snapshot.isComplete(track))
        assertFalse(snapshot.isFailed(track))
        assertFalse(snapshot.isActive(track))
    }

    @Test
    fun completedDownloadRowWinsOverStaleActiveProgress() {
        val track = track()
        val snapshot = DownloadStatusSnapshot(
            itemsByTrackId = mapOf(
                track.id to DownloadItem(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    state = DownloadState.Complete,
                    progress = 1f,
                    localUri = "file:///downloads/song.mp3",
                ),
            ),
        )

        assertTrue(snapshot.isComplete(track))
        assertFalse(snapshot.isFailed(track))
        assertFalse(snapshot.isActive(track))
    }

    @Test
    fun savedLocalUriWinsOverStaleDownloadingState() {
        val track = track()
        val snapshot = DownloadStatusSnapshot(
            itemsByTrackId = mapOf(
                track.id to DownloadItem(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    state = DownloadState.Downloading,
                    progress = 0.99f,
                    localUri = "file:///downloads/song.mp3",
                ),
            ),
        )

        assertTrue(snapshot.isComplete(track))
        assertFalse(snapshot.isFailed(track))
        assertFalse(snapshot.isActive(track))
    }

    @Test
    fun downloadingRowIsActiveOnlyWhileDownloadJobIsRunning() {
        val track = track()
        val item = DownloadItem(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            state = DownloadState.Downloading,
            progress = 0.99f,
        )

        assertFalse(
            DownloadStatusSnapshot(itemsByTrackId = mapOf(track.id to item))
                .isActive(track),
        )
        assertTrue(
            DownloadStatusSnapshot(
                itemsByTrackId = mapOf(track.id to item),
                hasActiveDownloadJobs = true,
            ).isActive(track),
        )
    }

    @Test
    fun nonDownloadableFailedRowsDoNotMakePlaylistPartlyDownloaded() {
        val track = track(downloadUrl = "")
        val snapshot = DownloadStatusSnapshot(
            itemsByTrackId = mapOf(
                track.id to DownloadItem(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    state = DownloadState.Failed,
                    progress = 0f,
                ),
            ),
        )

        assertFalse(snapshot.isFailed(track))
        assertFalse(snapshot.isActive(track))
        assertFalse(snapshot.isComplete(track))
    }

    @Test
    fun duplicateTrackRowsMergeLocalDownloadState() {
        val staleCopy = track(localUri = null)
        val downloadedCopy = track(localUri = "file:///downloads/song.mp3")

        val merged = listOf(staleCopy, downloadedCopy).mergeDownloadCopiesById()

        assertEquals(1, merged.size)
        assertEquals(downloadedCopy.localUri, merged.single().localUri)
        assertTrue(DownloadStatusSnapshot().isComplete(merged.single()))
    }

    private fun track(
        localUri: String? = null,
        downloadUrl: String = "https://plex.example/downloads/t1.mp3",
    ): Track =
        Track(
            id = "plex:t1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 1_000,
            streamUrl = "https://plex.example/stream/t1.mp3",
            downloadUrl = downloadUrl,
            localUri = localUri,
        )
}
