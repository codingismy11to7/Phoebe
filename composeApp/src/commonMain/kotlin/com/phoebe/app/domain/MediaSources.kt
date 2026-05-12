package com.phoebe.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class LocalFolderMediaSourceConfig(
    val id: String,
    val rootUri: String,
    val label: String = "Local folder",
    val enabled: Boolean = true,
)

@Serializable
data class MediaSourcesState(
    val localFolders: List<LocalFolderMediaSourceConfig> = emptyList(),
)
