package com.phoebe.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.phoebe.app.data.catalogAlbumsForArtist
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LibraryColumnVisibility
import com.phoebe.app.domain.RecentlyAddedKind
import com.phoebe.app.domain.Track

@Composable
internal fun RecentlyAddedScreen(
    kind: RecentlyAddedKind,
    catalog: CatalogSnapshot,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onSong: (Track) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val page = remember(kind, catalog, nowMs) {
        RecentlyAddedPage.from(kind, catalog, nowMs)
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        RecentlyAddedHeader(page, onBack)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            when (kind) {
                RecentlyAddedKind.Songs -> RecentlyAddedSongs(
                    tracks = page.tracks,
                    compact = maxWidth < 700.dp,
                    onSong = onSong,
                    onPlayTracks = onPlayTracks,
                    onAddToUpNext = onAddToUpNext,
                    onDownload = onDownload,
                    modifier = Modifier.fillMaxSize(),
                )
                RecentlyAddedKind.Artists -> RecentlyAddedArtists(
                    catalog = catalog,
                    artists = page.artists,
                    compact = maxWidth < 700.dp,
                    onArtist = onArtist,
                    modifier = Modifier.fillMaxSize(),
                )
                RecentlyAddedKind.Albums -> RecentlyAddedAlbums(
                    albums = page.albums,
                    compact = maxWidth < 700.dp,
                    onAlbum = onAlbum,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun RecentlyAddedHeader(page: RecentlyAddedPage, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .background(PhoebeUi.elevatedFill)
                .border(BorderStroke(1.dp, PhoebeUi.border), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(PhoebeIcon.Back, tint = PhoebeUi.primaryText, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Recently Added".uppercase(),
                color = PhoebeUi.mutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
            )
            Text(page.title, color = PhoebeUi.primaryText, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("${page.count} from the last 7 days", color = PhoebeUi.secondaryText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RecentlyAddedSongs(
    tracks: List<Track>,
    compact: Boolean,
    onSong: (Track) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onAddToUpNext: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        RecentlyAddedEmpty("No songs were added in the last 7 days.", modifier)
        return
    }
    if (!compact) {
        Column(modifier) {
            SongsTableHeader(LibraryColumnVisibility())
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        track = track,
                        selected = false,
                        columns = LibraryColumnVisibility(),
                        onSelect = { onSong(track) },
                        onPlay = { onPlayTracks(tracks, index) },
                        onAddToUpNext = { onAddToUpNext(track) },
                        onDownload = { onDownload(track) },
                    )
                }
            }
        }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            RecentlyAddedTrackCard(
                track = track,
                onClick = { onSong(track) },
                onPlay = { onPlayTracks(tracks, index) },
            )
        }
    }
}

@Composable
private fun RecentlyAddedArtists(
    catalog: CatalogSnapshot,
    artists: List<Artist>,
    compact: Boolean,
    onArtist: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) {
        RecentlyAddedEmpty("No artists were added in the last 7 days.", modifier)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (compact) 132.dp else 172.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(artists, key = { it.id }) { artist ->
            val thumb = catalogAlbumsForArtist(catalog, artist.title).firstOrNull { it.thumbUrl != null }?.thumbUrl
            RecentlyAddedMediaCard(
                title = artist.title,
                subtitle = "${artist.albumCount} albums",
                dateAddedMs = artist.dateAddedMs,
                nowMs = LocalNowMs.current,
                thumbUrl = thumb,
                circular = true,
                sharedKey = "artist:${artist.id}",
                onClick = { onArtist(artist) },
            )
        }
    }
}

@Composable
private fun RecentlyAddedAlbums(
    albums: List<Album>,
    compact: Boolean,
    onAlbum: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) {
        RecentlyAddedEmpty("No albums were added in the last 7 days.", modifier)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (compact) 132.dp else 172.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            RecentlyAddedMediaCard(
                title = album.title,
                subtitle = album.artist,
                dateAddedMs = album.dateAddedMs,
                nowMs = LocalNowMs.current,
                thumbUrl = album.thumbUrl,
                circular = false,
                sharedKey = "album:${album.id}",
                onClick = { onAlbum(album) },
            )
        }
    }
}

@Composable
private fun RecentlyAddedMediaCard(
    title: String,
    subtitle: String,
    dateAddedMs: Long?,
    nowMs: Long,
    thumbUrl: String?,
    circular: Boolean,
    sharedKey: String? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val artModifier = Modifier.fillMaxWidth().aspectRatio(1f).sharedArtworkTransition(sharedKey)
        if (circular) {
            ArtworkImage(title, thumbUrl, artModifier.clip(CircleShape), radius = 999.dp, elevated = false)
        } else {
            ArtworkImage(title, thumbUrl, artModifier, radius = 10.dp, elevated = false)
        }
        Text(
            title,
            color = PhoebeUi.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedBoundsTransition(sharedKey?.let { "$it:title" }),
        )
        Text(
            subtitle,
            color = PhoebeUi.secondaryText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedBoundsTransition(sharedKey?.let { "$it:subtitle" }),
        )
        Text(dateAddedMs?.let { formatLastPlayed(it, nowMs) } ?: "Date unknown", color = PhoebeUi.mutedText, fontSize = 10.sp)
    }
}

@Composable
private fun RecentlyAddedTrackCard(track: Track, onClick: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            track.title,
            track.thumbUrl,
            Modifier.size(52.dp).sharedArtworkTransition("song:${track.id}"),
            radius = 8.dp,
            elevated = false,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track.title,
                color = PhoebeUi.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedBoundsTransition("song:${track.id}:title"),
            )
            Text("${track.artist} • ${track.album}", color = PhoebeUi.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.dateAddedMs?.let { formatLastPlayed(it, LocalNowMs.current) } ?: "Date unknown", color = PhoebeUi.mutedText, fontSize = 10.sp)
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlay)
                .background(PhoebeUi.accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            PhoebeIconView(PhoebeIcon.Play, tint = PhoebeUi.accentLight, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun RecentlyAddedEmpty(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = PhoebeUi.secondaryText, fontSize = 14.sp)
    }
}

private data class RecentlyAddedPage(
    val title: String,
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
) {
    val count: Int
        get() = tracks.size + artists.size + albums.size

    companion object {
        fun from(kind: RecentlyAddedKind, catalog: CatalogSnapshot, nowMs: Long): RecentlyAddedPage {
            val cutoffMs = nowMs - RecentlyAddedWindowMs
            val albumAddedByTitle = albumAddedByTitle(catalog)
            val artistAddedByTitle = artistAddedByTitle(catalog)
            val tracks = catalog.tracksByParent.values
                .asSequence()
                .flatten()
                .distinctBy { it.id }
                .filter { effectiveTrackDateAdded(it, albumAddedByTitle) >= cutoffMs }
                .sortedByDescending { effectiveTrackDateAdded(it, albumAddedByTitle) }
                .toList()
            val albums = catalog.albums
                .filter { (it.dateAddedMs ?: Long.MIN_VALUE) >= cutoffMs }
                .sortedByDescending { it.dateAddedMs ?: 0L }
            val artists = catalog.artists
                .filter { artist -> recentlyAddedAt(artist, artistAddedByTitle) >= cutoffMs }
                .sortedByDescending { artist -> recentlyAddedAt(artist, artistAddedByTitle) }
            return when (kind) {
                RecentlyAddedKind.Songs -> RecentlyAddedPage("Songs", tracks = tracks)
                RecentlyAddedKind.Artists -> RecentlyAddedPage("Artists", artists = artists)
                RecentlyAddedKind.Albums -> RecentlyAddedPage("Albums", albums = albums)
            }
        }
    }
}
