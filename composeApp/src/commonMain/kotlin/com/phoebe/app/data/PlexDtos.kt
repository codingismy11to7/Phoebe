package com.phoebe.app.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    @SerialName("accessToken") val accessToken: String? = null,
    @SerialName("httpsRequired") val httpsRequired: Boolean = false,
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
    val totalSize: Int? = null,
    val offset: Int? = null,
    val machineIdentifier: String? = null,
    val leafCountAdded: Int? = null,
    val leafCountRequested: Int? = null,
    @SerialName("playQueueID") val playQueueId: Long? = null,
    @SerialName("playQueueSelectedItemID") val playQueueSelectedItemId: Long? = null,
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
    val addedAt: Long? = null,
)

@Serializable
data class PlexGenreTagDto(
    val tag: String? = null,
)

@Serializable
data class PlexMetadataDto(
    val ratingKey: String,
    val historyKey: String? = null,
    @SerialName("playlistItemID") val playlistItemId: Long? = null,
    @SerialName("playQueueItemID") val playQueueItemId: Long? = null,
    val key: String? = null,
    val title: String,
    val type: String? = null,
    val viewedAt: Long? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val librarySectionID: String? = null,
    @SerialName("parentRatingKey") val parentRatingKey: String? = null,
    @SerialName("grandparentRatingKey") val grandparentRatingKey: String? = null,
    @SerialName("parentTitle") val parentTitle: String? = null,
    @SerialName("grandparentTitle") val grandparentTitle: String? = null,
    @SerialName("parentThumb") val parentThumb: String? = null,
    @SerialName("grandparentThumb") val grandparentThumb: String? = null,
    val year: Int? = null,
    @SerialName("parentYear") val parentYear: Int? = null,
    val duration: Long? = null,
    val leafCount: Int? = null,
    val thumb: String? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
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

@OptIn(ExperimentalSerializationApi::class)
private object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        return (element as? JsonPrimitive)?.contentOrNull
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

@Serializable
data class MusicBrainzReleaseGroupSearchResponse(
    @SerialName("release-groups") val releaseGroups: List<MusicBrainzReleaseGroupDto> = emptyList(),
)

@Serializable
data class MusicBrainzReleaseGroupDto(
    val id: String,
)
