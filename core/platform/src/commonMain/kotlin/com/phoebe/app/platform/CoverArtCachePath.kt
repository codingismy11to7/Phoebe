package com.phoebe.app.platform

/**
 * Path, relative to the storage root, of a cached cover-art file.
 *
 * Separate from data/artwork's `cachedArtworkPathForUrl`: that one is resolved against
 * the *download* directory by `PlatformStorage.writeBytes`, and is only populated when a
 * track is downloaded for offline use. Notifications need a local file for any played
 * track, so they need their own cache.
 */
fun coverArtCachePath(url: String): String {
    val extension = url.substringBefore('?')
        .substringAfterLast('/', "")
        .substringAfterLast('.', "")
        .takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }
        ?: "jpg"
    return "coverart/${url.stableCoverArtHash()}.$extension"
}

private fun String.stableCoverArtHash(): String {
    var hash = 1125899906842597L
    forEach { c -> hash = (hash * 31) + c.code }
    return hash.toString()
}
