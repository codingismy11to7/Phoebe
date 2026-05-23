package com.phoebe.app.player

import com.phoebe.app.domain.Track
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom

data class CastMediaDescriptor(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val streamUrl: String,
    val castUrl: String,
    val contentType: String,
    val downloadUrl: String,
    val thumbUrl: String?,
    val filepath: String?,
    val audioCodec: String?,
) {
    val transcodesOriginal: Boolean get() = castUrl != streamUrl
}

object CastMediaCustomDataKeys {
    const val TrackId = "phoebeTrackId"
    const val Title = "title"
    const val Artist = "artist"
    const val Album = "album"
    const val DurationMs = "durationMs"
    const val StreamUrl = "streamUrl"
    const val CastUrl = "castUrl"
    const val DownloadUrl = "downloadUrl"
    const val ThumbUrl = "thumbUrl"
    const val Filepath = "filepath"
    const val AudioCodec = "audioCodec"
}

fun Track.toCastMediaDescriptor(): CastMediaDescriptor {
    val castUrl = chromecastMediaUrl()
    return CastMediaDescriptor(
        trackId = id,
        title = title.ifBlank { "Chromecast audio" },
        artist = artist,
        album = album,
        durationMs = durationMs,
        streamUrl = streamUrl,
        castUrl = castUrl,
        contentType = chromecastContentType(castUrl),
        downloadUrl = downloadUrl,
        thumbUrl = thumbUrl,
        filepath = filepath,
        audioCodec = audioCodec,
    )
}

fun castTrackFromMediaFields(
    trackId: String?,
    title: String?,
    artist: String?,
    album: String?,
    durationMs: Long,
    streamUrl: String?,
    castUrl: String?,
    downloadUrl: String?,
    thumbUrl: String?,
    filepath: String?,
    audioCodec: String?,
): Track {
    val resolvedStreamUrl = streamUrl?.takeIf { it.isNotBlank() }
        ?: castUrl?.takeIf { it.isNotBlank() }
        ?: ""
    return Track(
        id = trackId?.takeIf { it.isNotBlank() } ?: "cast:${resolvedStreamUrl.hashCode()}",
        title = title?.takeIf { it.isNotBlank() } ?: "Chromecast audio",
        artist = artist.orEmpty(),
        album = album.orEmpty(),
        durationMs = durationMs.coerceAtLeast(0L),
        streamUrl = resolvedStreamUrl,
        downloadUrl = downloadUrl.orEmpty(),
        thumbUrl = thumbUrl?.takeIf { it.isNotBlank() },
        filepath = filepath?.takeIf { it.isNotBlank() },
        audioCodec = audioCodec?.takeIf { it.isNotBlank() },
    )
}

fun Track.matchesCastMedia(remoteTrack: Track, remoteCastUrl: String? = null): Boolean {
    if (id.isNotBlank() && id == remoteTrack.id) return true
    if (streamUrl.isNotBlank() && streamUrl == remoteTrack.streamUrl) return true
    val descriptor = toCastMediaDescriptor()
    if (descriptor.castUrl.isNotBlank() && descriptor.castUrl == remoteTrack.streamUrl) return true
    val castUrl = remoteCastUrl?.takeIf { it.isNotBlank() } ?: return false
    return streamUrl == castUrl || descriptor.castUrl == castUrl
}

private fun Track.chromecastMediaUrl(): String {
    if (hasChromecastDirectPlayableCodec()) return streamUrl
    val ratingKey = id.removePrefix("plex:").takeIf { id.startsWith("plex:") && it.isNotBlank() }
        ?: return streamUrl
    val parsed = runCatching { Url(streamUrl) }.getOrNull() ?: return streamUrl
    val token = parsed.parameters["X-Plex-Token"].orEmpty()
    if (parsed.protocol.name.isBlank() || parsed.host.isBlank() || token.isBlank()) return streamUrl
    return runCatching {
        URLBuilder()
            .takeFrom(parsed)
            .apply {
                encodedPath = "/music/:/transcode/universal/start.mp3"
                parameters.clear()
                parameters.append("path", "/library/metadata/$ratingKey")
                parameters.append("mediaIndex", "0")
                parameters.append("partIndex", "0")
                parameters.append("protocol", "http")
                parameters.append("format", "mp3")
                parameters.append("audioCodec", "mp3")
                parameters.append("directPlay", "0")
                parameters.append("directStream", "0")
                parameters.append("X-Plex-Token", token)
            }
            .buildString()
    }.getOrDefault(streamUrl)
}

private fun Track.hasChromecastDirectPlayableCodec(): Boolean =
    when (audioCodec?.lowercase()) {
        "aac", "mp3", "mp4", "m4a" -> true
        else -> {
            val path = filepath ?: streamUrl.substringBefore('?').substringBefore('#')
            when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
                "aac", "mp3", "m4a", "mp4" -> true
                else -> false
            }
        }
    }

private fun Track.chromecastContentType(mediaUrl: String): String =
    if (mediaUrl != streamUrl) {
        "audio/mpeg"
    } else {
        chromecastDirectContentType()
    }

private fun Track.chromecastDirectContentType(): String =
    when (audioCodec?.lowercase()) {
        "aac" -> "audio/aac"
        "mp3" -> "audio/mpeg"
        "alac", "m4a", "mp4" -> "audio/mp4"
        "flac" -> "audio/flac"
        "ogg", "opus", "vorbis" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> {
            val path = filepath ?: streamUrl.substringBefore('?').substringBefore('#')
            when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
                "aac" -> "audio/aac"
                "m4a", "mp4" -> "audio/mp4"
                "flac" -> "audio/flac"
                "ogg", "oga", "opus" -> "audio/ogg"
                "wav" -> "audio/wav"
                else -> "audio/mpeg"
            }
        }
    }
