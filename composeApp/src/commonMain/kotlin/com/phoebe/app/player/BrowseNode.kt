package com.phoebe.app.player

import com.phoebe.app.domain.Track

data class BrowseNode(
    val mediaId: String,
    val title: String,
    val subtitle: String? = null,
    val isBrowsable: Boolean,
    val isPlayable: Boolean,
    val thumbUrl: String? = null,
    val track: Track? = null,
)
