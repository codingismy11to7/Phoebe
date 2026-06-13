package com.phoebe.app.data

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.isLikedSongsPlaylist

fun sortArtistsForLibrary(
    catalog: CatalogSnapshot,
    sortBy: LibrarySortBy,
    ascending: Boolean,
): List<Artist> {
    val artists = catalog.artists
    if (sortBy == LibrarySortBy.DateAdded) {
        return artists.sortedWith(
            if (ascending) compareBy<Artist>({ it.dateAddedMs ?: Long.MAX_VALUE }, { it.title.lowercase() })
            else compareByDescending<Artist> { it.dateAddedMs ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() },
        )
    }
    return artists.sortedWith(
        if (ascending) compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        else compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
}

fun sortAlbumsForLibrary(
    albums: List<Album>,
    sortBy: LibrarySortBy,
    ascending: Boolean,
): List<Album> =
    when (sortBy) {
        LibrarySortBy.Artist -> albums.sortedWith(
            if (ascending) compareBy<Album>({ it.artist.lowercase() }, { it.title.lowercase() })
            else compareByDescending<Album> { it.artist.lowercase() }.thenBy { it.title.lowercase() },
        )
        LibrarySortBy.Year -> {
            val (known, unknown) = albums.partition { it.year != null }
            val sortedKnown = known.sortedWith(
                if (ascending) compareBy<Album>({ it.year ?: Int.MAX_VALUE }, { it.title.lowercase() })
                else compareByDescending<Album> { it.year ?: Int.MIN_VALUE }.thenBy { it.title.lowercase() },
            )
            sortedKnown + unknown.sortedBy { it.title.lowercase() }
        }
        LibrarySortBy.DateAdded -> albums.sortedWith(
            if (ascending) compareBy<Album>({ it.dateAddedMs ?: Long.MAX_VALUE }, { it.title.lowercase() })
            else compareByDescending<Album> { it.dateAddedMs ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() },
        )
        else -> albums.sortedWith(
            if (ascending) compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            else compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }

fun sortTracksForLibrary(
    tracks: List<Track>,
    sortBy: LibrarySortBy,
    ascending: Boolean,
): List<Track> =
    when (sortBy) {
        LibrarySortBy.AlbumOrder -> if (ascending) tracks else tracks.asReversed()
        LibrarySortBy.PlaylistOrder -> if (ascending) tracks else tracks.asReversed()
        LibrarySortBy.Album -> tracks.sortedWith(
            if (ascending) compareBy<Track>({ it.album.lowercase() }, { it.title.lowercase() })
            else compareByDescending<Track> { it.album.lowercase() }.thenBy { it.title.lowercase() },
        )
        LibrarySortBy.Artist -> tracks.sortedWith(
            if (ascending) compareBy<Track>({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() })
            else compareByDescending<Track> { it.artist.lowercase() }
                .thenBy { it.album.lowercase() }
                .thenBy { it.title.lowercase() },
        )
        LibrarySortBy.Year -> {
            val (known, unknown) = tracks.partition { it.year != null }
            val sortedKnown = known.sortedWith(
                if (ascending) compareBy<Track>({ it.year ?: Int.MAX_VALUE }, { it.title.lowercase() })
                else compareByDescending<Track> { it.year ?: Int.MIN_VALUE }.thenBy { it.title.lowercase() },
            )
            sortedKnown + unknown.sortedBy { it.title.lowercase() }
        }
        LibrarySortBy.DateAdded -> tracks.sortedWith(
            if (ascending) compareBy<Track>({ it.dateAddedMs ?: Long.MAX_VALUE }, { it.title.lowercase() })
            else compareByDescending<Track> { it.dateAddedMs ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() },
        )
        else -> tracks.sortedWith(
            if (ascending) compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            else compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }

/** Filter a list of tracks by a free-form search query against title/artist/album. */
fun filterTracksByQuery(tracks: List<Track>, query: String): List<Track> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return tracks
    return tracks.filter {
        it.title.contains(trimmed, ignoreCase = true) ||
            it.artist.contains(trimmed, ignoreCase = true) ||
            it.album.contains(trimmed, ignoreCase = true)
    }
}

/** Filter albums by query against album title or artist. */
fun filterAlbumsByQuery(albums: List<Album>, query: String): List<Album> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return albums
    return albums.filter {
        it.title.contains(trimmed, ignoreCase = true) ||
            it.artist.contains(trimmed, ignoreCase = true)
    }
}

/** Filter artists by query against their title. */
fun filterArtistsByQuery(artists: List<Artist>, query: String): List<Artist> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return artists
    return artists.filter { it.title.contains(trimmed, ignoreCase = true) }
}

/** Filter playlists by query against playlist title, keeping liked songs first. */
fun filterPlaylistsByQuery(playlists: List<Playlist>, query: String): List<Playlist> {
    val trimmed = query.trim()
    val filtered = if (trimmed.isBlank()) {
        playlists
    } else {
        playlists.filter { it.title.contains(trimmed, ignoreCase = true) }
    }
    return filtered.sortedWith(compareByDescending<Playlist> { it.isLikedSongsPlaylist() })
}

fun artistAlbumCountSubtitle(artist: Artist): String {
    val word = if (artist.albumCount == 1) "album" else "albums"
    return "${artist.albumCount} $word"
}

fun songCountLabel(count: Int): String {
    val word = if (count == 1) "song" else "songs"
    return "$count $word"
}
