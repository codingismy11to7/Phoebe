package com.phoebe.app.sources

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track

object CatalogMerge {
    private const val Sep = ":"

    fun withPrefix(prefix: String, snapshot: CatalogSnapshot): CatalogSnapshot {
        val p = "$prefix$Sep"
        return CatalogSnapshot(
            artists = snapshot.artists.map { it.copy(id = it.id.withPrefix(p)) },
            albums = snapshot.albums.map { it.copy(id = it.id.withPrefix(p)) },
            playlists = snapshot.playlists.map { it.copy(id = it.id.withPrefix(p)) },
            tracksByParent = snapshot.tracksByParent.mapKeys { (k, _) -> k.withPrefix(p) }.mapValues { (_, tracks) ->
                tracks.map { t ->
                    t.copy(
                        id = t.id.withPrefix(p),
                        parentAlbumId = t.parentAlbumId?.let { if (it.startsWith(p)) it else p + it },
                    )
                }
            },
            popularTracksByArtist = snapshot.popularTracksByArtist
                .mapKeys { (k, _) -> k.withPrefix(p) }
                .mapValues { (_, tracks) ->
                    tracks.map { t ->
                        t.copy(
                            id = t.id.withPrefix(p),
                            parentAlbumId = t.parentAlbumId?.let { if (it.startsWith(p)) it else p + it },
                        )
                    }
                },
            popularTracksByLibrary = snapshot.popularTracksByLibrary
                .mapKeys { (k, _) -> k.withPrefix(p) }
                .mapValues { (_, tracks) ->
                    tracks.map { t ->
                        t.copy(
                            id = t.id.withPrefix(p),
                            parentAlbumId = t.parentAlbumId?.let { if (it.startsWith(p)) it else p + it },
                        )
                    }
                },
            similarArtistsByArtist = snapshot.similarArtistsByArtist
                .mapKeys { (k, _) -> k.withPrefix(p) }
                .mapValues { (_, artists) -> artists.map { artist -> artist.copy(id = artist.id.withPrefix(p)) } },
            collectionValues = snapshot.collectionValues,
            collectionValueLoads = snapshot.collectionValueLoads,
            collectionTags = snapshot.collectionTags.map { it.copy(itemId = p + it.itemId) },
            downloads = snapshot.downloads,
            remotePageInfo = snapshot.remotePageInfo,
        )
    }

    private fun String.withPrefix(prefix: String): String =
        if (startsWith(prefix)) this else prefix + this

    fun merge(first: CatalogSnapshot, vararg rest: CatalogSnapshot): CatalogSnapshot {
        if (rest.isEmpty()) return first
        val all = listOf(first) + rest
        return CatalogSnapshot(
            artists = all.flatMap { it.artists },
            albums = all.flatMap { it.albums },
            playlists = all.flatMap { it.playlists }.distinctBy { it.id },
            tracksByParent = all.fold(emptyMap()) { acc, s -> acc + s.tracksByParent },
            popularTracksByArtist = all.fold(emptyMap()) { acc, s -> acc + s.popularTracksByArtist },
            popularTracksByLibrary = all.fold(emptyMap()) { acc, s -> acc + s.popularTracksByLibrary },
            similarArtistsByArtist = all.fold(emptyMap()) { acc, s -> acc + s.similarArtistsByArtist },
            collectionValues = all.flatMap { it.collectionValues }.distinct(),
            collectionValueLoads = all.flatMap { it.collectionValueLoads }.distinct(),
            collectionTags = all.flatMap { it.collectionTags }.distinct(),
            downloads = all.flatMap { it.downloads }.distinctBy { it.trackId },
            remotePageInfo = all.asReversed().firstOrNull { it.remotePageInfo.hasAny }?.remotePageInfo
                ?: first.remotePageInfo,
        )
    }

    fun stripPlexId(prefixedId: String): String =
        if (prefixedId.startsWith("plex$Sep")) prefixedId.removePrefix("plex$Sep") else prefixedId
}
