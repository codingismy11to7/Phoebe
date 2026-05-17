package com.phoebe.app.data

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
)
