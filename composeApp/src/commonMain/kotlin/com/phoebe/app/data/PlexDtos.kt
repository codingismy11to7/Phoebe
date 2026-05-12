package com.phoebe.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexPinResponse(
    val id: Long,
    val code: String,
    @SerialName("authToken") val authToken: String? = null,
)

@Serializable
data class PlexUserResponse(
    val username: String? = null,
    @SerialName("authToken") val authToken: String? = null,
)

@Serializable
data class PlexDeviceDto(
    val name: String,
    @SerialName("clientIdentifier") val clientIdentifier: String,
    val owned: Boolean = false,
    val provides: String = "",
    val connections: List<PlexConnectionDto> = emptyList(),
)

@Serializable
data class PlexConnectionDto(
    val uri: String,
    val local: Boolean = false,
)

@Serializable
data class PlexMediaContainerResponse(
    @SerialName("MediaContainer") val mediaContainer: PlexMediaContainer = PlexMediaContainer(),
)

@Serializable
data class PlexMediaContainer(
    val size: Int = 0,
    val machineIdentifier: String? = null,
    val leafCountAdded: Int? = null,
    val leafCountRequested: Int? = null,
    @SerialName("Directory") val directories: List<PlexDirectoryDto> = emptyList(),
    @SerialName("Metadata") val metadata: List<PlexMetadataDto> = emptyList(),
)

@Serializable
data class PlexDirectoryDto(
    val key: String,
    val title: String,
    val type: String? = null,
    val thumb: String? = null,
    val leafCount: Int? = null,
)

@Serializable
data class PlexGenreTagDto(
    val tag: String? = null,
)

@Serializable
data class PlexMetadataDto(
    val ratingKey: String,
    val key: String? = null,
    val title: String,
    val type: String? = null,
    @SerialName("parentTitle") val parentTitle: String? = null,
    @SerialName("grandparentTitle") val grandparentTitle: String? = null,
    val year: Int? = null,
    val duration: Long? = null,
    val leafCount: Int? = null,
    val thumb: String? = null,
    @SerialName("Genre") val genreTags: List<PlexGenreTagDto>? = null,
    @SerialName("Media") val media: List<PlexMediaDto> = emptyList(),
)

@Serializable
data class PlexMediaDto(
    /** Plex often reports bitrate in kbps for audio; large values may be bits/sec. */
    val bitrate: Int? = null,
    @SerialName("audioCodec") val audioCodec: String? = null,
    @SerialName("Part") val parts: List<PlexPartDto> = emptyList(),
)

@Serializable
data class PlexPartDto(
    val key: String,
    val file: String? = null,
    val size: Long? = null,
)

@Serializable
data class MusicBrainzReleaseGroupSearchResponse(
    @SerialName("release-groups") val releaseGroups: List<MusicBrainzReleaseGroupDto> = emptyList(),
)

@Serializable
data class MusicBrainzReleaseGroupDto(
    val id: String,
)
