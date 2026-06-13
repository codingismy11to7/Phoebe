package com.phoebe.app.ui

import com.phoebe.app.domain.MediaSourcesState
import com.phoebe.app.domain.PlexSession

fun canBrowseMainSections(session: PlexSession?, mediaSources: MediaSourcesState): Boolean =
    session?.selectedLibrary != null || mediaSources.localFolders.any { it.enabled }
