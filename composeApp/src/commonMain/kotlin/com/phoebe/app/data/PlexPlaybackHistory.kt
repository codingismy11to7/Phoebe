package com.phoebe.app.data

import com.phoebe.app.domain.Track

data class PlexPlaybackHistoryPage(
    val entries: List<PlexPlaybackHistoryEntry>,
    val offset: Int,
    val size: Int,
    val totalSize: Int?,
)

data class PlexPlaybackHistoryEntry(
    val ratingKey: String,
    val historyKey: String,
    val viewedAtMs: Long,
    val type: String?,
    val librarySectionId: String?,
    val title: String,
    val artist: String,
    val album: String,
)

data class PlexTrackPlaybackStat(
    val ratingKey: String,
    val viewCount: Long,
    val lastViewedAtMs: Long?,
    val title: String,
    val artist: String,
    val album: String,
) {
    fun toPlayHistoryTrack(catalogTrack: Track? = null): Track {
        val prefixedId = "plex:$ratingKey"
        return catalogTrack?.copy(
            title = catalogTrack.title.ifBlank { title },
            artist = catalogTrack.artist.ifBlank { artist },
            album = catalogTrack.album.ifBlank { album },
        ) ?: Track(
            id = prefixedId,
            title = title,
            artist = artist,
            album = album,
            durationMs = 0L,
            streamUrl = "",
            downloadUrl = "",
        )
    }
}
