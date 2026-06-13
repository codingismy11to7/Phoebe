package com.phoebe.app.player

object BrowseMediaIds {
    const val ROOT = "phoebe:root"
    const val ARTISTS = "phoebe:artists"
    const val ALBUMS = "phoebe:albums"
    const val PLAYLISTS = "phoebe:playlists"
    const val SIGN_IN = "phoebe:sign_in"

    fun artist(id: String): String = "phoebe:artist:$id"
    fun album(id: String): String = "phoebe:album:$id"
    fun playlist(id: String): String = "phoebe:playlist:$id"
    fun track(parentMediaId: String, trackId: String): String =
        "phoebe:track:${parentMediaId.length}:$parentMediaId$trackId"

    fun albumPlay(id: String): String = "phoebe:play:album:$id"
    fun playlistPlay(id: String): String = "phoebe:play:playlist:$id"
    fun playlistShuffle(id: String): String = "phoebe:shuffle:playlist:$id"

    fun parseArtistId(mediaId: String): String? =
        mediaId.removePrefix("phoebe:artist:").takeIf { it != mediaId }

    fun parseAlbumId(mediaId: String): String? =
        mediaId.removePrefix("phoebe:album:").takeIf { it != mediaId }

    fun parsePlaylistId(mediaId: String): String? =
        mediaId.removePrefix("phoebe:playlist:").takeIf { it != mediaId }

    fun parseTrackId(mediaId: String): BrowseTrackId? {
        val payload = mediaId.removePrefix("phoebe:track:").takeIf { it != mediaId } ?: return null
        val separator = payload.indexOf(':')
        if (separator <= 0) return null
        val parentLength = payload.substring(0, separator).toIntOrNull() ?: return null
        val parentStart = separator + 1
        val trackStart = parentStart + parentLength
        if (trackStart > payload.length) return null
        return BrowseTrackId(
            parentMediaId = payload.substring(parentStart, trackStart),
            trackId = payload.substring(trackStart),
        )
    }

    fun parsePlaylistShuffleId(mediaId: String): String? =
        mediaId.removePrefix("phoebe:shuffle:playlist:").takeIf { it != mediaId }

    fun parseAlbumPlayId(mediaId: String): String? =
        mediaId.removePrefix("phoebe:play:album:").takeIf { it != mediaId }

    fun parsePlaylistPlayId(mediaId: String): String? =
        mediaId.removePrefix("phoebe:play:playlist:").takeIf { it != mediaId }
}

data class BrowseTrackId(
    val parentMediaId: String,
    val trackId: String,
)
