package com.phoebe.app

import com.phoebe.app.domain.DownloadItem
import com.phoebe.app.domain.DownloadState
import com.phoebe.app.domain.DownloadStatusEvent
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.mergeDownloadCopiesById
import com.phoebe.app.ui.DownloadStatusSnapshot
import com.phoebe.app.ui.activeDownloadActionLabel
import com.phoebe.app.ui.downloadFailureStatusLabel
import com.phoebe.app.ui.downloadActionProgress
import com.phoebe.app.ui.downloadActionPercentLabel
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
    fun downloadableLocalUriWinsOverStaleFailedDownloadRowForAnyProvider() {
        val track = track(
            id = "custom-provider:t1",
            localUri = "file:///downloads/song.mp3",
            downloadUrl = "https://music.example/downloads/t1.mp3",
        )
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
    fun remoteLocalUriCountsAsDownloadedEvenWhenDownloadUrlIsStale() {
        val track = track(localUri = "file:///downloads/song.mp3", downloadUrl = "")

        assertTrue(DownloadStatusSnapshot().isComplete(track))
    }

    @Test
    fun localSourceUriDoesNotCountAsDownloaded() {
        val track = track(
            id = "local:t1",
            localUri = "file:///music/song.mp3",
            downloadUrl = "",
        )

        assertFalse(DownloadStatusSnapshot().isComplete(track))
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
    fun staleFailedEventDoesNotOverwriteCompletedSnapshot() {
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
                    updatedAtMs = 200L,
                ),
            ),
        )

        snapshot.apply(
            DownloadStatusEvent(
                items = listOf(
                    DownloadItem(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        state = DownloadState.Failed,
                        progress = 0f,
                        updatedAtMs = 100L,
                    ),
                ),
            ),
        )

        assertTrue(snapshot.isComplete(track))
        assertFalse(snapshot.isFailed(track))
        assertEquals(DownloadState.Complete, snapshot.itemFor(track)?.state)
    }

    @Test
    fun completedSnapshotDoesNotRegressToBufferedQueuedEvent() {
        val track = track()
        val snapshot = DownloadStatusSnapshot()

        snapshot.replaceItems(
            listOf(
                DownloadItem(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    state = DownloadState.Complete,
                    progress = 1f,
                    localUri = "file:///downloads/song.mp3",
                    updatedAtMs = 200L,
                ),
            ),
        )
        snapshot.apply(
            DownloadStatusEvent(
                items = listOf(
                    DownloadItem(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        state = DownloadState.Queued,
                        progress = 0f,
                        updatedAtMs = 150L,
                    ),
                ),
            ),
        )

        assertTrue(snapshot.isComplete(track))
        assertFalse(snapshot.isActive(track))
        assertEquals(DownloadState.Complete, snapshot.itemFor(track)?.state)
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
    fun activeEventMarksVisibleRowActiveWithoutFullSnapshotRefresh() {
        val track = track()
        val snapshot = DownloadStatusSnapshot()

        snapshot.apply(
            DownloadStatusEvent(
                items = listOf(
                    DownloadItem(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        state = DownloadState.Downloading,
                        progress = 0.25f,
                        updatedAtMs = 123L,
                    ),
                ),
            ),
        )

        assertTrue(snapshot.isActive(track))
        assertEquals(0.25f, snapshot.itemFor(track)?.progress)
    }

    @Test
    fun visibleRowProgressStaysPreciseWhileCollectionSummaryUsesCoarseProgress() {
        val track = track()
        val snapshot = DownloadStatusSnapshot()

        snapshot.apply(
            DownloadStatusEvent(
                items = listOf(
                    DownloadItem(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        state = DownloadState.Downloading,
                        progress = 0.23f,
                        updatedAtMs = 123L,
                    ),
                ),
            ),
        )

        assertEquals(0.23f, snapshot.itemFor(track)?.progress)
        assertEquals(0.25f, snapshot.summarize(listOf(track)).activeProgress)
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

    @Test
    fun downloadActionProgressSumsActiveItemsAcrossBatch() {
        val activeItems = listOf(
            downloadItem("plex:t1", DownloadState.Downloading, progress = 0.5f),
        ) + (2..10).map { index ->
            downloadItem("plex:t$index", DownloadState.Queued, progress = 0f)
        }

        val progress = requireNotNull(
            downloadActionProgress(
                completed = 0,
                activeCount = activeItems.size,
                activeProgress = activeItems.sumOf { it.progress.toDouble() }.toFloat(),
                total = 10,
            ),
        )

        assertEquals(0.05f, progress, 0.0001f)
        assertEquals("5%", downloadActionPercentLabel(progress))
    }

    @Test
    fun downloadStatusSummaryAggregatesLargePlaylistWithoutIntermediateLists() {
        val tracks = (1..1000).map { index -> track(id = "plex:t$index") }
        val snapshot = DownloadStatusSnapshot(
            itemsByTrackId = tracks.associate { track ->
                track.id to DownloadItem(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    state = if (track.id == "plex:t1") DownloadState.Downloading else DownloadState.Queued,
                    progress = if (track.id == "plex:t1") 0.5f else 0f,
                    updatedAtMs = 123L,
                )
            },
        )

        val summary = snapshot.summarize(tracks)

        assertEquals(1000, summary.total)
        assertEquals(1000, summary.active)
        assertEquals(0.5f, summary.activeProgress)
        assertEquals(0, summary.complete)
        assertEquals(0, summary.failed)
    }

    @Test
    fun downloadPercentLabelShowsStartedSubPercentProgress() {
        assertEquals("1%", downloadActionPercentLabel(0.005f))
        assertEquals("0%", downloadActionPercentLabel(0f))
    }

    @Test
    fun partialDownloadStatusShowsExactFailureCountInsteadOfRoundedPercent() {
        assertEquals("1 failed", downloadFailureStatusLabel(1))
        assertEquals("2 failed", downloadFailureStatusLabel(2))
    }

    @Test
    fun activeDownloadActionLabelStaysCompact() {
        assertEquals("Downloading", activeDownloadActionLabel())
    }

    private fun track(
        id: String = "plex:t1",
        localUri: String? = null,
        downloadUrl: String = "https://plex.example/downloads/t1.mp3",
    ): Track =
        Track(
            id = id,
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 1_000,
            streamUrl = "https://plex.example/stream/t1.mp3",
            downloadUrl = downloadUrl,
            localUri = localUri,
        )

    private fun downloadItem(id: String, state: DownloadState, progress: Float): DownloadItem =
        DownloadItem(
            trackId = id,
            title = "Song $id",
            artist = "Artist",
            state = state,
            progress = progress,
        )
}
