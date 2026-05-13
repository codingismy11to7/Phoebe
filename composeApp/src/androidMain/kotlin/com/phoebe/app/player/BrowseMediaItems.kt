package com.phoebe.app.player

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track

internal fun browseFolderItem(
    mediaId: String,
    title: String,
    artworkUri: Uri? = null,
): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .apply { artworkUri?.let { setArtworkUri(it) } }
                .build(),
        )
        .build()

internal fun browseTrackItem(track: Track): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.id)
        .setUri((track.localUri ?: track.streamUrl).toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setDisplayTitle(track.title)
                .setArtist(track.artist)
                .setAlbumArtist(track.artist)
                .setSubtitle(track.artist)
                .setAlbumTitle(track.album)
                .setDescription(track.descriptionForCarDisplay())
                .setDurationMs(track.durationMs.takeIf { it > 0L })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply { track.thumbUrl?.let { setArtworkUri(it.toUri()) } }
                .build(),
        )
        .build()

internal fun Artist.toBrowseItem(): MediaItem =
    browseFolderItem(
        mediaId = BrowseMediaIds.artist(id),
        title = title,
        artworkUri = thumbUrl?.toUri(),
    )

internal fun Album.toBrowseItem(): MediaItem =
    browseFolderItem(
        mediaId = BrowseMediaIds.album(id),
        title = title,
        artworkUri = thumbUrl?.toUri(),
    )

internal fun Playlist.toBrowseItem(): MediaItem =
    browseFolderItem(
        mediaId = BrowseMediaIds.playlist(id),
        title = title,
        artworkUri = thumbUrl?.toUri(),
    )

internal fun playbackMediaItem(track: Track): MediaItem = browseTrackItem(track)

private fun Track.descriptionForCarDisplay(): String =
    listOf(artist, album)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" - ")
