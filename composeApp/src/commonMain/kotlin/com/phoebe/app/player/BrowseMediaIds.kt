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

    fun parseArtistId(mediaId: String): String? =
        mediaId.removePrefix("phoebe:artist:").takeIf { it != mediaId }

    fun parseAlbumId(mediaId: String): String? =
        mediaId.removePrefix("phoebe:album:").takeIf { it != mediaId }

    fun parsePlaylistId(mediaId: String): String? =
        mediaId.removePrefix("phoebe:playlist:").takeIf { it != mediaId }
}
