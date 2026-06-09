package com.phoebe.app.player

import com.phoebe.app.domain.Track
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom

/** Plex rating key for universal transcode URLs, with or without a `plex:` id prefix. */
internal fun Track.plexRatingKey(): String? {
    if (id.startsWith("plex:")) {
        val raw = id.removePrefix("plex:")
        return raw.substringBefore(':').takeIf { it.isNotBlank() }
    }
    return id.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
}

internal fun Track.plexUniversalMp3TranscodeUrl(): String? {
    val ratingKey = plexRatingKey() ?: return null
    val parsed = runCatching { Url(streamUrl) }.getOrNull() ?: return null
    val token = parsed.parameters["X-Plex-Token"].orEmpty()
    if (parsed.protocol.name.isBlank() || parsed.host.isBlank() || token.isBlank()) return null
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
    }.getOrNull()
}

internal fun Track.hasChromecastDirectPlayableCodec(): Boolean =
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

/**
 * Flatpak sandboxes cannot use JavaFX Media and Java Sound does not decode M4A/AAC/ALAC.
 * Reuse Plex's universal MP3 transcode endpoint (same as Chromecast) for those streams.
 */
internal fun flatpakSandboxSampledPlaybackExtension(
    audioCodec: String?,
    filepath: String?,
    streamUrl: String,
): String? {
    val codec = audioCodec?.lowercase()?.let { normalizeAudioCodecSuffix(it) }
    if (codec != null) {
        flatpakSampledPlaybackExtensionFromSuffix(codec)?.let { return it }
    }
    val path = filepath ?: streamUrl.substringBefore('?').substringBefore('#')
    return flatpakSampledPlaybackExtensionFromSuffix(
        path.substringAfterLast('.', missingDelimiterValue = ""),
    )
}

internal fun flatpakSampledPlaybackExtensionFromSuffix(extension: String): String? =
    when (extension.lowercase()) {
        "mp3", "mpeg", "mpga",
        "wav", "wave", "aif", "aiff", "flac", "ogg", "opus",
        -> extension.lowercase().let { if (it == "mpeg" || it == "mpga") "mp3" else it }
        else -> null
    }

private fun normalizeAudioCodecSuffix(codec: String): String =
    when (codec) {
        "mpeg", "mpga" -> "mp3"
        else -> codec
    }
