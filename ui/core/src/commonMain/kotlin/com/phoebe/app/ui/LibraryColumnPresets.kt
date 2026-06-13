package com.phoebe.app.ui

import com.phoebe.app.domain.LibraryColumnVisibility

/** When showing tracks outside Library (for example search), show all optional metadata columns. */
val FullTrackMetadataColumns = LibraryColumnVisibility(
    year = true,
    genre = true,
    filepath = true,
    audioCodec = true,
    bitrate = true,
    duration = true,
    sampleRate = true,
    fileType = true,
    dateAdded = true,
    rating = true,
    favorite = true,
)
