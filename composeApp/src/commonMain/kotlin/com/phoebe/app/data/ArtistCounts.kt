package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Track

/**
 * Album counts only (fast path during catalog refresh). Song totals are derived on the artist detail screen.
 */
internal fun enrichArtistAlbumCountsOnly(
    artists: List<Artist>,
    albums: List<Album>,
): List<Artist> =
    artists.map { artist ->
        val n = albums.count { albumMatchesArtist(it, artist.title) }
        artist.copy(albumCount = n, songCount = 0)
    }

internal fun enrichArtistArtwork(artists: List<Artist>, albums: List<Album>): List<Artist> =
    artists.map { artist ->
        if (!artist.thumbUrl.isNullOrBlank()) {
            artist
        } else {
            artist.copy(thumbUrl = albums.firstOrNull { it.artist == artist.title }?.thumbUrl)
        }
    }

fun catalogAlbumsForArtist(catalog: CatalogSnapshot, artistTitle: String): List<Album> =
    catalog.albums.filter { albumMatchesArtist(it, artistTitle) }.sortedBy { it.title.lowercase() }

fun catalogTracksForArtist(catalog: CatalogSnapshot, artistTitle: String): List<Track> =
    catalog.tracksByParent.values.asSequence()
        .flatten()
        .distinctBy { it.id }
        .filter { trackMatchesArtist(it, artistTitle) }
        .sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
        .toList()

private fun albumMatchesArtist(album: Album, artistTitle: String): Boolean {
    val t = artistTitle.trim()
    if (t.isEmpty()) return false
    val a = album.artist.trim()
    return a.equals(t, ignoreCase = true) ||
        a.startsWith("$t ", ignoreCase = true) ||
        a.startsWith("$t,", ignoreCase = true) ||
        a.startsWith("$t&", ignoreCase = true) ||
        a.contains(" $t ", ignoreCase = true) ||
        a.contains(" $t(", ignoreCase = true)
}

private fun trackMatchesArtist(track: Track, artistTitle: String): Boolean {
    val t = artistTitle.trim()
    if (t.isEmpty()) return false
    return track.artist.contains(t, ignoreCase = true) ||
        track.album.contains(t, ignoreCase = true)
}

fun catalogTracksForAlbum(catalog: CatalogSnapshot, albumId: String): List<Track> =
    catalog.tracksByParent[albumId].orEmpty()

/** Best-effort genre for an artist: most frequent non-blank genre across their tracks/albums. */
fun catalogArtistGenre(catalog: CatalogSnapshot, artistTitle: String): String? {
    val tracks = catalogTracksForArtist(catalog, artistTitle)
    val tally = LinkedHashMap<String, Int>()
    tracks.asSequence().mapNotNull { it.genre }.filter { it.isNotBlank() }.forEach {
        tally[it] = (tally[it] ?: 0) + 1
    }
    if (tally.isEmpty()) return null
    return tally.maxByOrNull { it.value }?.key
}

/** Total duration across all tracks for [artistTitle] (in ms). */
fun catalogArtistTotalDurationMs(catalog: CatalogSnapshot, artistTitle: String): Long =
    catalogTracksForArtist(catalog, artistTitle).sumOf { it.durationMs }

fun catalogAlbumTotalDurationMs(catalog: CatalogSnapshot, albumId: String): Long =
    catalogTracksForAlbum(catalog, albumId).sumOf { it.durationMs }

fun catalogAlbumTrackCount(catalog: CatalogSnapshot, albumId: String): Int =
    catalogTracksForAlbum(catalog, albumId).size

/** Best-effort codec label for the album (e.g. "FLAC"). */
fun catalogAlbumCodec(catalog: CatalogSnapshot, albumId: String): String? {
    val tracks = catalogTracksForAlbum(catalog, albumId)
    val tally = LinkedHashMap<String, Int>()
    tracks.asSequence().mapNotNull { it.audioCodec }.filter { it.isNotBlank() }.forEach {
        tally[it.uppercase()] = (tally[it.uppercase()] ?: 0) + 1
    }
    return tally.maxByOrNull { it.value }?.key
}

/** Best-effort genre label for the album. */
fun catalogAlbumGenre(catalog: CatalogSnapshot, albumId: String): String? {
    val tracks = catalogTracksForAlbum(catalog, albumId)
    val tally = LinkedHashMap<String, Int>()
    tracks.asSequence().mapNotNull { it.genre }.filter { it.isNotBlank() }.forEach {
        tally[it] = (tally[it] ?: 0) + 1
    }
    return tally.maxByOrNull { it.value }?.key
}

/** Average bitrate when known, otherwise null. */
fun catalogAlbumBitrateKbps(catalog: CatalogSnapshot, albumId: String): Int? {
    val rates = catalogTracksForAlbum(catalog, albumId).mapNotNull { it.bitrateKbps }.filter { it > 0 }
    if (rates.isEmpty()) return null
    return rates.average().toInt()
}

/** Top N most-representative tracks for an artist (currently: stable order by album then title). */
fun catalogArtistTopTracks(catalog: CatalogSnapshot, artistTitle: String, n: Int = 5): List<Track> =
    catalogTracksForArtist(catalog, artistTitle).take(n)
