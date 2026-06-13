package com.phoebe.app.ui.preview

import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track

object PhoebePreviewData {
    val artist = Artist(
        id = "preview-artist",
        title = "The Neon Archive",
        albumCount = 3,
        songCount = 24,
        genre = "Electronic",
        rating = 4.5f,
        favorite = true,
    )

    val album = Album(
        id = "preview-album",
        title = "Signals After Midnight",
        artist = artist.title,
        year = 2026,
        genre = "Synthwave",
        rating = 4.0f,
        favorite = true,
    )

    val track = Track(
        id = "preview-track",
        title = "Glass City Lights",
        artist = artist.title,
        album = album.title,
        durationMs = 212_000L,
        streamUrl = "https://example.invalid/stream.flac",
        downloadUrl = "https://example.invalid/download.flac",
        filepath = "/music/the-neon-archive/glass-city-lights.flac",
        audioCodec = "flac",
        bitrateKbps = 1_012,
        rating = 4.5f,
    )

    val tracks = listOf(
        track,
        track.copy(
            id = "preview-track-2",
            title = "Low Orbit Hymn",
            bitrateKbps = 320,
            audioCodec = "mp3",
            filepath = "/music/the-neon-archive/low-orbit-hymn.mp3",
        ),
        track.copy(
            id = "preview-track-3",
            title = "Every Station Knows Your Name",
            bitrateKbps = 768,
        ),
    )

    val playlist = Playlist(
        id = "preview-playlist",
        title = "Late Night Queue",
        trackCount = tracks.size,
        favorite = true,
    )

    val catalog = CatalogSnapshot(
        artists = listOf(
            artist,
            artist.copy(
                id = "preview-artist-2",
                title = "Luna North",
                albumCount = 2,
                songCount = 18,
                genre = "Dream pop",
            ),
            artist.copy(
                id = "preview-artist-3",
                title = "Paper Satellites",
                albumCount = 4,
                songCount = 41,
                genre = "Indie",
            ),
        ),
        albums = listOf(
            album,
            album.copy(
                id = "preview-album-2",
                title = "Northern Lines",
                artist = "Luna North",
                year = 2024,
                genre = "Dream pop",
            ),
            album.copy(
                id = "preview-album-3",
                title = "Weather Patterns",
                artist = "Paper Satellites",
                year = 2021,
                genre = "Indie",
                favorite = false,
            ),
        ),
        playlists = listOf(
            playlist,
            playlist.copy(
                id = "preview-playlist-2",
                title = "Sunday Reset",
                trackCount = 12,
                favorite = false,
            ),
        ),
        tracksByParent = mapOf(
            "all" to tracks,
            album.id to tracks,
            playlist.id to tracks,
        ),
    )
}
