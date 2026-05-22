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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.CollectionFacet
import com.phoebe.app.domain.CollectionTarget
import com.phoebe.app.domain.LibrarySortBy
import com.phoebe.app.data.splitCollectionTagLabels
import com.phoebe.app.platform.PhoebeLog

@Composable
internal fun CollectionsScreen(
    entry: CollectionEntry,
    catalog: CatalogSnapshot,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    supportedCollectionEntries: Set<CollectionEntry> = allCollectionEntries().toSet(),
    onBack: () -> Unit,
    onCollectionValue: (CollectionEntry, String) -> Unit,
) {
    val index = remember(catalog, supportedCollectionEntries) { CollectionIndex.from(catalog, supportedCollectionEntries) }
    val buckets = remember(index, entry) { index.bucketsFor(entry) }
    val loading = remember(catalog.collectionValues, catalog.collectionValueLoads, buckets, entry) {
        buckets.isEmpty() && !catalog.collectionValuesLoaded(entry)
    }
    var sortBy by rememberSaveable(entry.target.name, entry.facet.name) { mutableStateOf(LibrarySortBy.Name) }
    var ascending by rememberSaveable(entry.target.name, entry.facet.name) { mutableStateOf(true) }
    var viewMode by rememberSaveable(entry.target.name, entry.facet.name) { mutableStateOf(LibraryViewMode.Grid) }
    val visibleBuckets = remember(buckets, sortBy, ascending, searchQuery) {
        sortCollectionBuckets(
            filterCollectionBucketsByQuery(buckets, searchQuery),
            sortBy,
            ascending,
        )
    }
    PhoebeLog.d("PlexCollections") {
        val values = catalog.collectionValues.filter { it.target == entry.target.name && it.facet == entry.facet.name }
        val markers = catalog.collectionValueLoads.count { it.target == entry.target.name && it.facet == entry.facet.name }
        "screen values target=${entry.target.name} facet=${entry.facet.name} loading=$loading buckets=${buckets.size} nonEmpty=${buckets.count { it.items.isNotEmpty() }} values=${values.size} markers=$markers sample=${values.take(10).map { "${it.value}:${it.key}:${it.filterField}" }}"
    }
    val gridState = rememberSaveable(
        entry.target.name,
        entry.facet.name,
        saver = LazyGridState.Saver,
    ) { LazyGridState() }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CollectionsHeader(
            label = "COLLECTIONS",
            title = entry.title,
            subtitle = if (buckets.any { it.items.isNotEmpty() }) {
                "${buckets.sumOf { it.items.size }} ${entry.target.itemPlural.lowercase()} across ${buckets.size} ${entry.facet.plural.lowercase()}"
            } else if (loading) {
                "Loading ${entry.facet.plural.lowercase()}…"
            } else {
                "${buckets.size} ${entry.facet.plural.lowercase()}"
            },
            onBack = onBack,
        )
        DetailSectionHeader(
            title = entry.facet.plural,
            sortBy = sortBy,
            sortKeys = listOf(LibrarySortBy.Name, LibrarySortBy.DateAdded),
            sortLabel = { key -> if (key == LibrarySortBy.DateAdded) "Item count" else "${entry.facet.singular} name" },
            onSortBy = { sortBy = it },
            ascending = ascending,
            onAscending = { ascending = it },
            viewMode = viewMode,
            onViewMode = { viewMode = it },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            CollectionValuesGrid(
                entry = entry,
                buckets = visibleBuckets,
                loading = loading,
                searchQuery = searchQuery,
                compact = maxWidth < 700.dp,
                viewMode = viewMode,
                state = gridState,
                onCollectionValue = onCollectionValue,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun CollectionItemsScreen(
    entry: CollectionEntry,
    value: String,
    catalog: CatalogSnapshot,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    supportedCollectionEntries: Set<CollectionEntry> = allCollectionEntries().toSet(),
    onBack: () -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
) {
    val index = remember(catalog, supportedCollectionEntries) { CollectionIndex.from(catalog, supportedCollectionEntries) }
    val bucket = remember(index, entry, value) {
        index.bucketsFor(entry).firstOrNull { it.label.equals(value, ignoreCase = true) }
    }
    val items = bucket?.items.orEmpty()
    val collectionValue = remember(catalog.collectionValues, entry, value) {
        catalog.collectionValues.firstOrNull {
            it.target == entry.target.name &&
                it.facet == entry.facet.name &&
                it.value.equals(value, ignoreCase = true)
        }
    }
    val loading = items.isEmpty() && collectionValue?.itemsLoaded != true
    var sortBy by rememberSaveable(entry.target.name, entry.facet.name, value) {
        mutableStateOf(if (entry.target == CollectionTarget.Albums) LibrarySortBy.Year else LibrarySortBy.Name)
    }
    var ascending by rememberSaveable(entry.target.name, entry.facet.name, value) { mutableStateOf(true) }
    var viewMode by rememberSaveable(entry.target.name, entry.facet.name, value) { mutableStateOf(LibraryViewMode.Grid) }
    val visibleItems = remember(items, entry, sortBy, ascending, searchQuery) {
        sortCollectionItems(
            filterCollectionItemsByQuery(items, searchQuery),
            entry.target,
            sortBy,
            ascending,
        )
    }
    val gridState = rememberSaveable(
        entry.target.name,
        entry.facet.name,
        value,
        saver = LazyGridState.Saver,
    ) { LazyGridState() }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CollectionsHeader(
            label = entry.title,
            title = value,
            subtitle = if (loading) {
                "Loading ${entry.target.itemPlural.lowercase()}…"
            } else {
                "${items.size} ${if (items.size == 1) entry.target.itemSingular.lowercase() else entry.target.itemPlural.lowercase()}"
            },
            onBack = onBack,
        )
        DetailSectionHeader(
            title = entry.target.itemPlural,
            sortBy = sortBy,
            sortKeys = collectionItemSortKeys(entry.target),
            sortLabel = { key -> collectionItemSortLabel(entry.target, key) },
            onSortBy = { sortBy = it },
            ascending = ascending,
            onAscending = { ascending = it },
            viewMode = viewMode,
            onViewMode = { viewMode = it },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            CollectionItemsGrid(
                entry = entry,
                items = visibleItems,
                compact = maxWidth < 700.dp,
                loading = loading,
                searchQuery = searchQuery,
                viewMode = viewMode,
                state = gridState,
                onArtist = onArtist,
                onAlbum = onAlbum,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CollectionsHeader(
    label: String,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DetailBackButton(onBack = onBack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                label.uppercase(),
                color = PhoebeUi.mutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                title,
                color = PhoebeUi.primaryText,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = PhoebeUi.secondaryText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CollectionValuesGrid(
    entry: CollectionEntry,
    buckets: List<CollectionBucket>,
    loading: Boolean,
    searchQuery: String,
    compact: Boolean,
    viewMode: LibraryViewMode,
    state: LazyGridState,
    onCollectionValue: (CollectionEntry, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (loading) {
        RecentlyAddedEmpty("Loading ${entry.facet.plural.lowercase()}…", modifier)
        return
    }
    if (buckets.isEmpty()) {
        val query = searchQuery.trim()
        val message = if (query.isNotBlank()) {
            "No ${entry.facet.plural.lowercase()} match \"$query\"."
        } else {
            "No ${entry.facet.singular.lowercase()} tags are available for ${entry.target.itemPlural.lowercase()} yet."
        }
        RecentlyAddedEmpty(message, modifier)
        return
    }
    LazyVerticalGrid(
        columns = if (viewMode == LibraryViewMode.List) {
            GridCells.Fixed(1)
        } else {
            GridCells.Adaptive(if (compact) 148.dp else 184.dp)
        },
        state = state,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = buckets,
            key = { it.label },
            contentType = { "collection-value" },
        ) { bucket ->
            CollectionValueCard(entry, bucket) {
                onCollectionValue(entry, bucket.label)
            }
        }
    }
}

@Composable
private fun CollectionValueCard(entry: CollectionEntry, bucket: CollectionBucket, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CollectionIcon(entry, Modifier.size(38.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(bucket.label, color = PhoebeUi.primaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PhoebeIconView(PhoebeIcon.Forward, tint = PhoebeUi.secondaryText, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun CollectionItemsGrid(
    entry: CollectionEntry,
    items: List<CollectionItem>,
    compact: Boolean,
    loading: Boolean,
    searchQuery: String,
    viewMode: LibraryViewMode,
    state: LazyGridState,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (loading) {
        RecentlyAddedEmpty("Loading ${entry.target.itemPlural.lowercase()}…", modifier)
        return
    }
    if (items.isEmpty()) {
        val query = searchQuery.trim()
        val message = if (query.isNotBlank()) {
            "No ${entry.target.itemPlural.lowercase()} in this ${entry.facet.singular.lowercase()} match \"$query\"."
        } else {
            "Nothing is in this ${entry.facet.singular.lowercase()} yet."
        }
        RecentlyAddedEmpty(message, modifier)
        return
    }
    LazyVerticalGrid(
        columns = if (viewMode == LibraryViewMode.List) {
            GridCells.Fixed(1)
        } else {
            GridCells.Adaptive(if (compact) 132.dp else 158.dp)
        },
        state = state,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(
            key = "header",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "collection-items-header",
        ) {
            SectionLabel(entry.target.itemPlural, PhoebeUi.mutedText)
        }
        items(
            items = items,
            key = { it.id },
            contentType = { "collection-item" },
        ) { item ->
            when (entry.target) {
                CollectionTarget.Artists -> {
                    val artist = item.artist ?: return@items
                    if (viewMode == LibraryViewMode.List) {
                        LibraryRow(
                            title = artist.title,
                            subtitle = item.subtitle,
                            seed = artist.title,
                            thumbUrl = item.thumbUrl,
                            sharedKey = "artist:${artist.id}",
                            onClick = { onArtist(artist) },
                        )
                    } else {
                        CollectionMediaCard(
                            title = artist.title,
                            subtitle = item.subtitle,
                            thumbUrl = item.thumbUrl,
                            circular = true,
                            sharedKey = "artist:${artist.id}",
                            onClick = { onArtist(artist) },
                        )
                    }
                }
                CollectionTarget.Albums -> {
                    val album = item.album ?: return@items
                    if (viewMode == LibraryViewMode.List) {
                        LibraryRow(
                            title = album.title,
                            subtitle = item.subtitle,
                            seed = album.title,
                            thumbUrl = item.thumbUrl,
                            sharedKey = "album:${album.id}",
                            onClick = { onAlbum(album) },
                        )
                    } else {
                        CollectionMediaCard(
                            title = album.title,
                            subtitle = album.artist,
                            thumbUrl = album.thumbUrl,
                            circular = false,
                            sharedKey = "album:${album.id}",
                            onClick = { onAlbum(album) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionIcon(entry: CollectionEntry, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PhoebeUi.accentLight.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, PhoebeUi.accentLight.copy(alpha = 0.20f)), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val icon = when (entry.facet) {
            CollectionFacet.Mood -> PhoebeIcon.MoodFace
            CollectionFacet.Style -> PhoebeIcon.SunglassesFace
            CollectionFacet.Genre -> PhoebeIcon.GenreMasks
        }
        PhoebeIconView(icon, tint = PhoebeUi.accentLight, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun CollectionMediaCard(
    title: String,
    subtitle: String,
    thumbUrl: String?,
    circular: Boolean,
    sharedKey: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(PhoebeUi.elevatedFill)
            .border(BorderStroke(1.dp, PhoebeUi.border), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val artModifier = Modifier.fillMaxWidth().aspectRatio(1f).sharedArtworkTransition(sharedKey)
        if (circular) {
            ArtworkImage(title, thumbUrl, artModifier.clip(CircleShape), radius = 999.dp, elevated = false)
        } else {
            ArtworkImage(title, thumbUrl, artModifier, radius = 8.dp, elevated = false)
        }
        Text(
            title,
            color = PhoebeUi.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedBoundsTransition("$sharedKey:title"),
        )
        Text(
            subtitle,
            color = PhoebeUi.secondaryText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class CollectionIndex(
    private val bucketsByEntry: Map<CollectionEntry, List<CollectionBucket>>,
) {
    fun bucketsFor(entry: CollectionEntry): List<CollectionBucket> = bucketsByEntry[entry].orEmpty()

    companion object {
        fun from(
            catalog: CatalogSnapshot,
            supportedCollectionEntries: Set<CollectionEntry> = allCollectionEntries().toSet(),
        ): CollectionIndex {
            PhoebeLog.d("PlexCollections") {
                "index input collectionValues=${catalog.collectionValues.size} collectionTags=${catalog.collectionTags.size} artists=${catalog.artists.size} albums=${catalog.albums.size}"
            }
            val albumThumbByArtist = catalog.albums
                .asSequence()
                .filter { it.thumbUrl != null }
                .groupBy { it.artist.lowercase() }
                .mapValues { (_, albums) -> albums.firstNotNullOfOrNull { it.thumbUrl } }
            val artistGenre = CollectionEntry(CollectionTarget.Artists, CollectionFacet.Genre)
            val artistMood = CollectionEntry(CollectionTarget.Artists, CollectionFacet.Mood)
            val artistStyle = CollectionEntry(CollectionTarget.Artists, CollectionFacet.Style)
            val albumGenre = CollectionEntry(CollectionTarget.Albums, CollectionFacet.Genre)
            val albumMood = CollectionEntry(CollectionTarget.Albums, CollectionFacet.Mood)
            val albumStyle = CollectionEntry(CollectionTarget.Albums, CollectionFacet.Style)
            val bucketsByEntry = buildMap {
                if (artistGenre in supportedCollectionEntries) put(artistGenre, catalog.artistItems(artistGenre, albumThumbByArtist).toBuckets(catalog.collectionValueLabels(artistGenre)))
                if (albumGenre in supportedCollectionEntries) put(albumGenre, catalog.albumItems(albumGenre).toBuckets(catalog.collectionValueLabels(albumGenre)))
                if (artistMood in supportedCollectionEntries) put(artistMood, catalog.artistItems(artistMood, albumThumbByArtist).toBuckets(catalog.collectionValueLabels(artistMood)))
                if (albumMood in supportedCollectionEntries) put(albumMood, catalog.albumItems(albumMood).toBuckets(catalog.collectionValueLabels(albumMood)))
                if (artistStyle in supportedCollectionEntries) put(artistStyle, catalog.artistItems(artistStyle, albumThumbByArtist).toBuckets(catalog.collectionValueLabels(artistStyle)))
                if (albumStyle in supportedCollectionEntries) put(albumStyle, catalog.albumItems(albumStyle).toBuckets(catalog.collectionValueLabels(albumStyle)))
            }
            PhoebeLog.d("PlexCollections") {
                "index buckets=${bucketsByEntry.mapValues { (_, buckets) -> buckets.sumOf { it.items.size } }}"
            }
            return CollectionIndex(bucketsByEntry = bucketsByEntry)
        }
    }
}

private fun allCollectionEntries(): List<CollectionEntry> =
    CollectionTarget.entries.flatMap { target ->
        CollectionFacet.entries.map { facet -> CollectionEntry(target, facet) }
    }

private data class CollectionBucket(
    val label: String,
    val items: List<CollectionItem>,
)

private data class CollectionItem(
    val id: String,
    val label: String,
    val subtitle: String,
    val thumbUrl: String?,
    val artist: Artist? = null,
    val album: Album? = null,
)

private val CollectionEntry.title: String
    get() = "${target.itemSingular} ${facet.singular}"

private val CollectionTarget.itemSingular: String
    get() = when (this) {
        CollectionTarget.Artists -> "Artist"
        CollectionTarget.Albums -> "Album"
    }

private val CollectionTarget.itemPlural: String
    get() = when (this) {
        CollectionTarget.Artists -> "Artists"
        CollectionTarget.Albums -> "Albums"
    }

private val CollectionFacet.singular: String
    get() = when (this) {
        CollectionFacet.Mood -> "Mood"
        CollectionFacet.Style -> "Style"
        CollectionFacet.Genre -> "Genre"
    }

private val CollectionFacet.plural: String
    get() = when (this) {
        CollectionFacet.Mood -> "Moods"
        CollectionFacet.Style -> "Styles"
        CollectionFacet.Genre -> "Genres"
    }

private fun List<CollectionItem>.toBuckets(valueLabels: List<String>): List<CollectionBucket> {
    val bucketsByLabel = groupBy { it.label }
        .map { (label, items) ->
            CollectionBucket(
                label = label,
                items = items.sortedBy { item ->
                    (item.artist?.title ?: item.album?.title).orEmpty().lowercase()
                },
            )
        }
        .associateBy { it.label.lowercase() }
    val unloadedBuckets = valueLabels
        .filterNot { it.lowercase() in bucketsByLabel }
        .map { label -> CollectionBucket(label = label, items = emptyList()) }
    return (bucketsByLabel.values + unloadedBuckets)
        .sortedWith(compareByDescending<CollectionBucket> { it.items.size }.thenBy { it.label.lowercase() })
}

private fun filterCollectionBucketsByQuery(buckets: List<CollectionBucket>, query: String): List<CollectionBucket> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return buckets
    return buckets.filter { it.label.contains(trimmed, ignoreCase = true) }
}

private fun filterCollectionItemsByQuery(items: List<CollectionItem>, query: String): List<CollectionItem> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return items
    return items.filter { item ->
        item.artist?.title?.contains(trimmed, ignoreCase = true) == true ||
            item.album?.title?.contains(trimmed, ignoreCase = true) == true ||
            item.album?.artist?.contains(trimmed, ignoreCase = true) == true ||
            item.subtitle.contains(trimmed, ignoreCase = true)
    }
}

private fun sortCollectionBuckets(
    buckets: List<CollectionBucket>,
    sortBy: LibrarySortBy,
    ascending: Boolean,
): List<CollectionBucket> =
    when (sortBy) {
        LibrarySortBy.DateAdded -> buckets.sortedWith(
            if (ascending) compareBy<CollectionBucket>({ it.items.size }, { it.label.lowercase() })
            else compareByDescending<CollectionBucket> { it.items.size }.thenBy { it.label.lowercase() },
        )
        else -> buckets.sortedWith(
            if (ascending) compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            else compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.label },
        )
    }

private fun collectionItemSortKeys(target: CollectionTarget): List<LibrarySortBy> =
    when (target) {
        CollectionTarget.Artists -> listOf(LibrarySortBy.Name, LibrarySortBy.DateAdded)
        CollectionTarget.Albums -> listOf(LibrarySortBy.Year, LibrarySortBy.Name, LibrarySortBy.Artist, LibrarySortBy.DateAdded)
    }

private fun collectionItemSortLabel(target: CollectionTarget, sortBy: LibrarySortBy): String =
    when (target) {
        CollectionTarget.Artists -> if (sortBy == LibrarySortBy.DateAdded) "Date added" else "Artist name"
        CollectionTarget.Albums -> when (sortBy) {
            LibrarySortBy.Artist -> "Artist"
            LibrarySortBy.Year -> "Release date"
            LibrarySortBy.DateAdded -> "Date added"
            else -> "Album name"
        }
    }

private fun sortCollectionItems(
    items: List<CollectionItem>,
    target: CollectionTarget,
    sortBy: LibrarySortBy,
    ascending: Boolean,
): List<CollectionItem> =
    when (target) {
        CollectionTarget.Artists -> {
            val artists = items.mapNotNull { it.artist }.associateBy { it.id }
            val sorted = sortArtistsForLibrary(
                CatalogSnapshot(artists = items.mapNotNull { it.artist }),
                sortBy,
                ascending,
            )
            sorted.mapNotNull { artist -> items.firstOrNull { it.artist?.id == artist.id && artists.containsKey(artist.id) } }
        }
        CollectionTarget.Albums -> {
            val sorted = sortAlbumsForLibrary(items.mapNotNull { it.album }, sortBy, ascending)
            sorted.mapNotNull { album -> items.firstOrNull { it.album?.id == album.id } }
        }
    }

private fun CatalogSnapshot.collectionValueLabels(entry: CollectionEntry): List<String> =
    collectionValues
        .asSequence()
        .filter { it.target == entry.target.name && it.facet == entry.facet.name }
        .mapNotNull { it.value.cleanCollectionLabel() }
        .distinct()
        .toList()

private fun CatalogSnapshot.collectionValuesLoaded(entry: CollectionEntry): Boolean =
    collectionValues.any { it.target == entry.target.name && it.facet == entry.facet.name }

private fun String.cleanCollectionLabel(): String? =
    trim()
        .takeIf { it.isNotBlank() }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun CatalogSnapshot.artistItems(
    entry: CollectionEntry,
    albumThumbByArtist: Map<String, String?>,
): List<CollectionItem> {
    val assigned = assignedTags(entry)
    val fallbackByArtistTitle = tagsByArtistTitle(entry.facet)
    return artists.flatMap { artist ->
        val labels = assigned[artist.id].orEmpty().ifEmpty {
            fallbackByArtistTitle[artist.title.trim().lowercase()].orEmpty()
        }.mapNotNull { it.cleanCollectionLabel() }.distinct()
        labels.map { label ->
            CollectionItem(
                id = "artist:${artist.id}:$label",
                label = label,
                subtitle = "${artist.albumCount} ${if (artist.albumCount == 1) "album" else "albums"}",
                thumbUrl = artist.thumbUrl ?: albumThumbByArtist[artist.title.lowercase()],
                artist = artist,
            )
        }
    }
}

private fun CatalogSnapshot.albumItems(entry: CollectionEntry): List<CollectionItem> {
    val assigned = assignedTags(entry)
    val fallbackByAlbumId = tagsByAlbumId(entry.facet)
    return albums.flatMap { album ->
        val labels = assigned[album.id].orEmpty().ifEmpty {
            fallbackByAlbumId[album.id].orEmpty()
        }.mapNotNull { it.cleanCollectionLabel() }.distinct()
        labels.map { label ->
            CollectionItem(
                id = "album:${album.id}:$label",
                label = label,
                subtitle = album.artist,
                thumbUrl = album.thumbUrl,
                album = album,
            )
        }
    }
}

private fun CatalogSnapshot.assignedTags(entry: CollectionEntry): Map<String, List<String>> =
    collectionTags
        .asSequence()
        .filter { it.target == entry.target.name && it.facet == entry.facet.name }
        .groupBy { it.itemId.toCanonicalCatalogId() }
        .mapValues { (_, tags) -> tags.map { it.value }.distinct() }

private fun String.toCanonicalCatalogId(): String =
    when {
        startsWith("plex:plex:") -> removePrefix("plex:")
        startsWith("plex:") -> this
        ":" in this -> this
        else -> "plex:$this"
    }

private fun CatalogSnapshot.tagsByArtistTitle(facet: CollectionFacet): Map<String, List<String>> {
    val tagsByKey = LinkedHashMap<String, LinkedHashSet<String>>()
    fun add(key: String, raw: String?) {
        if (key.isBlank()) return
        val bucket = tagsByKey.getOrPut(key) { LinkedHashSet() }
        splitCollectionTagLabels(raw).forEach { label ->
            label.cleanCollectionLabel()?.let(bucket::add)
        }
    }
    artists.forEach { artist ->
        add(artist.title.trim().lowercase(), artist.collectionTag(facet))
    }
    tracksByParent.values.asSequence()
        .flatten()
        .distinctBy { it.id }
        .forEach { track ->
            add(track.artist.trim().lowercase(), track.collectionTag(facet))
        }
    return tagsByKey.mapValues { (_, labels) -> labels.toList() }
}

private fun CatalogSnapshot.tagsByAlbumId(facet: CollectionFacet): Map<String, List<String>> {
    val tagsById = LinkedHashMap<String, LinkedHashSet<String>>()
    fun add(albumId: String, raw: String?) {
        if (albumId.isBlank()) return
        val bucket = tagsById.getOrPut(albumId) { LinkedHashSet() }
        splitCollectionTagLabels(raw).forEach { label ->
            label.cleanCollectionLabel()?.let(bucket::add)
        }
    }
    albums.forEach { album -> add(album.id, album.collectionTag(facet)) }
    tracksByParent.forEach { (albumId, tracks) ->
        tracks.asSequence()
            .distinctBy { it.id }
            .forEach { track -> add(albumId, track.collectionTag(facet)) }
    }
    return tagsById.mapValues { (_, labels) -> labels.toList() }
}

private fun Artist.collectionTag(facet: CollectionFacet): String? =
    when (facet) {
        CollectionFacet.Mood -> mood
        CollectionFacet.Style -> style
        CollectionFacet.Genre -> genre
    }?.trim()?.takeIf { it.isNotBlank() }

private fun Album.collectionTag(facet: CollectionFacet): String? =
    when (facet) {
        CollectionFacet.Mood -> mood
        CollectionFacet.Style -> style
        CollectionFacet.Genre -> genre
    }?.trim()?.takeIf { it.isNotBlank() }

private fun com.phoebe.app.domain.Track.collectionTag(facet: CollectionFacet): String? =
    when (facet) {
        CollectionFacet.Mood -> mood
        CollectionFacet.Style -> style
        CollectionFacet.Genre -> genre
    }?.trim()?.takeIf { it.isNotBlank() }
