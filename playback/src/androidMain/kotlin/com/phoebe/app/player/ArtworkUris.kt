package com.phoebe.app.player

import android.net.Uri
import com.phoebe.app.AndroidContextHolder

enum class ArtworkType(val segment: String) {
    ALBUM("album"),
    ARTIST("artist"),
    PLAYLIST("playlist"),
    TRACK("track"),
}

/** Authority suffix appended to the application id. */
const val ArtworkAuthoritySuffix: String = ".artwork"

fun artworkAuthority(packageName: String): String = packageName + ArtworkAuthoritySuffix

/**
 * Builds `content://<packageName>.artwork/<type>/<id>`.
 *
 * Uses catalog ids rather than remote URLs so provider credentials (notably
 * the Plex token) never leave this process.
 */
fun artworkUri(packageName: String, type: ArtworkType, id: String): Uri =
    Uri.Builder()
        .scheme("content")
        .authority(artworkAuthority(packageName))
        .appendPath(type.segment)
        .appendPath(id)
        .build()

/** Application id of the running process, for building artwork authorities. */
internal fun runningPackageName(): String = AndroidContextHolder.application.packageName

fun parseArtworkUri(uri: Uri): Pair<ArtworkType, String>? {
    if (uri.scheme != "content") return null
    val segments = uri.pathSegments
    if (segments.size != 2) return null
    val type = ArtworkType.entries.firstOrNull { it.segment == segments[0] } ?: return null
    val id = segments[1].takeIf { it.isNotBlank() } ?: return null
    return type to id
}
