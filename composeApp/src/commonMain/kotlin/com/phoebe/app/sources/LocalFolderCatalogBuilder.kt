package com.phoebe.app.sources

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.Track
import com.phoebe.app.data.enrichArtistAlbumCountsOnly
import com.phoebe.app.data.enrichArtistArtwork

object LocalFolderCatalogBuilder {

    suspend fun build(config: LocalFolderMediaSourceConfig): CatalogSnapshot {
        val root = config.rootUri
        val uris = runCatching { LocalLibraryIO.listAudioUris(root) }.getOrElse { emptyList() }
            .filter { runCatching { LocalLibraryIO.fileExists(it) }.getOrDefault(false) }
        if (uris.isEmpty()) return CatalogSnapshot()

        val prefix = "local_${config.id}"
        val tracksByAlbum = linkedMapOf<String, MutableList<Pair<String, Track>>>()

        for (uri in uris) {
            val meta = runCatching { LocalLibraryIO.readAudioMetadata(uri) }.getOrElse {
                AudioMetadata(title = null, artist = null, album = null, durationMs = 0L)
            }
            val parent = parentFolderLabel(uri)
            val albumTitle = meta.album?.takeIf { it.isNotBlank() } ?: parent
            val artistName = meta.artist?.takeIf { it.isNotBlank() } ?: "Local files"
            val trackTitle = meta.title?.takeIf { it.isNotBlank() }
                ?: uri.substringAfterLast('/').substringBeforeLast('.')
            val albumKey = "$prefix:album:${albumTitle.hashCode().toUInt()}"
            val trackId = "$prefix:track:${uri.hashCode()}"
            val track = Track(
                id = trackId,
                title = trackTitle,
                artist = artistName,
                album = albumTitle,
                durationMs = meta.durationMs,
                streamUrl = "",
                downloadUrl = "",
                thumbUrl = null,
                localUri = uri,
                year = meta.year,
                genre = meta.genre,
                filepath = filepathDisplay(uri),
                audioCodec = meta.audioCodec,
                bitrateKbps = meta.bitrateKbps,
            )
            tracksByAlbum.getOrPut(albumKey) { mutableListOf() }.add(albumTitle to track)
        }

        val albums = mutableListOf<Album>()
        val tracksByParent = mutableMapOf<String, List<Track>>()

        for ((albumId, pairs) in tracksByAlbum) {
            val albumTitle = pairs.first().first
            val tracks = pairs.map { it.second }
            val artistGuess = tracks.firstOrNull()?.artist ?: "Local files"
            albums.add(
                Album(
                    id = albumId,
                    title = albumTitle,
                    artist = artistGuess,
                    year = null,
                    thumbUrl = null,
                ),
            )
            tracksByParent[albumId] = tracks
        }

        val rawArtists = albums.map { it.artist }.distinct().map { name ->
            Artist(id = "$prefix:artist:${name.hashCode()}", title = name, thumbUrl = null, albumCount = 0)
        }
        val artists = enrichArtistAlbumCountsOnly(enrichArtistArtwork(rawArtists, albums), albums)

        return CatalogSnapshot(
            artists = artists,
            albums = albums,
            playlists = emptyList(),
            tracksByParent = tracksByParent,
            downloads = emptyList(),
        )
    }

    private fun filepathDisplay(uri: String): String {
        val noQuery = uri.substringBefore('?').trimEnd('/')
        val slash = noQuery.lastIndexOf('/')
        return if (slash >= 0) noQuery.substring(slash + 1).ifBlank { noQuery } else noQuery
    }

    private fun parentFolderLabel(uri: String): String {
        val path = uri.substringBefore('?').trimEnd('/')
        val last = path.lastIndexOf('/')
        if (last <= 0) return "Library"
        val second = path.lastIndexOf('/', last - 1)
        return if (second >= 0) {
            path.substring(second + 1, last).ifBlank { "Library" }
        } else {
            "Library"
        }
    }
}
