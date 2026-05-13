package com.phoebe.app.data

data class PlexPlayQueue(
    val playQueueId: Long,
    val itemIdByRatingKey: Map<String, Long>,
)
