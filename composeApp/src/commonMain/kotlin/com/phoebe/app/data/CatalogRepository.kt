package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogCollectionValue
import com.phoebe.app.domain.CatalogCollectionValueLoad
import com.phoebe.app.domain.CatalogCollectionTag
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CatalogSyncPhase
import com.phoebe.app.domain.CatalogSyncState
import com.phoebe.app.domain.CollectionEntry
import com.phoebe.app.domain.CollectionFacet
import com.phoebe.app.domain.CollectionTarget
import com.phoebe.app.domain.DownloadItem
import com.phoebe.app.domain.DownloadState
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.LIKED_SONGS_PLAYLIST_TITLE
import com.phoebe.app.domain.LOCAL_PLAYLIST_ID_PREFIX
import com.phoebe.app.domain.PENDING_LIKED_SONGS_PLAYLIST_ID
import com.phoebe.app.domain.PlexRadioStation
import com.phoebe.app.domain.PlexRadioStationCategory
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.domain.canAddToLocalPlaylist
import com.phoebe.app.domain.canAddToPlexPlaylist
import com.phoebe.app.domain.canTogglePlexLike
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.domain.isLikedSongsPlaylist
import com.phoebe.app.domain.isPlexLibraryTrack
import com.phoebe.app.domain.serverAuthToken
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.domain.supportsPlexRatings
import com.phoebe.app.platform.PlatformStorage
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.catalogTrackPrefetchParallelism
import com.phoebe.app.sources.CatalogMerge
import com.phoebe.app.sources.LocalFolderMusicSourcePlugin
import com.phoebe.app.sources.PlexCatalogBuilder
import com.phoebe.app.sources.SourceBuildContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlin.random.Random

data class DownloadBatchResult(
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
) {
    val skipped: Int
        get() = (total - completed - failed).coerceAtLeast(0)
}

data class RatingSyncResult(
    val savedLocally: Boolean,
    val plexAttempted: Boolean,
    val plexSynced: Boolean,
)

data class FavoriteSyncResult(
    val favorite: Boolean? = null,
    val plexAttempted: Boolean = false,
    val plexSynced: Boolean = false,
)

@Serializable
data class FavoritePlaylistsExport(
    val version: Int = 1,
    val playlists: List<FavoritePlaylistExportEntry> = emptyList(),
)

@Serializable
data class FavoritePlaylistExportEntry(
    val id: String,
    val title: String,
    val key: String? = null,
)

class CatalogRepository(
    private val plexClient: PlexClient,
    private val database: PhoebeDatabase,
    private val storage: PlatformStorage,
    private val httpClient: HttpClient,
    private val mediaSourcesRepository: MediaSourcesRepository,
) {
    private val json = PlexClient.PlexJson
    private val mutableCatalog = MutableStateFlow(CatalogSnapshot())
    private val refreshMutex = Mutex()
    private val catalogMergeMutex = Mutex()
    private val downloadMutex = Mutex()
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val catalog: StateFlow<CatalogSnapshot> = mutableCatalog

    private val mutableCatalogRefreshing = MutableStateFlow(false)
    val catalogRefreshing: StateFlow<Boolean> = mutableCatalogRefreshing
    private var catalogRefreshingDepth = 0

    private fun pushCatalogRefreshing() {
        catalogRefreshingDepth++
        mutableCatalogRefreshing.value = true
    }

    private fun popCatalogRefreshing() {
        catalogRefreshingDepth = (catalogRefreshingDepth - 1).coerceAtLeast(0)
        if (catalogRefreshingDepth == 0) {
            mutableCatalogRefreshing.value = false
        }
    }

    private suspend inline fun <T> withCatalogRefreshing(crossinline block: suspend () -> T): T {
        pushCatalogRefreshing()
        return try {
            block()
        } finally {
            popCatalogRefreshing()
        }
    }

    private val mutableCatalogSyncState = MutableStateFlow(CatalogSyncState())
    val catalogSyncState: StateFlow<CatalogSyncState> = mutableCatalogSyncState
    private val localFileMetadataCache = LocalFileMetadataCache(database)

    fun clearInMemoryCatalog() {
        mutableCatalog.value = CatalogSnapshot()
        mutableCatalogRefreshing.value = false
        catalogRefreshingDepth = 0
        mutableCatalogSyncState.value = CatalogSyncState()
    }

    /**
     * Cached canonical machine identifier per session token. Plex's `server://X/…` URIs
     * require the value reported by the server's own `/identity` endpoint, which can differ
     * from the `clientIdentifier` exposed by plex.tv resources (especially for relay servers).
     * We resolve it lazily on first playlist mutation and reuse it for the lifetime of the
     * token.
     */
    private val machineIdentifierMutex = Mutex()
    private var cachedMachineIdentifier: Pair<String, String>? = null

    private suspend fun resolveMachineIdentifier(server: PlexServer, token: String): String =
        machineIdentifierMutex.withLock {
            val cached = cachedMachineIdentifier
            if (cached != null && cached.first == token) return cached.second
            val resolved = runCatching { plexClient.machineIdentifier(server, token) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: server.id
            cachedMachineIdentifier = token to resolved
            resolved
        }

    suspend fun restoreCachedCatalog() {
        PhoebeLog.d("CatalogRepository") { "restoreCachedCatalog start" }
        val cachedShell = withContext(Dispatchers.Default) { readCatalogShellFromDatabase() }
        if (cachedShell.isNotEmpty()) {
            mutableCatalog.value = cachedShell
            val cachedTracks = withContext(Dispatchers.Default) { readTracksFromDatabase() }
            if (cachedTracks.isNotEmpty()) {
                val hydrated = mutableCatalog.value.copy(
                    tracksByParent = cachedTracks.tracksByParent,
                    downloads = cachedTracks.downloads,
                )
                mutableCatalog.value = removeMissingLocalArtworkReferences(hydrated)
            }
            PhoebeLog.d("CatalogRepository") {
                "restoreCachedCatalog from DB → ${mutableCatalog.value.albums.size} albums, " +
                    "${mutableCatalog.value.tracksByParent.values.sumOf { it.size }} tracks"
            }
            return
        }
        val legacy = storage.readText(LegacyCatalogFile) ?: run {
            PhoebeLog.d("CatalogRepository") { "restoreCachedCatalog: no cached catalog" }
            return
        }
        val parsed = runCatching {
            json.decodeFromString<CatalogSnapshot>(legacy)
        }.getOrNull() ?: run {
            PhoebeLog.d("CatalogRepository") { "restoreCachedCatalog: legacy file unreadable" }
            return
        }
        val repaired = removeMissingLocalArtworkReferences(parsed)
        withContext(Dispatchers.Default) { persist(repaired) }
        mutableCatalog.value = repaired
        storage.delete(LegacyCatalogFile)
        PhoebeLog.d("CatalogRepository") {
            "restoreCachedCatalog from legacy file → ${parsed.albums.size} albums"
        }
    }

    suspend fun refreshAggregated(session: PlexSession?) {
        PhoebeLog.d("CatalogRepository") {
            "refreshAggregated start → plex=${session?.selectedServer?.name ?: "none"}, " +
                "localFolders=${mediaSourcesRepository.state.value.localFolders.count { it.enabled }}"
        }
        var stalePlaylists: List<Playlist> = emptyList()
        var persistSnapshot = true
        var foregroundRefreshing = false
        val snapshot = refreshMutex.withLock {
            pushCatalogRefreshing()
            foregroundRefreshing = true
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.LoadingLibrary,
                message = if (mutableCatalog.value.isNotEmpty()) "Refreshing library…" else "Loading your library…",
                blocking = mutableCatalog.value.isNotEmpty().not(),
            )
            try {
                val ctx = SourceBuildContext(
                    session = session,
                    plexClient = plexClient,
                    httpClient = httpClient,
                    localFolders = mediaSourcesRepository.state.value.localFolders,
                    localFileMetadataCache = localFileMetadataCache,
                )
                val previous = mutableCatalog.value

                val server = session?.selectedServer
                val library = session?.selectedLibrary
                val token = session.serverAuthToken()
                val plexBuilder = PlexCatalogBuilder(plexClient, httpClient)

                val (plexRawMetadata, localRaw) = coroutineScope {
                    val localDeferred = async { LocalFolderMusicSourcePlugin.buildCatalog(ctx) }
                    if (server == null || library == null || token == null) {
                        CatalogSnapshot() to localDeferred.await()
                    } else {
                        val metadata = plexBuilder.buildMetadataCatalog(server, library, token)
                        PhoebeLog.d("PlexCollections") {
                            "refresh metadata skipped collection discovery"
                        }
                        if (metadata.isNotEmpty()) {
                            publishPlexMetadataPartial(
                                raw = metadata,
                                previous = previous,
                                session = session,
                                message = "Loaded Plex metadata…",
                            )
                            yield()
                        }
                        metadata to localDeferred.await()
                    }
                }

                val metadataMerged = CatalogMerge.merge(
                    CatalogSnapshot(),
                    CatalogMerge.withPrefix("plex", plexRawMetadata),
                    localRaw,
                )
                val reconciled = reconcileMergedSnapshot(
                    merged = metadataMerged,
                    previous = previous,
                    session = session,
                )
                stalePlaylists = reconciled.stalePlaylists
                mutableCatalog.value = reconciled.snapshot
                popCatalogRefreshing()
                foregroundRefreshing = false
                mutableCatalogSyncState.value = CatalogSyncState(
                    phase = CatalogSyncPhase.LoadingSongs,
                    message = "Loaded albums, indexing songs…",
                    loadedAlbums = metadataMerged.albums.size,
                    loadedTracks = reconciled.snapshot.tracksByParent.values.sumOf { it.size },
                    blocking = false,
                )
                yield()

                if (server != null && library != null && token != null) {
                    val indexed = runCatching {
                        indexPlexTrackPages(server, library, token)
                    }.onFailure { error ->
                        PhoebeLog.d("CatalogRepository") { "paged Plex track index failed: ${error.message}" }
                    }.getOrDefault(false)
                    if (!indexed && plexRawMetadata.albums.isNotEmpty()) {
                        plexBuilder.prefetchAlbumTracks(server, plexRawMetadata.albums.sortedByDescending { it.dateAddedMs ?: 0L }, token) { album, tracks ->
                            val parentId = "plex:${album.id}"
                            catalogMergeMutex.withLock {
                                val cur = mutableCatalog.value
                                val prefixedTracks = preserveTrackDateAdded(
                                    existing = cur.tracksByParent[parentId].orEmpty(),
                                    incoming = tracks.map { it.withPlexPrefix() },
                                )
                                mutableCatalog.value = cur.copy(
                                    tracksByParent = cur.tracksByParent + (parentId to prefixedTracks),
                                )
                                mutableCatalogSyncState.value = CatalogSyncState(
                                    phase = CatalogSyncPhase.LoadingSongs,
                                    message = "Loaded albums, fetching songs…",
                                    loadedAlbums = cur.albums.size,
                                    loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
                                    blocking = false,
                                )
                            }
                        }
                    }
                }

                mutableCatalogSyncState.value = CatalogSyncState(
                    phase = CatalogSyncPhase.FinishingArtwork,
                    message = "Finishing artwork…",
                    loadedAlbums = mutableCatalog.value.albums.size,
                    loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
                    blocking = false,
                )
                val finalSnapshot = plexBuilder.enrichWithTrackArtwork(mutableCatalog.value)
                    .copy(downloads = previous.downloads)
                persistSnapshot = finalSnapshot != previous || stalePlaylists.isNotEmpty()
                yield()
                mutableCatalog.value = finalSnapshot
                finalSnapshot
            } catch (error: Throwable) {
                mutableCatalogSyncState.value = CatalogSyncState(
                    phase = CatalogSyncPhase.Failed,
                    message = error.message ?: "Sync failed.",
                )
                if (foregroundRefreshing) {
                    popCatalogRefreshing()
                    foregroundRefreshing = false
                }
                throw error
            }
        }
        try {
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.Persisting,
                message = "Saving library…",
                loadedAlbums = mutableCatalog.value.albums.size,
                loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
                blocking = false,
            )
            if (persistSnapshot) {
                persistAsync(snapshot)
            }
            // Refetch each playlist that grew externally after the main catalog is visible. Each
            // refetch persists its own update, so the full-snapshot write above must happen first.
            for (playlist in stalePlaylists) {
                mutableCatalogSyncState.value = CatalogSyncState(
                    phase = CatalogSyncPhase.RefreshingPlaylists,
                    message = "Refreshing playlists…",
                    loadedAlbums = mutableCatalog.value.albums.size,
                    loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
                    blocking = false,
                )
                runCatching { refetchPlaylistTracksFromPlex(session, playlist) }
                    .onFailure { error ->
                        PhoebeLog.d("CatalogRepository") { "background refetch failed for '${playlist.title}': ${error.message}" }
                    }
            }
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.Complete,
                message = "Library refreshed.",
                loadedAlbums = mutableCatalog.value.albums.size,
                loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
            )
            PhoebeLog.d("CatalogRepository") {
                "refreshAggregated complete → ${mutableCatalog.value.albums.size} albums, " +
                    "${mutableCatalog.value.tracksByParent.values.sumOf { it.size }} tracks, persist=$persistSnapshot"
            }
        } catch (error: Throwable) {
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.Failed,
                message = error.message ?: "Sync failed.",
            )
            throw error
        } finally {
            if (foregroundRefreshing) {
                popCatalogRefreshing()
            }
        }
    }

    suspend fun ensureCollectionItems(session: PlexSession?, entry: CollectionEntry, value: String) {
        val server = session?.selectedServer ?: return
        val library = session.selectedLibrary ?: return
        val token = session.serverAuthToken() ?: return
        ensureCollectionValues(session, entry)
        val current = mutableCatalog.value
        val normalizedValue = value.trim()
        val matchingTags = current.collectionTags.filter {
            it.target == entry.target.name &&
                it.facet == entry.facet.name &&
                it.value.equals(normalizedValue, ignoreCase = true)
        }
        val existingTargetIds = current.collectionTargetIds(entry.target)
        val alreadyLoaded = matchingTags.any { it.itemId.toPlexCatalogId() in existingTargetIds }
        PhoebeLog.d("PlexCollections") {
            "lazy item state target=${entry.target.name} facet=${entry.facet.name} value='$normalizedValue' tags=${matchingTags.size} usable=$alreadyLoaded targetIds=${existingTargetIds.size} sample=${matchingTags.take(10).map { it.itemId }}"
        }
        if (alreadyLoaded) {
            markCollectionValueItemsLoaded(entry, normalizedValue)
            return
        }
        val collectionValue = current.collectionValues.firstOrNull {
            it.target == entry.target.name &&
                it.facet == entry.facet.name &&
                it.value.equals(normalizedValue, ignoreCase = true)
        } ?: return
        if (collectionValue.itemsLoaded) {
            markCollectionValueItemsLoading(entry, normalizedValue)
        }
        val target = entry.target.toPlexCollectionTarget()
        val facet = entry.facet.toPlexCollectionFacet()
        PhoebeLog.d("PlexCollections") {
            "lazy load target=${entry.target.name} facet=${entry.facet.name} value='${collectionValue.value}'"
        }
        val cachedChoice = PlexFilterChoice(
            key = collectionValue.key,
            title = collectionValue.value,
            fastKey = collectionValue.fastKey,
            filterField = collectionValue.filterField,
        )
        var resolvedChoice = cachedChoice
        var plexItemIds = plexClient.collectionFilterItems(
            server = server,
            library = library,
            target = target,
            facet = facet,
            choice = cachedChoice,
            token = token,
        )
        if (plexItemIds.isEmpty()) {
            val refreshedChoice = plexClient.collectionFilterChoices(
                server = server,
                library = library,
                target = target,
                facet = facet,
                token = token,
            ).firstOrNull { it.title.equals(collectionValue.value, ignoreCase = true) }
            if (refreshedChoice != null && refreshedChoice != cachedChoice) {
                PhoebeLog.d("PlexCollections") {
                    "lazy choice refreshed target=${entry.target.name} facet=${entry.facet.name} value='${collectionValue.value}' oldKey='${cachedChoice.key}' newKey='${refreshedChoice.key}' oldField='${cachedChoice.filterField}' newField='${refreshedChoice.filterField}'"
                }
                resolvedChoice = refreshedChoice
                plexItemIds = plexClient.collectionFilterItems(
                    server = server,
                    library = library,
                    target = target,
                    facet = facet,
                    choice = refreshedChoice,
                    token = token,
                )
            }
        }
        val itemIds = plexItemIds.ifEmpty {
            current.collectionItemIdsFromIndexedMetadata(entry, collectionValue.value)
        }
        val loadedTags = itemIds.map { itemId ->
            CatalogCollectionTag(
                target = entry.target.name,
                facet = entry.facet.name,
                itemId = itemId.toPlexCatalogId(),
                value = collectionValue.value,
            )
        }
        catalogMergeMutex.withLock {
            val latest = mutableCatalog.value
            val retained = latest.collectionTags.filterNot {
                it.target == entry.target.name &&
                    it.facet == entry.facet.name &&
                    it.value.equals(collectionValue.value, ignoreCase = true)
            }
            val values = latest.collectionValues.map {
                if (it.target == entry.target.name &&
                    it.facet == entry.facet.name &&
                    it.value.equals(collectionValue.value, ignoreCase = true)
                ) {
                    it.copy(
                        key = resolvedChoice.key,
                        fastKey = resolvedChoice.fastKey,
                        filterField = resolvedChoice.filterField,
                        itemsLoaded = true,
                    )
                } else {
                    it
                }
            }
            val updated = latest.copy(
                collectionValues = values,
                collectionTags = (retained + loadedTags).distinct(),
            )
            mutableCatalog.value = updated
            persistAsync(updated)
        }
        PhoebeLog.d("PlexCollections") {
            "lazy loaded target=${entry.target.name} facet=${entry.facet.name} value='${collectionValue.value}' plexItems=${plexItemIds.size} items=${loadedTags.size}"
        }
    }

    private fun CatalogSnapshot.collectionItemIdsFromIndexedMetadata(
        entry: CollectionEntry,
        value: String,
    ): List<String> {
        val normalized = value.trim()
        if (normalized.isBlank()) return emptyList()
        val ids = when (entry.target) {
            CollectionTarget.Albums -> {
                val directAlbumIds = albums.asSequence()
                    .filter { it.collectionLabel(entry.facet).matchesCollectionValue(normalized) }
                    .map { it.id }
                val trackAlbumIds = tracksByParent.asSequence()
                    .filter { (_, tracks) ->
                        tracks.any { it.collectionLabel(entry.facet).matchesCollectionValue(normalized) }
                    }
                    .map { (albumId, _) -> albumId }
                (directAlbumIds + trackAlbumIds).toList()
            }
            CollectionTarget.Artists -> {
                val directArtistIds = artists.asSequence()
                    .filter { it.collectionLabel(entry.facet).matchesCollectionValue(normalized) }
                    .map { it.id }
                val artistIdsByTitle = artists.associateBy { it.title.lowercase() }
                val trackArtistIds = tracksByParent.values.asSequence()
                    .flatten()
                    .filter { it.collectionLabel(entry.facet).matchesCollectionValue(normalized) }
                    .mapNotNull { artistIdsByTitle[it.artist.lowercase()]?.id }
                (directArtistIds + trackArtistIds).toList()
            }
        }
        val distinct = ids.map { it.removePrefix("plex:") }.filter { it.isNotBlank() }.distinct()
        PhoebeLog.d("PlexCollections") {
            "indexed fallback target=${entry.target.name} facet=${entry.facet.name} value='$value' items=${distinct.size}"
        }
        return distinct
    }

    private fun CatalogSnapshot.collectionTargetIds(target: CollectionTarget): Set<String> =
        when (target) {
            CollectionTarget.Albums -> albums.map { it.id }.toSet()
            CollectionTarget.Artists -> artists.map { it.id }.toSet()
        }

    private fun Album.collectionLabel(facet: CollectionFacet): String? = when (facet) {
        CollectionFacet.Genre -> genre
        CollectionFacet.Mood -> mood
        CollectionFacet.Style -> style
    }

    private fun Artist.collectionLabel(facet: CollectionFacet): String? = when (facet) {
        CollectionFacet.Genre -> genre
        CollectionFacet.Mood -> mood
        CollectionFacet.Style -> style
    }

    private fun Track.collectionLabel(facet: CollectionFacet): String? = when (facet) {
        CollectionFacet.Genre -> genre
        CollectionFacet.Mood -> mood
        CollectionFacet.Style -> style
    }

    private fun String.toPlexCatalogId(): String {
        val raw = removePrefix("plex:").removePrefix("plex:")
        return "plex:$raw"
    }

    private fun String?.matchesCollectionValue(value: String): Boolean =
        this?.trim()?.equals(value, ignoreCase = true) == true

    private suspend fun markCollectionValueItemsLoading(entry: CollectionEntry, value: String) {
        catalogMergeMutex.withLock {
            val latest = mutableCatalog.value
            val values = latest.collectionValues.map {
                if (it.target == entry.target.name &&
                    it.facet == entry.facet.name &&
                    it.value.equals(value, ignoreCase = true) &&
                    it.itemsLoaded
                ) {
                    it.copy(itemsLoaded = false)
                } else {
                    it
                }
            }
            if (values != latest.collectionValues) {
                mutableCatalog.value = latest.copy(collectionValues = values)
            }
        }
    }

    private suspend fun markCollectionValueItemsLoaded(entry: CollectionEntry, value: String) {
        catalogMergeMutex.withLock {
            val latest = mutableCatalog.value
            val values = latest.collectionValues.map {
                if (it.target == entry.target.name &&
                    it.facet == entry.facet.name &&
                    it.value.equals(value, ignoreCase = true) &&
                    !it.itemsLoaded
                ) {
                    it.copy(itemsLoaded = true)
                } else {
                    it
                }
            }
            if (values != latest.collectionValues) {
                val updated = latest.copy(collectionValues = values)
                mutableCatalog.value = updated
                persistAsync(updated)
            }
        }
    }

    suspend fun ensureCollectionValues(session: PlexSession?, entry: CollectionEntry) {
        val server = session?.selectedServer
        if (server == null) {
            PhoebeLog.d("PlexCollections") { "lazy values skipped target=${entry.target.name} facet=${entry.facet.name}: no selected server" }
            return
        }
        val library = session.selectedLibrary
        if (library == null) {
            PhoebeLog.d("PlexCollections") { "lazy values skipped target=${entry.target.name} facet=${entry.facet.name}: no selected library" }
            return
        }
        val token = session.serverAuthToken()
        if (token == null) {
            PhoebeLog.d("PlexCollections") { "lazy values skipped target=${entry.target.name} facet=${entry.facet.name}: no auth token" }
            return
        }
        val current = mutableCatalog.value
        val alreadyLoaded = current.collectionValues.any {
            it.target == entry.target.name && it.facet == entry.facet.name
        }
        PhoebeLog.d("PlexCollections") {
            val matchingValues = current.collectionValues.filter {
                it.target == entry.target.name && it.facet == entry.facet.name
            }
            val loadMarkers = current.collectionValueLoads.count {
                it.target == entry.target.name && it.facet == entry.facet.name
            }
            "lazy values state target=${entry.target.name} facet=${entry.facet.name} alreadyLoaded=$alreadyLoaded values=${matchingValues.size} loadMarkers=$loadMarkers sample=${matchingValues.take(10).map { "${it.value}:${it.key}:${it.filterField}" }}"
        }
        if (alreadyLoaded) return

        val target = entry.target.toPlexCollectionTarget()
        val facet = entry.facet.toPlexCollectionFacet()
        PhoebeLog.d("PlexCollections") {
            "lazy values target=${entry.target.name} facet=${entry.facet.name}"
        }
        val loadedValues = plexClient.collectionFilterChoices(
            server = server,
            library = library,
            target = target,
            facet = facet,
            token = token,
        ).map { choice ->
            CatalogCollectionValue(
                target = entry.target.name,
                facet = entry.facet.name,
                value = choice.title,
                key = choice.key,
                fastKey = choice.fastKey,
                filterField = choice.filterField,
                itemsLoaded = false,
            )
        }
        catalogMergeMutex.withLock {
            val latest = mutableCatalog.value
            val retained = latest.collectionValues.filterNot {
                it.target == entry.target.name && it.facet == entry.facet.name
            }
            val updated = latest.copy(
                collectionValues = (retained + loadedValues).distinct(),
                collectionValueLoads = (
                    latest.collectionValueLoads +
                        CatalogCollectionValueLoad(target = entry.target.name, facet = entry.facet.name)
                    ).distinct(),
            )
            mutableCatalog.value = updated
            persistAsync(updated)
        }
        PhoebeLog.d("PlexCollections") {
            "lazy values loaded target=${entry.target.name} facet=${entry.facet.name} count=${loadedValues.size}"
        }
    }

    private data class ReconciledSnapshot(
        val snapshot: CatalogSnapshot,
        val stalePlaylists: List<Playlist>,
    )

    private fun publishPlexMetadataPartial(
        raw: CatalogSnapshot,
        previous: CatalogSnapshot,
        session: PlexSession?,
        message: String,
    ) {
        val merged = CatalogMerge.merge(
            CatalogSnapshot(),
            CatalogMerge.withPrefix("plex", raw),
        )
        val reconciled = reconcileMergedSnapshot(
            merged = merged,
            previous = previous,
            session = session,
        )
        mutableCatalog.value = reconciled.snapshot
        mutableCatalogSyncState.value = CatalogSyncState(
            phase = CatalogSyncPhase.LoadingLibrary,
            message = message,
            loadedAlbums = reconciled.snapshot.albums.size,
            loadedTracks = reconciled.snapshot.tracksByParent.values.sumOf { it.size },
            blocking = false,
        )
    }

    private fun reconcileMergedSnapshot(
        merged: CatalogSnapshot,
        previous: CatalogSnapshot,
        session: PlexSession?,
    ): ReconciledSnapshot {
        // The Plex builder prefetches tracks after the first metadata publish. To avoid wiping
        // lazily-loaded entries that the user has accumulated, keep previous entries for any
        // parent that still exists and let newly-fetched data overlay them later.
        val knownParents =
            (merged.albums.asSequence().map { it.id } +
                merged.playlists.asSequence().map { it.id }).toSet()
        val localPlaylists = previous.playlists.filter { it.isLocalPlaylist() }
        val localPlaylistIds = localPlaylists.map { it.id }.toSet()
        val currentToken = session.serverAuthToken()
        val preservedTracks = previous.tracksByParent
            .filterKeys { it in knownParents || it in localPlaylistIds }
            .filterValues { tracks ->
                tracks.all { it.shouldPreserveAcrossPlexRefresh(currentToken) }
            }

        // If Plex reports a playlist count mismatch, keep stale tracks visible and refetch after
        // the main catalog is published so additions and removals update the detail panel in place.
        // Liked Songs is also fetched the first time it appears so global heart state has ids to
        // compare against.
        val staleForRefetch = mutableListOf<Playlist>()
        val reconciledPlaylists = merged.playlists.map { p ->
            val cached = preservedTracks[p.id]
            val cachedSize = cached?.size ?: 0
            when {
                p.trackCount > 0 && cached == null && p.thumbUrl.isNullOrBlank() -> {
                    staleForRefetch += p
                    p
                }
                cached != null && p.trackCount != cachedSize -> {
                    staleForRefetch += p
                    p
                }
                else -> p
            }
        }

        val reconciled = merged.copy(
            playlists = reconciledPlaylists + localPlaylists,
            tracksByParent = preservedTracks + merged.tracksByParent,
            collectionValues = previous.collectionValues,
            collectionValueLoads = previous.collectionValueLoads,
            collectionTags = previous.collectionTags,
            downloads = previous.downloads,
        )
        return ReconciledSnapshot(
            snapshot = preserveDateAdded(previous, reconciled),
            stalePlaylists = staleForRefetch,
        )
    }

    private fun preserveDateAdded(previous: CatalogSnapshot, next: CatalogSnapshot): CatalogSnapshot {
        val previousTracks = previous.tracksByParent.values.flatten().associateBy { it.id }
        val tracksByParent = next.tracksByParent.mapValues { (_, tracks) ->
            tracks.map { track ->
                val previousAdded = previousTracks[track.id]?.dateAddedMs
                if (previousAdded != null) track.copy(dateAddedMs = previousAdded) else track
            }
        }
        val allTracks = tracksByParent.values.flatten()
        val previousAlbums = previous.albums.associateBy { it.id }
        val albums = next.albums.map { album ->
            val added = album.dateAddedMs
                ?: previousAlbums[album.id]?.dateAddedMs
                ?: allTracks
                    .filter { it.album.equals(album.title, ignoreCase = true) && it.artist.equals(album.artist, ignoreCase = true) }
                    .mapNotNull { it.dateAddedMs }
                    .maxOrNull()
            album.copy(
                dateAddedMs = added,
                genre = album.genre ?: previousAlbums[album.id]?.genre,
                mood = album.mood ?: previousAlbums[album.id]?.mood,
                style = album.style ?: previousAlbums[album.id]?.style,
                rating = album.rating ?: previousAlbums[album.id]?.rating,
                favorite = album.favorite || previousAlbums[album.id]?.favorite == true,
            )
        }
        val previousArtists = previous.artists.associateBy { it.id }
        val artists = next.artists.map { artist ->
            val added = artist.dateAddedMs
                ?: previousArtists[artist.id]?.dateAddedMs
                ?: albums
                    .filter { it.artist.equals(artist.title, ignoreCase = true) }
                    .mapNotNull { it.dateAddedMs }
                    .maxOrNull()
            artist.copy(
                dateAddedMs = added,
                genre = artist.genre ?: previousArtists[artist.id]?.genre,
                mood = artist.mood ?: previousArtists[artist.id]?.mood,
                style = artist.style ?: previousArtists[artist.id]?.style,
                rating = artist.rating ?: previousArtists[artist.id]?.rating,
                favorite = if (artist.id.startsWith("plex:")) {
                    artist.favorite
                } else {
                    artist.favorite || previousArtists[artist.id]?.favorite == true
                },
            )
        }
        val previousPlaylists = previous.playlists.associateBy { it.id }
        val playlists = next.playlists.map { playlist ->
            playlist.copy(
                rating = playlist.rating ?: previousPlaylists[playlist.id]?.rating,
                favorite = playlist.favorite || previousPlaylists[playlist.id]?.favorite == true,
            )
        }
        return next.copy(artists = artists, albums = albums, playlists = playlists, tracksByParent = tracksByParent)
    }

    private fun preserveTrackDateAdded(existing: List<Track>, incoming: List<Track>): List<Track> {
        if (existing.isEmpty()) return incoming
        val existingById = existing.associateBy { it.id }
        return incoming.map { track ->
            val previous = existingById[track.id] ?: return@map track
            track.copy(
                dateAddedMs = previous.dateAddedMs ?: track.dateAddedMs,
                localUri = previous.localUri ?: track.localUri,
                localArtworkUri = previous.localArtworkUri ?: track.localArtworkUri,
                thumbUrl = track.thumbUrl ?: previous.thumbUrl?.takeUnless { it.isLocalArtworkUrl() },
                rating = track.rating ?: previous.rating,
            )
        }
    }

    private suspend fun removeMissingLocalArtworkReferences(snapshot: CatalogSnapshot): CatalogSnapshot {
        val checked = mutableMapOf<String, Boolean>()
        suspend fun available(url: String?): Boolean {
            if (url.isNullOrBlank() || !url.isLocalArtworkUrl()) return true
            checked[url]?.let { return it }
            val exists = storage.readUriBytes(url) != null
            checked[url] = exists
            return exists
        }

        suspend fun clean(url: String?): String? =
            if (available(url)) url else null

        val artists = snapshot.artists.map { artist ->
            artist.copy(thumbUrl = clean(artist.thumbUrl))
        }
        val albums = snapshot.albums.map { album ->
            album.copy(thumbUrl = clean(album.thumbUrl))
        }
        val playlists = snapshot.playlists.map { playlist ->
            playlist.copy(thumbUrl = clean(playlist.thumbUrl))
        }
        val tracksByParent = snapshot.tracksByParent.mapValues { (_, tracks) ->
            tracks.map { track ->
                track.copy(
                    thumbUrl = clean(track.thumbUrl),
                    localArtworkUri = clean(track.localArtworkUri),
                )
            }
        }
        return snapshot.copy(
            artists = artists,
            albums = albums,
            playlists = playlists,
            tracksByParent = tracksByParent,
        )
    }

    private suspend fun indexPlexTrackPages(
        server: PlexServer,
        library: MusicLibrary,
        token: String,
    ): Boolean {
        var offset = 0
        var pages = 0
        var indexedAny = false
        while (pages < MaxTrackIndexPages) {
            val page = plexClient.libraryTracksPage(
                server = server,
                library = library,
                token = token,
                start = offset,
                size = TrackIndexPageSize,
            )
            if (page.tracks.isEmpty()) break
            publishIndexedPlexTracks(page.tracks)
            indexedAny = true
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.LoadingSongs,
                message = "Loaded albums, indexing songs…",
                loadedAlbums = mutableCatalog.value.albums.size,
                loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
                blocking = false,
            )
            if (!page.hasMore) break
            offset = page.nextOffset
            pages++
            yield()
        }
        return indexedAny
    }

    private suspend fun publishIndexedPlexTracks(rawTracks: List<Track>) {
        val tracksByAlbum = rawTracks
            .map { it.withPlexPrefix() }
            .groupBy { track -> resolveIndexedTrackParentId(track, mutableCatalog.value) }
            .filterKeys { it != null }
            .mapKeys { it.key!! }
        if (tracksByAlbum.isEmpty()) return
        catalogMergeMutex.withLock {
            val cur = mutableCatalog.value
            var nextParents = cur.tracksByParent
            tracksByAlbum.forEach { (parentId, tracks) ->
                val existing = nextParents[parentId].orEmpty()
                val incoming = preserveTrackDateAdded(existing, tracks)
                nextParents = nextParents + (parentId to (existing + incoming).distinctBy { it.id })
            }
            mutableCatalog.value = cur.copy(tracksByParent = nextParents)
        }
    }

    private fun resolveIndexedTrackParentId(track: Track, snapshot: CatalogSnapshot): String? {
        track.parentAlbumId?.takeIf { it.isNotBlank() }?.let { raw ->
            return if (raw.startsWith("plex:")) raw else "plex:$raw"
        }
        val album = snapshot.albums.firstOrNull {
            it.title.equals(track.album, ignoreCase = true) &&
                it.artist.equals(track.artist, ignoreCase = true)
        } ?: snapshot.albums.firstOrNull {
            it.title.equals(track.album, ignoreCase = true)
        }
        return album?.id
    }

    /**
     * Always refetches a playlist's track list from Plex (ignoring any cached entry) and
     * publishes the result. Used by [refreshAggregated] to reconcile playlists that grew
     * externally, and by [tracksForPlaylist] when the cache is empty.
     */
    private suspend fun refetchPlaylistTracksFromPlex(
        session: PlexSession?,
        playlist: Playlist,
        showRefreshing: Boolean = true,
    ) {
        val fetch: suspend () -> Unit = fetch@{
            val rating = plexRatingKey(playlist.id) ?: return@fetch
            val server = session?.selectedServer ?: return@fetch
            val token = session.serverAuthToken() ?: return@fetch
            val tracks = plexClient.playlistTracks(server, playlist.copy(id = rating), token)
                .map { it.withPlexPrefix() }
                .let { preserveTrackDateAdded(mutableCatalog.value.tracksByParent[playlist.id].orEmpty(), it) }
            val next = mutableCatalog.value.copy(
                tracksByParent = mutableCatalog.value.tracksByParent + (playlist.id to tracks),
                playlists = mutableCatalog.value.playlists.map { p ->
                        if (p.id == playlist.id) {
                            p.copy(
                                trackCount = tracks.size,
                                thumbUrl = p.thumbUrl ?: tracks.firstNotNullOfOrNull { it.thumbUrl },
                            )
                        } else p
                    },
            )
            publish(next, persist = false)
            persistPlaylistTracksAsync(next, playlist.id)
        }
        if (showRefreshing) {
            withCatalogRefreshing { fetch() }
        } else {
            fetch()
        }
    }

    suspend fun warmPlaylistTracks(session: PlexSession?) {
        val playlists = mutableCatalog.value.playlists
            .filter { playlist ->
                playlist.id.startsWith("plex:") &&
                    playlist.trackCount > 0 &&
                    mutableCatalog.value.tracksByParent[playlist.id].isNullOrEmpty()
            }
        if (playlists.isEmpty()) return
        PhoebeLog.d("CatalogRepository") { "warming ${playlists.size} playlist track lists" }
        for (playlist in playlists) {
            runCatching {
                refetchPlaylistTracksFromPlex(session, playlist, showRefreshing = false)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                PhoebeLog.d("CatalogRepository") {
                    "playlist warm failed for '${playlist.title}': ${error.message}"
                }
            }
            yield()
        }
    }

    suspend fun tracksForAlbum(session: PlexSession?, album: Album): List<Track> {
        val existing = mutableCatalog.value.tracksByParent[album.id]
        if (!existing.isNullOrEmpty()) return existing
        val rating = plexRatingKey(album.id) ?: return mutableCatalog.value.tracksByParent[album.id].orEmpty()
        val server = session?.selectedServer ?: return emptyList()
        session.selectedLibrary ?: return emptyList()
        return withCatalogRefreshing {
            val tracks = plexClient.children(server, rating, session.serverAuthToken()!!)
                .map { it.withPlexPrefix() }
                .let { preserveTrackDateAdded(mutableCatalog.value.tracksByParent[album.id].orEmpty(), it) }
            publish(
                mutableCatalog.value.copy(
                    tracksByParent = mutableCatalog.value.tracksByParent + (album.id to tracks),
                ),
                persist = true,
            )
            mutableCatalog.value.tracksByParent[album.id].orEmpty()
        }
    }

    /**
     * Fetches track listings from Plex for every album by this artist that is not already loaded.
     * Called when opening the artist detail screen.
     */
    suspend fun ensureTracksForArtistAlbums(session: PlexSession?, artistTitle: String) {
        val server = session?.selectedServer ?: return
        val token = session.serverAuthToken() ?: return
        val albums = catalogAlbumsForArtist(mutableCatalog.value, artistTitle)
            .filter { plexRatingKey(it.id) != null }
        val albumsToFetch = albums.filter { album ->
            mutableCatalog.value.tracksByParent[album.id].isNullOrEmpty()
        }
        if (albumsToFetch.isEmpty()) return

        withCatalogRefreshing {
            coroutineScope {
                albumsToFetch.map { album ->
                    async {
                        runCatching {
                            val rating = plexRatingKey(album.id) ?: return@runCatching
                            val snap = mutableCatalog.value
                            val existing = snap.tracksByParent[album.id]
                            if (!existing.isNullOrEmpty()) return@runCatching
                            val tracks = plexClient.children(server, rating, token)
                                .map { it.withPlexPrefix() }
                                .let { preserveTrackDateAdded(mutableCatalog.value.tracksByParent[album.id].orEmpty(), it) }
                            catalogMergeMutex.withLock {
                                val cur = mutableCatalog.value
                                publish(
                                    cur.copy(tracksByParent = cur.tracksByParent + (album.id to tracks)),
                                    persist = false,
                                )
                            }
                        }.onFailure { e ->
                            PhoebeLog.d("CatalogRepository") { "album track fetch failed for '${album.title}': ${e.message}" }
                        }
                    }
                }.awaitAll()
            }
            publish(mutableCatalog.value, persist = true)
        }
    }

    suspend fun warmRecentAlbumTracks(session: PlexSession?, cutoffMs: Long, maxAlbums: Int = 10) {
        val server = session?.selectedServer ?: return
        val token = session.serverAuthToken() ?: return
        val albumsToFetch = mutableCatalog.value.albums
            .asSequence()
            .filter { (it.dateAddedMs ?: Long.MIN_VALUE) >= cutoffMs }
            .filter { plexRatingKey(it.id) != null }
            .filter { mutableCatalog.value.tracksByParent[it.id].isNullOrEmpty() }
            .sortedByDescending { it.dateAddedMs ?: 0L }
            .take(maxAlbums)
            .toList()
        if (albumsToFetch.isEmpty()) return
        PhoebeLog.v("CatalogRepository") { "warmRecentAlbumTracks → ${albumsToFetch.size} albums" }

        coroutineScope {
            albumsToFetch
                .chunked(catalogTrackPrefetchParallelism().coerceAtLeast(1))
                .forEach { chunk ->
                    chunk.map { album ->
                        async {
                            runCatching {
                                val rating = plexRatingKey(album.id) ?: return@runCatching
                                val tracks = plexClient.children(server, rating, token)
                                    .map { track ->
                                        track.withPlexPrefix().let { prefixed ->
                                            if (prefixed.dateAddedMs == null && album.dateAddedMs != null) {
                                                prefixed.copy(dateAddedMs = album.dateAddedMs)
                                            } else {
                                                prefixed
                                            }
                                        }
                                    }
                                    .let { preserveTrackDateAdded(mutableCatalog.value.tracksByParent[album.id].orEmpty(), it) }
                                catalogMergeMutex.withLock {
                                    val cur = mutableCatalog.value
                                    if (cur.tracksByParent[album.id].isNullOrEmpty()) {
                                        publish(
                                            cur.copy(tracksByParent = cur.tracksByParent + (album.id to tracks)),
                                            persist = false,
                                        )
                                    }
                                }
                            }.onFailure { e ->
                                PhoebeLog.d("CatalogRepository") { "recent album warm failed for '${album.title}': ${e.message}" }
                            }
                        }
                    }.awaitAll()
                    yield()
                }
        }
        publish(mutableCatalog.value, persist = true)
    }

    suspend fun warmAlbumTracksByTitle(session: PlexSession?, albumTitles: List<String>, maxAlbums: Int = 10) {
        val server = session?.selectedServer ?: return
        val token = session.serverAuthToken() ?: return
        val titleOrder = albumTitles
            .mapIndexedNotNull { index, title ->
                title.trim().lowercase().takeIf { it.isNotBlank() }?.let { it to index }
            }
            .toMap()
        if (titleOrder.isEmpty()) return
        val albumsToFetch = mutableCatalog.value.albums
            .asSequence()
            .filter { it.title.trim().lowercase() in titleOrder }
            .filter { plexRatingKey(it.id) != null }
            .filter { mutableCatalog.value.tracksByParent[it.id].isNullOrEmpty() }
            .sortedBy { titleOrder[it.title.trim().lowercase()] ?: Int.MAX_VALUE }
            .take(maxAlbums)
            .toList()
        if (albumsToFetch.isEmpty()) return
        PhoebeLog.v("CatalogRepository") { "warmAlbumTracksByTitle → ${albumsToFetch.size} albums" }

        coroutineScope {
            albumsToFetch
                .chunked(catalogTrackPrefetchParallelism().coerceAtLeast(1))
                .forEach { chunk ->
                    chunk.map { album ->
                        async {
                            runCatching {
                                val rating = plexRatingKey(album.id) ?: return@runCatching
                                val tracks = plexClient.children(server, rating, token)
                                    .map { track ->
                                        track.withPlexPrefix().let { prefixed ->
                                            if (prefixed.dateAddedMs == null && album.dateAddedMs != null) {
                                                prefixed.copy(dateAddedMs = album.dateAddedMs)
                                            } else {
                                                prefixed
                                            }
                                        }
                                    }
                                    .let { preserveTrackDateAdded(mutableCatalog.value.tracksByParent[album.id].orEmpty(), it) }
                                catalogMergeMutex.withLock {
                                    val cur = mutableCatalog.value
                                    if (cur.tracksByParent[album.id].isNullOrEmpty()) {
                                        publish(
                                            cur.copy(tracksByParent = cur.tracksByParent + (album.id to tracks)),
                                            persist = false,
                                        )
                                    }
                                }
                            }.onFailure { e ->
                                PhoebeLog.d("CatalogRepository") { "played album warm failed for '${album.title}': ${e.message}" }
                            }
                        }
                    }.awaitAll()
                    yield()
                }
        }
        publish(mutableCatalog.value, persist = true)
    }

    suspend fun warmPlexHistoryTracks(session: PlexSession?, maxEntries: Int = 200): Int {
        val server = session?.selectedServer ?: return 0
        val library = session.selectedLibrary ?: return 0
        val token = session.serverAuthToken() ?: return 0
        if (maxEntries <= 0) return 0

        val loadedTrackIds = mutableCatalog.value.tracksByParent.values
            .asSequence()
            .flatten()
            .map { it.id }
            .toSet()
        val entries = mutableListOf<PlexPlaybackHistoryEntry>()
        var start = 0
        while (entries.size < maxEntries) {
            val pageSize = (maxEntries - entries.size).coerceAtMost(PlexPlayHistorySyncer.PageSize)
            val page = plexClient.playbackHistoryPage(
                server = server,
                token = token,
                library = library,
                minViewedAtMs = null,
                start = start,
                size = pageSize,
            )
            entries += page.entries.filter { entry ->
                (entry.type == null || entry.type == "track") &&
                    (entry.librarySectionId == null || entry.librarySectionId == library.key)
            }
            val total = page.totalSize
            val next = page.offset + page.size
            if (page.size <= 0 || (total != null && next >= total)) break
            start = next
        }

        val missingRatingKeys = entries
            .asSequence()
            .map { it.ratingKey }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { "plex:$it" in loadedTrackIds || it in loadedTrackIds }
            .toList()
        if (missingRatingKeys.isEmpty()) return 0

        val fetched = coroutineScope {
            val tracks = mutableListOf<Track>()
            missingRatingKeys
                .chunked(catalogTrackPrefetchParallelism().coerceAtLeast(1))
                .forEach { chunk ->
                    tracks.addAll(chunk.map { ratingKey ->
                        async {
                            runCatching { plexClient.trackDetails(server, ratingKey, token) }
                                .onFailure { e ->
                                    PhoebeLog.d("CatalogRepository") {
                                        "history track warm failed for '$ratingKey': ${e.message}"
                                    }
                                }
                                .getOrNull()
                        }
                    }.awaitAll().filterNotNull())
                }
            tracks
        }
        if (fetched.isEmpty()) return 0

        publishIndexedPlexTracks(fetched)
        publish(mutableCatalog.value, persist = true)
        PhoebeLog.d("CatalogRepository") { "warmPlexHistoryTracks → ${fetched.size} tracks" }
        return fetched.size
    }

    suspend fun tracksForDecade(session: PlexSession?, decade: Int): List<Track> {
        val start = decade
        val end = decade + 9
        val server = session?.selectedServer
        val library = session?.selectedLibrary
        val token = session.serverAuthToken()
        if (server != null && library != null && token != null) {
            val directTracks = runCatching {
                val firstPage = plexClient.tracksForYearRangePage(
                    server = server,
                    library = library,
                    token = token,
                    startYear = start,
                    endYear = end,
                    start = 0,
                    size = DecadeTrackPageSize,
                    limit = DecadeTrackLimit,
                )
                val pages = mutableListOf(firstPage)
                var offset = firstPage.nextOffset
                var pageCount = 1
                while (firstPage.hasMore && pageCount < MaxDecadeTrackPages) {
                    val page = plexClient.tracksForYearRangePage(
                        server = server,
                        library = library,
                        token = token,
                        startYear = start,
                        endYear = end,
                        start = offset,
                        size = DecadeTrackPageSize,
                        limit = DecadeTrackLimit,
                    )
                    if (page.tracks.isEmpty()) break
                    pages += page
                    if (!page.hasMore) break
                    offset = page.nextOffset
                    pageCount++
                }
                pages
                    .flatMap { it.tracks }
                    .map { it.withPlexPrefix() }
                    .filter { it.year?.let { year -> year in start..end } == true }
            }.onFailure { e ->
                PhoebeLog.d("CatalogRepository") { "decade track search failed for ${decade}s: ${e.message}" }
            }.getOrDefault(emptyList())
            if (directTracks.isNotEmpty()) {
                publishIndexedPlexTracks(directTracks)
                val loadedTracks = mutableCatalog.value.tracksByParent.values
                    .asSequence()
                    .flatten()
                    .filter { it.year?.let { year -> year in start..end } == true }
                    .toList()
                return (directTracks + loadedTracks).distinctBy { it.id }
            }
        }
        val matchingPlexAlbums = mutableCatalog.value.albums
            .filter { album -> album.year?.let { it in start..end } == true && plexRatingKey(album.id) != null }
        if (server != null && token != null && matchingPlexAlbums.isNotEmpty()) {
            withCatalogRefreshing {
                val normalized = mutableCatalog.value
                val matchingById = matchingPlexAlbums.associateBy { it.id }
                val normalizedParents = normalized.tracksByParent.mapValues { (parentId, tracks) ->
                    val album = matchingById[parentId]
                    if (album?.year == null) {
                        tracks
                    } else {
                        tracks.map { track ->
                            if (track.year == null) track.copy(year = album.year) else track
                        }
                    }
                }
                if (normalizedParents != normalized.tracksByParent) {
                    publish(normalized.copy(tracksByParent = normalizedParents), persist = false)
                }
                coroutineScope {
                    val parallelism = maxOf(catalogTrackPrefetchParallelism(), 4)
                    matchingPlexAlbums
                        .filter { album -> mutableCatalog.value.tracksByParent[album.id].isNullOrEmpty() }
                        .chunked(parallelism)
                        .forEach { chunk ->
                            chunk.map { album ->
                                async {
                                    runCatching {
                                        val rating = plexRatingKey(album.id) ?: return@runCatching
                                        val rawTracks = withTimeoutOrNull(DecadeAlbumFetchTimeoutMs) {
                                            plexClient.children(server, rating, token)
                                        }
                                        if (rawTracks == null) {
                                            PhoebeLog.d("CatalogRepository") {
                                                "decade album fetch timed out for '${album.title}' after ${DecadeAlbumFetchTimeoutMs}ms"
                                            }
                                            return@runCatching
                                        }
                                        val tracks = rawTracks
                                            .map { track ->
                                                track.withPlexPrefix().let { prefixed ->
                                                    prefixed.copy(
                                                        year = prefixed.year ?: album.year,
                                                        dateAddedMs = prefixed.dateAddedMs ?: album.dateAddedMs,
                                                    )
                                                }
                                            }
                                            .let { preserveTrackDateAdded(mutableCatalog.value.tracksByParent[album.id].orEmpty(), it) }
                                        catalogMergeMutex.withLock {
                                            val cur = mutableCatalog.value
                                            if (cur.tracksByParent[album.id].isNullOrEmpty()) {
                                                publish(
                                                    cur.copy(tracksByParent = cur.tracksByParent + (album.id to tracks)),
                                                    persist = false,
                                                )
                                            }
                                        }
                                    }.onFailure { e ->
                                        PhoebeLog.d("CatalogRepository") { "decade album fetch failed for '${album.title}': ${e.message}" }
                                    }
                                }
                            }.awaitAll()
                            yield()
                        }
                }
                publish(mutableCatalog.value, persist = true)
            }
        }
        return mutableCatalog.value.tracksByParent.values
            .asSequence()
            .flatten()
            .distinctBy { it.id }
            .filter { it.year?.let { year -> year in start..end } == true }
            .toList()
    }

    suspend fun firstTracksForDecade(session: PlexSession?, decade: Int): List<Track> {
        val start = decade
        val end = decade + 9
        val cached = cachedTracksForDecade(start, end)
        if (cached.isNotEmpty()) return cached

        val server = session?.selectedServer
        val library = session?.selectedLibrary
        val token = session.serverAuthToken()
        if (server != null && library != null && token != null) {
            val directTracks = runCatching {
                plexClient.tracksForYearRangePage(
                    server = server,
                    library = library,
                    token = token,
                    startYear = start,
                    endYear = end,
                    start = 0,
                    size = DecadeFirstPageSize,
                    limit = DecadeTrackLimit,
                ).tracks
                    .map { it.withPlexPrefix() }
                    .filter { it.year?.let { year -> year in start..end } == true }
            }.onFailure { e ->
                PhoebeLog.d("CatalogRepository") { "first decade page failed for ${decade}s: ${e.message}" }
            }.getOrDefault(emptyList())
            if (directTracks.isNotEmpty()) {
                publishIndexedPlexTracks(directTracks)
                return directTracks
            }
        }
        return cachedTracksForDecade(start, end)
    }

    suspend fun plexRadioStations(session: PlexSession?): List<PlexRadioStation> {
        val server = session?.selectedServer ?: return emptyList()
        val library = session.selectedLibrary ?: return emptyList()
        val token = session.serverAuthToken() ?: return emptyList()
        val stations = plexClient.musicStations(server, library, token)
        return stations.ifEmpty { defaultPlexRadioStations(library) }
    }

    suspend fun playRadioStation(session: PlexSession?, station: PlexRadioStation): List<Track> {
        val server = session?.selectedServer ?: return emptyList()
        session.selectedLibrary ?: return emptyList()
        val token = session.serverAuthToken() ?: return emptyList()
        val machineId = resolveMachineIdentifier(server, token)
        return plexClient.createStationPlayQueue(server, token, machineId, station.key)
            .map { it.withPlexPrefix() }
            .also { tracks ->
                if (tracks.isNotEmpty()) publishIndexedPlexTracks(tracks)
            }
    }

    suspend fun playArtistRadio(session: PlexSession?, artist: Artist): List<Track> {
        val station = artistRadioStation(session, artist) ?: return emptyList()
        return playRadioStation(session, station)
    }

    suspend fun artistRadioStation(session: PlexSession?, artist: Artist): PlexRadioStation? {
        val ratingKey = plexRatingKey(artist.id) ?: return null
        val server = session?.selectedServer ?: return null
        session.selectedLibrary ?: return null
        val token = session.serverAuthToken() ?: return null
        val station = withTimeoutOrNull(ArtistStationLookupTimeoutMs) {
            plexClient.artistStation(server, ratingKey, token)
        }
        if (station == null) {
            PhoebeLog.d("CatalogRepository") { "artist radio station not found for '${artist.title}' ($ratingKey)" }
        }
        return station
    }

    private fun cachedTracksForDecade(start: Int, end: Int): List<Track> =
        mutableCatalog.value.tracksByParent.values
            .asSequence()
            .flatten()
            .distinctBy { it.id }
            .filter { it.year?.let { year -> year in start..end } == true }
            .toList()

    suspend fun tracksForPlaylist(session: PlexSession?, playlist: Playlist): List<Track> {
        if (playlist.isLocalPlaylist()) {
            return mutableCatalog.value.tracksByParent[playlist.id].orEmpty()
        }
        if (playlist.id == PENDING_LIKED_SONGS_PLAYLIST_ID) {
            return mutableCatalog.value.tracksByParent[playlist.id].orEmpty()
        }
        val snapshot = mutableCatalog.value
        val playlistMeta = snapshot.playlists.find { it.id == playlist.id } ?: playlist
        val existing = snapshot.tracksByParent[playlist.id]
        if (!existing.isNullOrEmpty()) {
            if (existing.size != playlistMeta.trackCount) {
                persistenceScope.launch {
                    runCatching { refetchPlaylistTracksFromPlex(session, playlistMeta) }
                        .onFailure { error ->
                            PhoebeLog.d("CatalogRepository") {
                                "background playlist refresh failed for '${playlistMeta.title}': ${error.message}"
                            }
                        }
                }
            }
            return existing
        }
        refetchPlaylistTracksFromPlex(session, playlistMeta)
        return mutableCatalog.value.tracksByParent[playlist.id].orEmpty()
    }

    suspend fun findOrCreateLikedSongsPlaylist(session: PlexSession?): Playlist? {
        val existing = mutableCatalog.value.playlists.firstOrNull {
            it.isLikedSongsPlaylist() && it.id != PENDING_LIKED_SONGS_PLAYLIST_ID
        }
        if (existing != null) return existing
        return createPlaylist(session, LIKED_SONGS_PLAYLIST_TITLE)
    }

    suspend fun ensureLocalLikedSongsPlaylist(): Playlist {
        mutableCatalog.value.playlists.firstOrNull { it.isLikedSongsPlaylist() }?.let { return it }
        val playlist = Playlist(
            id = PENDING_LIKED_SONGS_PLAYLIST_ID,
            title = LIKED_SONGS_PLAYLIST_TITLE,
            trackCount = 0,
        )
        publish(
            mutableCatalog.value.copy(playlists = listOf(playlist) + mutableCatalog.value.playlists),
            persist = true,
        )
        return playlist
    }

    fun isTrackLiked(trackId: String): Boolean {
        if (trackId.isBlank()) return false
        val liked = mutableCatalog.value.playlists.firstOrNull { it.isLikedSongsPlaylist() } ?: return false
        return mutableCatalog.value.tracksByParent[liked.id].orEmpty().any { it.hasSamePlexIdentity(trackId) }
    }

    suspend fun toggleLikedTrack(session: PlexSession?, track: Track): Boolean {
        return toggleLikedTrackRemote(session, track)
    }

    suspend fun toggleLikedTrackLocally(track: Track): Boolean {
        if (!track.canTogglePlexLike()) return false
        val playlist = ensureLocalLikedSongsPlaylist()
        val snapshot = mutableCatalog.value
        val existing = snapshot.tracksByParent[playlist.id].orEmpty()
        val isLiked = existing.any { it.hasSamePlexIdentity(track.id) }
        val updated = if (isLiked) {
            existing.filterNot { it.hasSamePlexIdentity(track.id) }
        } else {
            existing + track
        }
        publishLikedSongs(playlist, updated)
        return !isLiked
    }

    suspend fun syncLikedSongsPlaylist(session: PlexSession?): Boolean {
        if (session?.supportsPlexPlaylists() != true) return false
        val remotePlaylist = mutableCatalog.value.playlists.firstOrNull {
            it.isLikedSongsPlaylist() && it.id != PENDING_LIKED_SONGS_PLAYLIST_ID
        }
        val localPlaylistBeforeFetch = mutableCatalog.value.playlists.firstOrNull { it.isLikedSongsPlaylist() } ?: return false
        val desiredTracksBeforeFetch = mutableCatalog.value.tracksByParent[localPlaylistBeforeFetch.id]
        if (remotePlaylist == null) {
            val desiredTracks = desiredTracksBeforeFetch.orEmpty()
            if (desiredTracks.isEmpty()) return false
            return createPlaylist(session, LIKED_SONGS_PLAYLIST_TITLE, desiredTracks) != null
        }

        val remoteTracks = runCatching {
            refetchPlaylistTracksFromPlex(session, remotePlaylist)
            mutableCatalog.value.tracksByParent[remotePlaylist.id].orEmpty()
        }.getOrElse {
            mutableCatalog.value.tracksByParent[remotePlaylist.id].orEmpty()
        }
        val desiredTracks = desiredTracksBeforeFetch ?: remoteTracks
        val desiredIds = desiredTracks.mapNotNull { it.plexIdentityKey() }.toSet()
        val toAdd = desiredTracks.filter { desired ->
            val desiredKey = desired.plexIdentityKey()
            desiredKey != null && remoteTracks.none { it.plexIdentityKey() == desiredKey }
        }
        val toRemove = remoteTracks.filter { remote ->
            remote.plexIdentityKey() !in desiredIds && remote.playlistItemId != null
        }
        if (toAdd.isNotEmpty()) {
            addTracksToPlaylist(session, remotePlaylist, toAdd)
        }
        toRemove.forEach { track ->
            removeTrackFromPlexPlaylist(session, remotePlaylist, track)
        }
        val mergedDesiredTracks = desiredTracks.map { desired ->
            remoteTracks.firstOrNull { it.id == desired.id }?.let { remote ->
                desired.copy(playlistItemId = remote.playlistItemId ?: desired.playlistItemId)
            } ?: desired
        }
        publishLikedSongs(remotePlaylist, mergedDesiredTracks)
        return true
    }

    suspend fun syncLikedTrackChange(
        session: PlexSession?,
        track: Track,
        liked: Boolean,
    ): Boolean {
        if (session?.supportsPlexPlaylists() != true || !track.canTogglePlexLike()) return false
        val remotePlaylist = mutableCatalog.value.playlists.firstOrNull {
            it.isLikedSongsPlaylist() && it.id != PENDING_LIKED_SONGS_PLAYLIST_ID
        } ?: run {
            if (!liked) return false
            return createPlaylist(session, LIKED_SONGS_PLAYLIST_TITLE, listOf(track)) != null
        }
        return if (liked) {
            appendTracksToPlexPlaylistRemoteOnly(session, remotePlaylist, listOf(track))
        } else {
            val localLikedTrack = mutableCatalog.value.tracksByParent[remotePlaylist.id].orEmpty()
                .firstOrNull { it.hasSamePlexIdentity(track.id) }
            val removable = localLikedTrack?.takeIf { it.playlistItemId != null } ?: run {
                runCatching { refetchPlaylistTracksFromPlex(session, remotePlaylist) }
                mutableCatalog.value.tracksByParent[remotePlaylist.id].orEmpty()
                    .firstOrNull { it.hasSamePlexIdentity(track.id) && it.playlistItemId != null }
            }
            removable?.let { removeTrackFromPlexPlaylist(session, remotePlaylist, it) } ?: false
        }
    }

    private suspend fun appendTracksToPlexPlaylistRemoteOnly(
        session: PlexSession,
        playlist: Playlist,
        tracks: List<Track>,
    ): Boolean {
        val server = session.selectedServer ?: return false
        val token = session.serverAuthToken() ?: return false
        val playlistRating = plexRatingKey(playlist.id) ?: return false
        val ratingKeys = tracks.mapNotNull { plexRatingKey(it.id) }.distinct()
        if (ratingKeys.isEmpty()) return false
        val machineId = resolveMachineIdentifier(server, token)
        return runCatching {
            plexClient.addTracksToPlaylist(server, token, machineId, playlistRating, ratingKeys)
        }.onFailure { error ->
            PhoebeLog.d("CatalogRepository") { "Liked Songs delta add failed: ${error.message}" }
        }.isSuccess
    }

    private suspend fun publishLikedSongs(playlist: Playlist, tracks: List<Track>) {
        val snapshot = mutableCatalog.value
        val updatedPlaylist = playlist.copy(
            trackCount = tracks.size,
            thumbUrl = playlist.thumbUrl ?: tracks.firstNotNullOfOrNull { it.thumbUrl },
        )
        val nextPlaylists = listOf(updatedPlaylist) + snapshot.playlists.filterNot { it.isLikedSongsPlaylist() }
        val likedPlaylistIds = snapshot.playlists
            .filter { it.isLikedSongsPlaylist() }
            .map { it.id }
            .toSet() + PENDING_LIKED_SONGS_PLAYLIST_ID + playlist.id
        val nextTracks = snapshot.tracksByParent
            .filterKeys { parentId -> parentId !in likedPlaylistIds }
            .toMutableMap()
            .apply {
                put(playlist.id, tracks)
            }
        publish(
            snapshot.copy(playlists = nextPlaylists, tracksByParent = nextTracks),
            persist = true,
        )
    }

    suspend fun toggleLikedTrackRemote(session: PlexSession?, track: Track): Boolean {
        if (!track.canTogglePlexLike()) return false
        val playlist = findOrCreateLikedSongsPlaylist(session) ?: return false
        val fresh = runCatching {
            refetchPlaylistTracksFromPlex(session, playlist)
            mutableCatalog.value.tracksByParent[playlist.id].orEmpty()
        }.getOrElse {
            mutableCatalog.value.tracksByParent[playlist.id].orEmpty()
        }
        val existing = fresh.firstOrNull { it.hasSamePlexIdentity(track.id) }
        return if (existing == null) {
            addTracksToPlaylist(session, playlist, listOf(track))
            true
        } else {
            !removeTrackFromPlexPlaylist(session, playlist, existing)
        }
    }

    suspend fun copyPlexPlaylistIntoPlaylist(
        session: PlexSession?,
        source: Playlist,
        target: Playlist,
    ): Int {
        if (source.id == target.id) return 0
        if (!source.id.startsWith("plex:") || !target.id.startsWith("plex:")) return 0
        if (session?.supportsPlexPlaylists() != true) return 0
        val sourceTracks = tracksForPlaylist(session, source)
            .filter { it.canAddToPlexPlaylist() }
        if (sourceTracks.isEmpty()) return 0
        val before = tracksForPlaylist(session, target).map { it.id }.toSet()
        val toCopy = sourceTracks.filterNot { it.id in before }
        if (toCopy.isEmpty()) return 0
        addTracksToPlaylist(session, target, toCopy)
        return toCopy.size
    }

    private suspend fun removeTrackFromPlexPlaylist(
        session: PlexSession?,
        playlist: Playlist,
        track: Track,
    ): Boolean {
        if (session?.supportsPlexPlaylists() != true) return false
        val server = session.selectedServer ?: return false
        val token = session.serverAuthToken() ?: return false
        val playlistRating = plexRatingKey(playlist.id) ?: return false
        val itemId = track.playlistItemId ?: return false
        return runCatching {
            plexClient.removePlaylistItems(server, token, playlistRating, listOf(itemId))
        }.onFailure { error ->
            PhoebeLog.d("CatalogRepository") { "removeTrackFromPlexPlaylist failed for '${playlist.title}': ${error.message}" }
        }.onSuccess {
            val snapshot = mutableCatalog.value
            val existing = snapshot.tracksByParent[playlist.id].orEmpty()
            val updated = existing.filterNot { it.id == track.id }
            publish(
                snapshot.copy(
                    tracksByParent = snapshot.tracksByParent + (playlist.id to updated),
                    playlists = snapshot.playlists.map {
                        if (it.id == playlist.id) it.copy(trackCount = updated.size) else it
                    },
                ),
                persist = true,
            )
        }.isSuccess
    }

    /**
     * Create a new **Plex** playlist (requires signed-in Plex with server + music library selected).
     * The playlist appears on other Plex clients; [initialTracks] must be Plex library tracks only.
     *
     * Returns the created [Playlist] (with the same id used in the in-memory snapshot) or
     * `null` if Plex creation failed or the session is not ready.
     */
    suspend fun createPlaylist(
        session: PlexSession?,
        title: String,
        initialTracks: List<Track> = emptyList(),
    ): Playlist? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null
        val s = session ?: return null
        if (!s.supportsPlexPlaylists()) return null
        if (initialTracks.any { it.isLocalMediaPlayback() || !it.isPlexLibraryTrack() }) return null
        val server = s.selectedServer ?: return null
        val library = s.selectedLibrary ?: return null
        val token = s.serverAuthToken() ?: return null
        return createPlexPlaylist(server, library, token, cleanTitle, initialTracks)
    }

    /**
     * Create a playlist stored only in Phoebe. Only local audio files ([Track.isLocalMediaPlayback])
     * may be added as seeds.
     */
    suspend fun createLocalPlaylist(
        title: String,
        initialTracks: List<Track> = emptyList(),
    ): Playlist? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null
        if (initialTracks.any { !it.canAddToLocalPlaylist() }) return null
        val id = "$LOCAL_PLAYLIST_ID_PREFIX${(Random.nextLong() and Long.MAX_VALUE).toString(16)}"
        val playlist = Playlist(id = id, title = cleanTitle, trackCount = initialTracks.size)
        val snapshot = mutableCatalog.value
        publish(
            snapshot.copy(
                playlists = snapshot.playlists + playlist,
                tracksByParent = if (initialTracks.isEmpty()) {
                    snapshot.tracksByParent
                } else {
                    snapshot.tracksByParent + (id to initialTracks)
                },
            ),
            persist = true,
        )
        return playlist
    }

    private suspend fun createPlexPlaylist(
        server: PlexServer,
        library: MusicLibrary,
        token: String,
        title: String,
        initialTracks: List<Track>,
    ): Playlist? {
        // Plex only accepts its own (un-prefixed) rating keys; tracks not sourced from this
        // Plex server are silently dropped from the initial seed.
        val seedKeys = initialTracks.mapNotNull { plexRatingKey(it.id) }
        val machineId = resolveMachineIdentifier(server, token)
        val createdResult = runCatching {
            plexClient.createPlaylist(server, token, library, machineId, title, seedKeys)
        }
        val created = createdResult.getOrElse { error ->
            PhoebeLog.d("CatalogRepository") { "createPlexPlaylist '$title' failed: ${error.message}" }
            return null
        }
        val prefixedPlaylist = created.copy(
            id = "plex:${created.id}",
            trackCount = if (initialTracks.isEmpty()) 0 else created.trackCount,
        )
        val prefixedTracks = initialTracks.filter { plexRatingKey(it.id) != null }
        val snapshot = mutableCatalog.value
        val nextPlaylists = if (prefixedPlaylist.isLikedSongsPlaylist()) {
            listOf(prefixedPlaylist) + snapshot.playlists.filterNot { it.isLikedSongsPlaylist() }
        } else {
            snapshot.playlists.filterNot { it.id == prefixedPlaylist.id } + prefixedPlaylist
        }
        val nextTracks = if (prefixedTracks.isEmpty()) {
            if (prefixedPlaylist.isLikedSongsPlaylist()) {
                snapshot.tracksByParent - PENDING_LIKED_SONGS_PLAYLIST_ID
            } else {
                snapshot.tracksByParent
            }
        } else {
            (snapshot.tracksByParent - PENDING_LIKED_SONGS_PLAYLIST_ID) + (prefixedPlaylist.id to prefixedTracks)
        }
        publish(
            snapshot.copy(playlists = nextPlaylists, tracksByParent = nextTracks),
            persist = true,
        )
        return prefixedPlaylist
    }

    /**
     * Add [tracks] to an existing Plex playlist, de-duplicating against the tracks already on it.
     * Only [Playlist] rows with `plex:` ids are supported; only Plex-sourced tracks are appended.
     */
    suspend fun addTracksToPlaylist(
        session: PlexSession?,
        playlist: Playlist,
        tracks: List<Track>,
    ) {
        PhoebeLog.d("CatalogRepository") { "addTracksToPlaylist entry → playlist='${playlist.title}' (${playlist.id}), tracks=${tracks.map { it.id }}" }
        if (tracks.isEmpty()) return
        if (playlist.isLocalPlaylist()) {
            addTracksToLocalPlaylist(playlist, tracks)
            return
        }
        if (!playlist.id.startsWith("plex:")) {
            PhoebeLog.d("CatalogRepository") { "addTracksToPlaylist: ignoring non-Plex playlist ${playlist.id}" }
            return
        }
        if (session?.supportsPlexPlaylists() != true) {
            PhoebeLog.d("CatalogRepository") { "addTracksToPlaylist: Plex session not ready" }
            return
        }
        val s = session!!
        var snapshot = mutableCatalog.value
        val playlistMeta = snapshot.playlists.find { it.id == playlist.id } ?: playlist
        var existing = snapshot.tracksByParent[playlist.id].orEmpty()
        // Playlist rows only carry trackCount from Plex metadata until the user opens the
        // playlist (or we refetch). Without this, appending onto an empty cache would
        // replace the whole list locally with just the dragged track.
        if (existing.size < playlistMeta.trackCount) {
            runCatching { refetchPlaylistTracksFromPlex(s, playlistMeta) }
            snapshot = mutableCatalog.value
            existing = snapshot.tracksByParent[playlist.id].orEmpty()
        }

        val existingIds = existing.map { it.id }.toHashSet()
        val toAdd = tracks
            .filterNot { it.id in existingIds }
            .filter { !it.isLocalMediaPlayback() && it.isPlexLibraryTrack() && plexRatingKey(it.id) != null }
        if (toAdd.isEmpty()) {
            PhoebeLog.d("CatalogRepository") { "addTracksToPlaylist: nothing to add after Plex filters, skipping" }
            return
        }

        val server = s.selectedServer
        val token = s.serverAuthToken()
        val playlistRating = plexRatingKey(playlist.id)
        val ratingKeys = toAdd.mapNotNull { plexRatingKey(it.id) }
        PhoebeLog.d("CatalogRepository") { "plex branch: hasServer=${server != null}, hasToken=${token != null}, playlistRating=$playlistRating, ratingKeys=$ratingKeys" }
        if (server != null && token != null && playlistRating != null && ratingKeys.isNotEmpty()) {
            val machineId = resolveMachineIdentifier(server, token)
            PhoebeLog.d("CatalogRepository") { "resolved machineIdentifier='$machineId' (server.id was '${server.id}')" }
            runCatching {
                plexClient.addTracksToPlaylist(server, token, machineId, playlistRating, ratingKeys)
            }.onFailure { error ->
                PhoebeLog.d("CatalogRepository") { "addTracksToPlaylist failed for '${playlist.title}': ${error.message}" }
            }.onSuccess { result ->
                PhoebeLog.d("CatalogRepository") { "Plex sync OK for '${playlist.title}': leafCountAdded=$result" }
            }
        } else {
            PhoebeLog.d("CatalogRepository") { "skipping Plex sync — missing one of server/token/playlistRating/ratingKeys" }
        }

        val canUpdateTrackList = existing.isNotEmpty() || playlistMeta.trackCount == 0
        val newTrackCount = if (canUpdateTrackList) {
            existing.size + toAdd.size
        } else {
            playlistMeta.trackCount + toAdd.size
        }
        val updatedPlaylists = snapshot.playlists.map {
            if (it.id == playlist.id) it.copy(trackCount = newTrackCount) else it
        }
        val nextSnapshot = if (canUpdateTrackList) {
            snapshot.copy(
                tracksByParent = snapshot.tracksByParent + (playlist.id to (toAdd + existing)),
                playlists = updatedPlaylists,
            )
        } else {
            snapshot.copy(playlists = updatedPlaylists)
        }
        publish(nextSnapshot, persist = true)
    }

    private suspend fun addTracksToLocalPlaylist(playlist: Playlist, tracks: List<Track>) {
        val snapshot = mutableCatalog.value
        val existing = snapshot.tracksByParent[playlist.id].orEmpty()
        val existingIds = existing.map { it.id }.toHashSet()
        val toAdd = tracks
            .filterNot { it.id in existingIds }
            .filter { it.canAddToLocalPlaylist() }
        if (toAdd.isEmpty()) return
        val updated = toAdd + existing
        publish(
            snapshot.copy(
                playlists = snapshot.playlists.map {
                    if (it.id == playlist.id) it.copy(trackCount = updated.size) else it
                },
                tracksByParent = snapshot.tracksByParent + (playlist.id to updated),
            ),
            persist = true,
        )
    }

    suspend fun updateTrackMetadata(
        session: PlexSession?,
        update: TrackMetadataUpdate,
    ): MetadataUpdateResult {
        val snapshot = mutableCatalog.value
        val existing = snapshot.tracksByParent.values.asSequence().flatten().firstOrNull { it.id == update.trackId }
            ?: return MetadataUpdateResult(savedLocally = false, plexAttempted = false, plexSynced = false)
        val cleanUpdate = update.copy(
            title = update.title.trim().ifBlank { existing.title },
            artist = update.artist.trim().ifBlank { existing.artist },
            album = update.album.trim().ifBlank { existing.album },
            genre = update.genre?.trim()?.takeIf { it.isNotBlank() },
        )

        var plexAttempted = false
        var plexSynced = false
        val rating = plexRatingKey(cleanUpdate.trackId)
        val server = session?.selectedServer
        val library = session?.selectedLibrary
        val token = session?.token?.takeIf { it.isNotBlank() }
        val hasPlexEditableChanges = cleanUpdate.title != existing.title || cleanUpdate.artist != existing.artist
        if (hasPlexEditableChanges && rating != null && server != null && library != null && token != null) {
            plexAttempted = true
            plexSynced = runCatching {
                plexClient.editTrackMetadata(server, token, library, rating, existing, cleanUpdate)
            }.onFailure { error ->
                PhoebeLog.d("CatalogRepository") { "updateTrackMetadata Plex sync failed for '${existing.title}': ${error.message}" }
            }.isSuccess
        }

        val edited = existing.copy(
            title = cleanUpdate.title,
            artist = cleanUpdate.artist,
            album = cleanUpdate.album,
            year = cleanUpdate.year,
            genre = cleanUpdate.genre,
        )
        val updatedTracks = snapshot.tracksByParent.mapValues { (_, tracks) ->
            tracks.map { if (it.id == edited.id) edited else it }
        }
        publish(snapshot.copy(tracksByParent = updatedTracks), persist = true)
        return MetadataUpdateResult(savedLocally = true, plexAttempted = plexAttempted, plexSynced = plexSynced)
    }

    suspend fun rateTrack(session: PlexSession?, track: Track, rating: Float?): RatingSyncResult {
        val normalized = rating.normalizedRating()
        val snapshot = mutableCatalog.value
        var changed = false
        val updatedTracks = snapshot.tracksByParent.mapValues { (_, tracks) ->
            tracks.map { existing ->
                if (existing.hasSamePlexIdentity(track.id)) {
                    changed = true
                    existing.copy(rating = normalized)
                } else {
                    existing
                }
            }
        }
        if (!changed) return RatingSyncResult(savedLocally = false, plexAttempted = false, plexSynced = false)
        publish(snapshot.copy(tracksByParent = updatedTracks), persist = true)
        return syncPlexRating(session, track.id, normalized, "track '${track.title}'").copy(savedLocally = true)
    }

    suspend fun rateArtist(session: PlexSession?, artist: Artist, rating: Float?): RatingSyncResult {
        val normalized = rating.normalizedRating()
        val snapshot = mutableCatalog.value
        var changed = false
        val artists = snapshot.artists.map {
            if (it.id == artist.id) {
                changed = true
                it.copy(rating = normalized)
            } else {
                it
            }
        }
        if (!changed) return RatingSyncResult(savedLocally = false, plexAttempted = false, plexSynced = false)
        publish(snapshot.copy(artists = artists), persist = true)
        return syncPlexRating(session, artist.id, normalized, "artist '${artist.title}'").copy(savedLocally = true)
    }

    suspend fun rateAlbum(session: PlexSession?, album: Album, rating: Float?): RatingSyncResult {
        val normalized = rating.normalizedRating()
        val snapshot = mutableCatalog.value
        var changed = false
        val albums = snapshot.albums.map {
            if (it.id == album.id) {
                changed = true
                it.copy(rating = normalized)
            } else {
                it
            }
        }
        if (!changed) return RatingSyncResult(savedLocally = false, plexAttempted = false, plexSynced = false)
        publish(snapshot.copy(albums = albums), persist = true)
        return syncPlexRating(session, album.id, normalized, "album '${album.title}'").copy(savedLocally = true)
    }

    suspend fun ratePlaylist(session: PlexSession?, playlist: Playlist, rating: Float?): RatingSyncResult {
        val normalized = rating.normalizedRating()
        val snapshot = mutableCatalog.value
        var changed = false
        val playlists = snapshot.playlists.map {
            if (it.id == playlist.id) {
                changed = true
                it.copy(rating = normalized)
            } else {
                it
            }
        }
        if (!changed) return RatingSyncResult(savedLocally = false, plexAttempted = false, plexSynced = false)
        publish(snapshot.copy(playlists = playlists), persist = true)
        return syncPlexRating(session, playlist.id, normalized, "playlist '${playlist.title}'").copy(savedLocally = true)
    }

    suspend fun toggleFavoriteArtist(session: PlexSession?, artist: Artist): FavoriteSyncResult {
        val snapshot = mutableCatalog.value
        var nextFavorite: Boolean? = null
        val artists = snapshot.artists.map {
            if (it.id == artist.id) {
                nextFavorite = !it.favorite
                it.copy(favorite = nextFavorite!!)
            } else {
                it
            }
        }
        val favorite = nextFavorite ?: return FavoriteSyncResult()
        publish(snapshot.copy(artists = artists), persist = true)
        return syncPlexFavoriteCollection(
            session = session,
            id = artist.id,
            favorite = favorite,
            label = "artist '${artist.title}'",
            sync = { server, token, library, ratingKey ->
                plexClient.setFavoriteArtistCollection(server, token, library, ratingKey, favorite)
            },
        ).copy(favorite = favorite)
    }

    suspend fun toggleFavoriteAlbum(session: PlexSession?, album: Album): FavoriteSyncResult {
        val snapshot = mutableCatalog.value
        var nextFavorite: Boolean? = null
        val albums = snapshot.albums.map {
            if (it.id == album.id) {
                nextFavorite = !it.favorite
                it.copy(favorite = nextFavorite!!)
            } else {
                it
            }
        }
        val favorite = nextFavorite ?: return FavoriteSyncResult()
        publish(snapshot.copy(albums = albums), persist = true)
        return syncPlexFavoriteCollection(
            session = session,
            id = album.id,
            favorite = favorite,
            label = "album '${album.title}'",
            sync = { server, token, library, ratingKey ->
                plexClient.setFavoriteAlbumCollection(server, token, library, ratingKey, favorite)
            },
        ).copy(favorite = favorite)
    }

    suspend fun toggleFavoritePlaylist(playlist: Playlist): FavoriteSyncResult {
        val snapshot = mutableCatalog.value
        var nextFavorite: Boolean? = null
        val playlists = snapshot.playlists.map {
            if (it.id == playlist.id) {
                nextFavorite = !it.favorite
                it.copy(favorite = nextFavorite!!)
            } else {
                it
            }
        }
        val favorite = nextFavorite ?: return FavoriteSyncResult()
        publish(snapshot.copy(playlists = playlists), persist = true)
        return FavoriteSyncResult(favorite = favorite)
    }

    fun favoritePlaylistsExport(): FavoritePlaylistsExport =
        FavoritePlaylistsExport(
            playlists = mutableCatalog.value.playlists
                .filter { it.favorite }
                .sortedBy { it.title.lowercase() }
                .map { playlist ->
                    FavoritePlaylistExportEntry(
                        id = playlist.id,
                        title = playlist.title,
                        key = playlist.key,
                    )
                },
        )

    suspend fun importFavoritePlaylists(export: FavoritePlaylistsExport): Int {
        val entries = export.playlists
        if (entries.isEmpty()) return 0
        val ids = entries.map { it.id }.toSet()
        val keys = entries.mapNotNull { it.key?.takeIf { key -> key.isNotBlank() } }.toSet()
        val titles = entries.map { it.title.normalizedFavoritePlaylistTitle() }.toSet()
        val snapshot = mutableCatalog.value
        var matched = 0
        val playlists = snapshot.playlists.map { playlist ->
            val matches = playlist.id in ids ||
                playlist.key?.let { it in keys } == true ||
                playlist.title.normalizedFavoritePlaylistTitle() in titles
            if (matches) {
                matched++
                playlist.copy(favorite = true)
            } else {
                playlist
            }
        }
        if (matched > 0) {
            publish(snapshot.copy(playlists = playlists), persist = true)
        }
        return matched
    }

    private suspend fun syncPlexRating(
        session: PlexSession?,
        id: String,
        rating: Float?,
        label: String,
    ): RatingSyncResult {
        val ratingKey = plexRatingKey(id)
        val server = session?.selectedServer
        val token = session?.serverAuthToken()
        if (!session.supportsPlexRatings() || ratingKey == null || server == null || token == null) {
            return RatingSyncResult(savedLocally = false, plexAttempted = false, plexSynced = false)
        }
        val synced = runCatching {
            plexClient.rateItem(server, token, ratingKey, rating)
        }.onFailure { error ->
            PhoebeLog.d("CatalogRepository") { "rateItem Plex sync failed for $label: ${error.message}" }
        }.isSuccess
        return RatingSyncResult(savedLocally = false, plexAttempted = true, plexSynced = synced)
    }

    private suspend fun syncPlexFavoriteCollection(
        session: PlexSession?,
        id: String,
        favorite: Boolean,
        label: String,
        sync: suspend (PlexServer, String, MusicLibrary, String) -> Unit,
    ): FavoriteSyncResult {
        val ratingKey = plexRatingKey(id)
        val server = session?.selectedServer
        val library = session?.selectedLibrary
        val token = session?.serverAuthToken()
        if (ratingKey == null || server == null || library == null || token == null) {
            return FavoriteSyncResult(favorite = favorite, plexAttempted = false, plexSynced = false)
        }
        val synced = runCatching {
            sync(server, token, library, ratingKey)
        }.onFailure { error ->
            PhoebeLog.d("CatalogRepository") { "favorite collection Plex sync failed for $label: ${error.message}" }
        }.isSuccess
        return FavoriteSyncResult(favorite = favorite, plexAttempted = true, plexSynced = synced)
    }

    suspend fun download(track: Track): DownloadBatchResult =
        downloadTracks(listOf(track))

    suspend fun downloadAlbum(session: PlexSession?, album: Album): DownloadBatchResult {
        downloadArtworkForAlbum(album)
        return downloadTracks(tracksForAlbum(session, album))
    }

    suspend fun downloadArtist(session: PlexSession?, artist: Artist): DownloadBatchResult {
        ensureTracksForArtistAlbums(session, artist.title)
        downloadArtworkForArtist(artist)
        catalogAlbumsForArtist(mutableCatalog.value, artist.title).forEach { album ->
            downloadArtworkForAlbum(album)
        }
        return downloadTracks(catalogTracksForArtist(mutableCatalog.value, artist.title))
    }

    suspend fun downloadPlaylist(session: PlexSession?, playlist: Playlist): DownloadBatchResult {
        val tracks = tracksForPlaylist(session, playlist)
        val refreshedPlaylist = mutableCatalog.value.playlists.firstOrNull { it.id == playlist.id } ?: playlist
        downloadArtworkForPlaylist(refreshedPlaylist)
        return downloadTracks(tracks)
    }

    suspend fun deleteAllDownloads(): Int = downloadMutex.withLock {
        val snapshot = mutableCatalog.value
        if (snapshot.downloads.isEmpty()) return@withLock 0
        deleteDownloadsForTrackIdsLocked(snapshot.downloads.map { it.trackId }.toSet())
    }

    suspend fun deleteDownloadsForTracks(tracks: List<Track>): Int = downloadMutex.withLock {
        val trackIds = tracks.map { it.id }.toSet()
        if (trackIds.isEmpty()) return@withLock 0
        deleteDownloadsForTrackIdsLocked(trackIds)
    }

    private suspend fun deleteDownloadsForTrackIdsLocked(trackIds: Set<String>): Int {
        val snapshot = mutableCatalog.value
        if (snapshot.downloads.isEmpty()) return 0
        val itemsToDelete = snapshot.downloads.filter { it.trackId in trackIds }
        if (itemsToDelete.isEmpty()) return 0
        val downloadedRemoteIds = itemsToDelete
            .mapNotNull { item -> item.trackId.takeIf { !item.localUri.isNullOrBlank() } }
            .toSet()
        itemsToDelete.forEach { item ->
            if (!item.localUri.isNullOrBlank()) {
                runCatching { storage.deleteUri(item.localUri) }
                    .onFailure { error ->
                        PhoebeLog.d("CatalogRepository") { "download delete failed for '${item.title}': ${error.message}" }
                }
            }
        }
        val deletedIds = itemsToDelete.map { it.trackId }.toSet()
        snapshot.tracksByParent.values
            .asSequence()
            .flatten()
            .distinctBy { it.id }
            .filter { it.id in deletedIds }
            .mapNotNull { it.localArtworkUri }
            .distinct()
            .forEach { uri ->
                runCatching { storage.deleteUri(uri) }
                    .onFailure { error ->
                        PhoebeLog.d("CatalogRepository") { "artwork delete failed: ${error.message}" }
                    }
            }
        val updatedTracks = snapshot.tracksByParent.mapValues { (_, tracks) ->
            tracks.map { track ->
                if (track.id in downloadedRemoteIds) track.copy(localUri = null, localArtworkUri = null) else track
            }
        }
        publish(
            snapshot.copy(
                tracksByParent = updatedTracks,
                downloads = snapshot.downloads.filterNot { it.trackId in deletedIds },
            ),
            persist = true,
        )
        return itemsToDelete.size
    }

    suspend fun cacheDownloadedArtwork(): Int = downloadMutex.withLock {
        val snapshot = mutableCatalog.value
        val downloadedIds = snapshot.downloads
            .asSequence()
            .filter { it.state == DownloadState.Complete && !it.localUri.isNullOrBlank() }
            .map { it.trackId }
            .toSet()
        if (downloadedIds.isEmpty()) return@withLock 0

        val tracks = snapshot.tracksByParent.values
            .asSequence()
            .flatten()
            .distinctBy { it.id }
            .filter { it.id in downloadedIds && it.thumbUrl?.isRemoteArtworkUrl() == true && it.localArtworkUri == null }
            .toList()
        var cached = 0
        tracks.forEach { track ->
            val artworkUri = downloadArtworkForTrack(track) ?: return@forEach
            updateTrackArtworkInfo(track.id, artworkUri)
            cached++
        }
        val trackIds = tracks.map { it.id }.toSet()
        val playlists = snapshot.playlists
            .filter { playlist ->
                playlist.thumbUrl?.isRemoteArtworkUrl() == true &&
                    snapshot.tracksByParent[playlist.id].orEmpty().any { it.id in trackIds }
            }
        playlists.forEach { playlist ->
            downloadArtworkForPlaylist(playlist) ?: return@forEach
            cached++
        }
        cached
    }

    suspend fun downloadTracks(tracks: List<Track>): DownloadBatchResult = downloadMutex.withLock {
        val uniqueTracks = tracks.distinctBy { it.id }
        if (uniqueTracks.isEmpty()) return@withLock DownloadBatchResult()

        uniqueTracks.filter { it.localUri != null }.forEach { track ->
            val artworkUri = track.localArtworkUri ?: downloadArtworkForTrack(track)
            updateTrackOfflineInfo(
                offlineTrack = track.copy(localArtworkUri = artworkUri ?: track.localArtworkUri),
            )
            updateDownload(track, DownloadState.Complete, progress = 1f)
        }
        val downloadable = uniqueTracks.filter { it.localUri == null && it.downloadUrl.isNotBlank() }
        val failedBeforeStart = uniqueTracks.filter { it.localUri == null && it.downloadUrl.isBlank() }
        failedBeforeStart.forEach { track ->
            updateDownload(track, DownloadState.Failed, progress = 0f)
        }
        downloadable.forEach { track ->
            updateDownload(track, DownloadState.Queued, progress = 0f)
        }

        var completed = uniqueTracks.count { it.localUri != null }
        var failed = failedBeforeStart.size
        downloadable.forEach { track ->
            updateDownload(track, DownloadState.Downloading, progress = 0.05f)
            runCatching {
                val bytes = httpClient.get(track.downloadUrl).body<ByteArray>()
                val localUri = storage.writeBytes(downloadPathFor(track), bytes)
                val artworkUri = downloadArtworkForTrack(track)
                val offlineTrack = track.copy(localUri = localUri, localArtworkUri = artworkUri ?: track.localArtworkUri)
                updateTrackOfflineInfo(
                    offlineTrack = offlineTrack,
                )
                updateDownload(offlineTrack, DownloadState.Complete, progress = 1f)
            }.onSuccess {
                completed++
            }.onFailure { error ->
                if (error is CancellationException) throw error
                PhoebeLog.d("CatalogRepository") { "download failed for '${track.title}': ${error.message}" }
                failed++
                updateDownload(track, DownloadState.Failed, progress = 0f)
            }
        }
        DownloadBatchResult(total = uniqueTracks.size, completed = completed, failed = failed)
    }

    private suspend fun updateDownload(track: Track, state: DownloadState, progress: Float) {
        val item = DownloadItem(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            state = state,
            progress = progress,
            localUri = track.localUri,
        )
        val others = mutableCatalog.value.downloads.filterNot { it.trackId == track.id }
        publish(mutableCatalog.value.copy(downloads = others + item), persist = true)
    }

    private suspend fun downloadArtworkForTrack(track: Track): String? {
        val thumbUrl = track.thumbUrl?.takeIf { it.isNotBlank() && it.isRemoteArtworkUrl() } ?: return null
        return runCatching {
            val bytes = httpClient.get(thumbUrl).body<ByteArray>()
            storage.writeBytes(cachedArtworkPathForUrl(thumbUrl), bytes)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            PhoebeLog.d("CatalogRepository") { "artwork download failed for '${track.title}': ${error.message}" }
        }.getOrNull()
    }

    private suspend fun downloadArtworkForArtist(artist: Artist): String? =
        downloadArtworkForEntity(
            owner = "artist",
            id = artist.id,
            title = artist.title,
            thumbUrl = artist.thumbUrl,
        )

    private suspend fun downloadArtworkForAlbum(album: Album): String? =
        downloadArtworkForEntity(
            owner = "album",
            id = album.id,
            title = album.title,
            thumbUrl = album.thumbUrl,
        )

    private suspend fun downloadArtworkForPlaylist(playlist: Playlist): String? =
        downloadArtworkForEntity(
            owner = "playlist",
            id = playlist.id,
            title = playlist.title,
            thumbUrl = playlist.thumbUrl,
        )

    private suspend fun downloadArtworkForEntity(
        owner: String,
        id: String,
        title: String,
        thumbUrl: String?,
    ): String? {
        val remoteThumbUrl = thumbUrl?.takeIf { it.isNotBlank() && it.isRemoteArtworkUrl() } ?: return null
        return runCatching {
            val bytes = httpClient.get(remoteThumbUrl).body<ByteArray>()
            storage.writeBytes(cachedArtworkPathForUrl(remoteThumbUrl), bytes)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            PhoebeLog.d("CatalogRepository") { "$owner artwork download failed for '$title': ${error.message}" }
        }.getOrNull()
    }

    private suspend fun updateTrackOfflineInfo(offlineTrack: Track) {
        val snapshot = mutableCatalog.value
        val updatedTracks = snapshot.tracksByParent.mapValues { (_, tracks) ->
            tracks.map { track ->
                if (track.id == offlineTrack.id) {
                    offlineTrack
                } else {
                    track
                }
            }
        }
        publish(
            snapshot.copy(
                tracksByParent = updatedTracks,
            ),
            persist = true,
        )
    }

    private suspend fun updateTrackArtworkInfo(trackId: String, localArtworkUri: String) {
        val snapshot = mutableCatalog.value
        val updatedTracks = snapshot.tracksByParent.mapValues { (_, tracks) ->
            tracks.map { track ->
                if (track.id == trackId) {
                    track.copy(localArtworkUri = localArtworkUri)
                } else {
                    track
                }
            }
        }
        publish(snapshot.copy(tracksByParent = updatedTracks), persist = true)
    }

    private fun downloadPathFor(track: Track): String {
        val plexRelativePath = track.filepath
            ?.takeIf { it.isNotBlank() }
            ?.libraryRelativeDownloadPath(track)
        if (plexRelativePath != null) return "downloads/$plexRelativePath"

        val extension = track.filepath
            ?.substringAfterLast('/', "")
            ?.substringAfterLast('\\', "")
            ?.substringAfterLast('.', "")
            ?.takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }
            ?: track.downloadUrl.substringBefore('?')
                .substringAfterLast('/', "")
                .substringAfterLast('.', "")
                .takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }
            ?: track.audioCodec?.lowercase()?.takeIf { it.isNotBlank() }
            ?: "audio"
        return "downloads/${track.id.safePathSegment()}.$extension"
    }

    private fun String.isRemoteArtworkUrl(): Boolean =
        startsWith("http://") || startsWith("https://")

    private fun String.isLocalArtworkUrl(): Boolean =
        startsWith("file:") || startsWith("content:") || startsWith("web-storage://")

    private fun String.safePathSegment(): String =
        map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
            .joinToString("")
            .trim('-')
            .ifBlank { "track" }

    private fun String.libraryRelativeDownloadPath(track: Track): String? {
        val normalized = replace('\\', '/').substringBefore('?').trim()
        if (normalized.isBlank() || normalized.startsWith("file://")) return null
        val rawSegments = normalized
            .split('/')
            .filter { it.isNotBlank() }
            .map { it.trim() }
        if (rawSegments.isEmpty()) return null
        val fileName = rawSegments.last().safeFileName().takeIf { it.hasLikelyExtension() } ?: return null
        val folders = rawSegments.dropLast(1)
        val artistIndex = folders.indexOfFirst { it.samePathName(track.artist) }
        val albumIndex = folders.indexOfFirst { it.samePathName(track.album) }
        val startIndex = when {
            artistIndex >= 0 -> artistIndex
            albumIndex > 0 -> albumIndex - 1
            folders.size > 1 && rawSegments.first().looksLikeFilesystemRoot() -> 1
            else -> 0
        }
        val relativeFolders = folders.drop(startIndex).mapNotNull { segment ->
            segment.safeFileName().takeIf { it.isNotBlank() && it != "." && it != ".." }
        }
        return (relativeFolders + fileName).joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun String.safeFileName(): String =
        map { c ->
            when {
                c.isLetterOrDigit() || c == ' ' || c == '-' || c == '_' || c == '.' || c == '(' || c == ')' || c == '[' || c == ']' -> c
                else -> '-'
            }
        }
            .joinToString("")
            .trim(' ', '.', '-')
            .ifBlank { "untitled" }

    private fun String.samePathName(other: String): Boolean =
        safeFileName().lowercase() == other.safeFileName().lowercase()

    private fun String.hasLikelyExtension(): Boolean =
        substringAfterLast('.', "")
            .takeIf { it.length in 2..5 }
            ?.all { it.isLetterOrDigit() } == true

    private fun String.looksLikeFilesystemRoot(): Boolean {
        val value = trimEnd(':').lowercase()
        return value.length == 1 ||
            value in setOf("music", "media", "audio", "library", "libraries", "mnt", "mount", "volume", "volumes", "storage")
    }

    private suspend fun publish(snapshot: CatalogSnapshot, persist: Boolean) {
        mutableCatalog.value = snapshot
        if (persist) {
            persistAsync(snapshot)
        }
    }

    /** Persist the entire snapshot off the UI thread. */
    private suspend fun persistAsync(snapshot: CatalogSnapshot) = withContext(Dispatchers.Default) {
        persist(snapshot)
    }

    private fun persistPlaylistTracksAsync(snapshot: CatalogSnapshot, playlistId: String) {
        persistenceScope.launch {
            runCatching { persistPlaylistTracks(snapshot, playlistId) }
                .onFailure { error ->
                    PhoebeLog.d("CatalogRepository") {
                        "playlist track cache persist failed for $playlistId: ${error.message}"
                    }
                }
        }
    }

    private suspend fun persistPlaylistTracks(snapshot: CatalogSnapshot, playlistId: String) = withContext(Dispatchers.Default) {
        val playlist = snapshot.playlists.firstOrNull { it.id == playlistId } ?: return@withContext
        val tracks = snapshot.tracksByParent[playlistId].orEmpty()
        val sortKey = snapshot.playlists.indexOfFirst { it.id == playlistId }.takeIf { it >= 0 } ?: 0
        database.transaction {
            database.catalogQueries.upsertPlaylist(
                id = playlist.id,
                title = playlist.title,
                trackCount = playlist.trackCount.toLong(),
                plKey = playlist.key,
                thumbUrl = playlist.thumbUrl,
                sortKey = sortKey.toLong(),
                rating = playlist.rating?.toDouble(),
                favorite = playlist.favorite.toDb(),
            )
            tracks.forEach { track ->
                database.catalogQueries.upsertTrack(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    streamUrl = track.streamUrl,
                    downloadUrl = track.downloadUrl,
                    thumbUrl = track.thumbUrl,
                    localArtworkUri = track.localArtworkUri,
                    localUri = track.localUri,
                    year = track.year?.toLong(),
                    genre = track.genre,
                    mood = track.mood,
                    style = track.style,
                    filepath = track.filepath,
                    audioCodec = track.audioCodec,
                    bitrateKbps = track.bitrateKbps?.toLong(),
                    dateAddedMs = track.dateAddedMs,
                    rating = track.rating?.toDouble(),
                    parentAlbumId = track.parentAlbumId,
                )
            }
            database.catalogQueries.deleteTrackParents(playlistId)
            tracks.forEachIndexed { index, track ->
                database.catalogQueries.upsertTrackParent(
                    parentId = playlistId,
                    trackId = track.id,
                    position = index.toLong(),
                )
            }
        }
    }

    private suspend fun persist(snapshot: CatalogSnapshot) {
        database.transaction {
            database.catalogQueries.clearTrackParents()
            database.catalogQueries.clearTracks()
            database.catalogQueries.clearCollectionTags()
            database.catalogQueries.clearCollectionValues()
            database.catalogQueries.clearCollectionValueLoads()
            database.catalogQueries.clearArtists()
            database.catalogQueries.clearAlbums()
            database.catalogQueries.clearPlaylists()
            snapshot.artists.forEachIndexed { index, artist ->
                database.catalogQueries.upsertArtist(
                    id = artist.id,
                    title = artist.title,
                    thumbUrl = artist.thumbUrl,
                    albumCount = artist.albumCount.toLong(),
                    songCount = artist.songCount.toLong(),
                    sortKey = index.toLong(),
                    dateAddedMs = artist.dateAddedMs,
                    genre = artist.genre,
                    mood = artist.mood,
                    style = artist.style,
                    rating = artist.rating?.toDouble(),
                    favorite = artist.favorite.toDb(),
                )
            }
            snapshot.albums.forEachIndexed { index, album ->
                database.catalogQueries.upsertAlbum(
                    id = album.id,
                    title = album.title,
                    artist = album.artist,
                    year = album.year?.toLong(),
                    thumbUrl = album.thumbUrl,
                    sortKey = index.toLong(),
                    dateAddedMs = album.dateAddedMs,
                    genre = album.genre,
                    mood = album.mood,
                    style = album.style,
                    rating = album.rating?.toDouble(),
                    favorite = album.favorite.toDb(),
                )
            }
            snapshot.playlists.forEachIndexed { index, playlist ->
                database.catalogQueries.upsertPlaylist(
                    id = playlist.id,
                    title = playlist.title,
                    trackCount = playlist.trackCount.toLong(),
                    plKey = playlist.key,
                    thumbUrl = playlist.thumbUrl,
                    sortKey = index.toLong(),
                    rating = playlist.rating?.toDouble(),
                    favorite = playlist.favorite.toDb(),
                )
            }
            val uniqueTracks = snapshot.tracksByParent.values.flatten().distinctBy { it.id }
            uniqueTracks.forEach { track ->
                database.catalogQueries.upsertTrack(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    streamUrl = track.streamUrl,
                    downloadUrl = track.downloadUrl,
                    thumbUrl = track.thumbUrl,
                    localArtworkUri = track.localArtworkUri,
                    localUri = track.localUri,
                    year = track.year?.toLong(),
                    genre = track.genre,
                    mood = track.mood,
                    style = track.style,
                    filepath = track.filepath,
                    audioCodec = track.audioCodec,
                    bitrateKbps = track.bitrateKbps?.toLong(),
                    dateAddedMs = track.dateAddedMs,
                    rating = track.rating?.toDouble(),
                    parentAlbumId = track.parentAlbumId,
                )
            }
            snapshot.tracksByParent.forEach { (parentId, tracks) ->
                tracks.forEachIndexed { index, track ->
                    database.catalogQueries.upsertTrackParent(
                        parentId = parentId,
                        trackId = track.id,
                        position = index.toLong(),
                    )
                }
            }
            snapshot.collectionTags.forEach { tag ->
                database.catalogQueries.upsertCollectionTag(
                    target = tag.target,
                    facet = tag.facet,
                    itemId = tag.itemId,
                    value_ = tag.value,
                )
            }
            snapshot.collectionValues.forEach { value ->
                database.catalogQueries.upsertCollectionValue(
                    target = value.target,
                    facet = value.facet,
                    value_ = value.value,
                    key = value.key,
                    fastKey = value.fastKey,
                    filterField = value.filterField,
                    itemsLoaded = if (value.itemsLoaded) 1L else 0L,
                )
            }
            snapshot.collectionValueLoads.forEach { load ->
                database.catalogQueries.upsertCollectionValueLoad(
                    target = load.target,
                    facet = load.facet,
                )
            }
            PhoebeLog.d("PlexCollections") {
                "persist collectionValues count=${snapshot.collectionValues.size} collectionTags count=${snapshot.collectionTags.size} tagsByEntry=${snapshot.collectionTags.groupingBy { "${it.target}/${it.facet}" }.eachCount()}"
            }
        }
        yield()
        database.transaction {
            database.downloadsQueries.clearAll()
            snapshot.downloads.forEach { item ->
                database.downloadsQueries.upsert(
                    trackId = item.trackId,
                    title = item.title,
                    artist = item.artist,
                    dlState = item.state.name,
                    progress = item.progress.toDouble(),
                    localUri = item.localUri,
                )
            }
        }
    }

    private suspend fun readFromDatabase(): CatalogSnapshot {
        val shell = readCatalogShellFromDatabase()
        val tracks = readTracksFromDatabase()
        return shell.copy(
            tracksByParent = tracks.tracksByParent,
            downloads = tracks.downloads,
        )
    }

    private suspend fun readCatalogShellFromDatabase(): CatalogSnapshot {
        val artists = database.catalogQueries.selectArtists().awaitAsList().map {
            Artist(
                id = it.id,
                title = it.title,
                thumbUrl = it.thumbUrl,
                albumCount = it.albumCount.toInt(),
                songCount = it.songCount.toInt(),
                dateAddedMs = it.dateAddedMs,
                genre = it.genre,
                mood = it.mood,
                style = it.style,
                rating = it.rating?.toFloat(),
                favorite = it.favorite.toBool(),
            )
        }
        val albums = database.catalogQueries.selectAlbums().awaitAsList().map {
            Album(
                id = it.id,
                title = it.title,
                artist = it.artist,
                year = it.year?.toInt(),
                thumbUrl = it.thumbUrl,
                dateAddedMs = it.dateAddedMs,
                genre = it.genre,
                mood = it.mood,
                style = it.style,
                rating = it.rating?.toFloat(),
                favorite = it.favorite.toBool(),
            )
        }
        val playlists = database.catalogQueries.selectPlaylists().awaitAsList().map {
            Playlist(
                id = it.id,
                title = it.title,
                trackCount = it.trackCount.toInt(),
                key = it.plKey,
                thumbUrl = it.thumbUrl,
                rating = it.rating?.toFloat(),
                favorite = it.favorite.toBool(),
            )
        }
        return CatalogSnapshot(
            artists = artists,
            albums = albums,
            playlists = playlists,
            collectionValueLoads = database.catalogQueries.selectCollectionValueLoads().awaitAsList().map {
                CatalogCollectionValueLoad(
                    target = it.target,
                    facet = it.facet,
                )
            },
            collectionValues = database.catalogQueries.selectCollectionValues().awaitAsList().map {
                CatalogCollectionValue(
                    target = it.target,
                    facet = it.facet,
                    value = it.value_,
                    key = it.key,
                    fastKey = it.fastKey,
                    filterField = it.filterField,
                    itemsLoaded = it.itemsLoaded != 0L,
                )
            },
            collectionTags = database.catalogQueries.selectCollectionTags().awaitAsList().map {
                CatalogCollectionTag(
                    target = it.target,
                    facet = it.facet,
                    itemId = it.itemId,
                    value = it.value_,
                )
            },
        )
    }

    private suspend fun readTracksFromDatabase(): CatalogSnapshot {
        val trackRows = database.catalogQueries.selectAllTracks().awaitAsList()
        yield()
        val tracksById: Map<String, Track> = trackRows.associate { row ->
                row.id to Track(
                    id = row.id,
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    durationMs = row.durationMs,
                    streamUrl = row.streamUrl,
                    downloadUrl = row.downloadUrl,
                    thumbUrl = row.thumbUrl,
                    localArtworkUri = row.localArtworkUri,
                    localUri = row.localUri,
                    year = row.year?.toInt(),
                    genre = row.genre,
                    mood = row.mood,
                    style = row.style,
                    filepath = row.filepath,
                    audioCodec = row.audioCodec,
                    bitrateKbps = row.bitrateKbps?.toInt(),
                    dateAddedMs = row.dateAddedMs,
                    rating = row.rating?.toFloat(),
                    parentAlbumId = row.parentAlbumId,
                )
            }
        yield()
        val tracksByParent: Map<String, List<Track>> = database.catalogQueries.selectTrackParents()
            .awaitAsList()
            .groupBy { it.parentId }
            .mapValues { (_, entries) ->
                entries.sortedBy { it.position }
                    .mapNotNull { tracksById[it.trackId] }
            }
        val downloads = database.downloadsQueries.selectAll().awaitAsList().map { row ->
            DownloadItem(
                trackId = row.trackId,
                title = row.title,
                artist = row.artist,
                state = runCatching { DownloadState.valueOf(row.dlState) }.getOrDefault(DownloadState.Failed),
                progress = row.progress.toFloat(),
                localUri = row.localUri,
            )
        }
        return CatalogSnapshot(
            tracksByParent = tracksByParent,
            downloads = downloads,
        )
    }

    private fun CatalogSnapshot.isNotEmpty(): Boolean =
        artists.isNotEmpty() ||
            albums.isNotEmpty() ||
            playlists.isNotEmpty() ||
            tracksByParent.isNotEmpty() ||
            collectionValues.isNotEmpty() ||
            collectionValueLoads.isNotEmpty() ||
            collectionTags.isNotEmpty() ||
            downloads.isNotEmpty()

    private fun CatalogSnapshot.hasCollectionValueLoad(entry: CollectionEntry): Boolean =
        collectionValueLoads.any { it.target == entry.target.name && it.facet == entry.facet.name }

    private fun CollectionTarget.toPlexCollectionTarget(): PlexCollectionTarget =
        when (this) {
            CollectionTarget.Artists -> PlexCollectionTarget.Artists
            CollectionTarget.Albums -> PlexCollectionTarget.Albums
        }

    private fun CollectionFacet.toPlexCollectionFacet(): PlexCollectionFacet =
        when (this) {
            CollectionFacet.Mood -> PlexCollectionFacet.Mood
            CollectionFacet.Style -> PlexCollectionFacet.Style
            CollectionFacet.Genre -> PlexCollectionFacet.Genre
        }

    private fun plexRatingKey(id: String): String? =
        if (id.startsWith("plex:")) id.removePrefix("plex:") else null

    private fun String.normalizedFavoritePlaylistTitle(): String =
        trim().lowercase()

    private fun Track.plexIdentityKey(): String? =
        plexRatingKey(id) ?: id.takeIf { it.isNotBlank() && ':' !in it }

    private fun Track.hasSamePlexIdentity(otherId: String): Boolean {
        if (id == otherId) return true
        val selfKey = plexIdentityKey() ?: return false
        val otherKey = plexRatingKey(otherId) ?: otherId.takeIf { it.isNotBlank() && ':' !in it } ?: return false
        return selfKey == otherKey
    }

    /**
     * Lazy-loaded Plex tracks come back with raw rating keys (e.g. `46171`), but the rest of
     * the catalog stores them prefixed (`plex:46171`) because [CatalogMerge.withPrefix]
     * touches every id during initial sync. Without this fix, playlist mutations downstream
     * call `plexRatingKey(track.id)` which returns `null` for any lazy-loaded track, so the
     * Plex sync silently no-ops with `ratingKeys=[]`.
     */
    private fun Track.withPlexPrefix(): Track =
        if (id.startsWith("plex:")) this else copy(id = "plex:$id")

    private fun Track.shouldPreserveAcrossPlexRefresh(currentToken: String?): Boolean {
        if (isLocalMediaPlayback() || !isPlexLibraryTrack()) return true
        return currentToken != null && streamUrl.contains(currentToken)
    }

    private companion object {
        const val LegacyCatalogFile = "catalog.json"
        const val DecadeAlbumFetchTimeoutMs = 4_000L
        const val TrackIndexPageSize = 500
        const val MaxTrackIndexPages = 400
        const val DecadeTrackPageSize = 250
        const val DecadeFirstPageSize = 80
        const val DecadeTrackLimit = 500
        const val ArtistStationLookupTimeoutMs = 8_000L
        const val MaxDecadeTrackPages = 4
    }
}

private fun Boolean.toDb(): Long = if (this) 1L else 0L
private fun Long.toBool(): Boolean = this != 0L

data class MetadataUpdateResult(
    val savedLocally: Boolean,
    val plexAttempted: Boolean,
    val plexSynced: Boolean,
)

fun defaultPlexRadioStations(library: MusicLibrary): List<PlexRadioStation> =
    listOf(
        PlexRadioStation(
            id = "library-${library.key}-radio",
            title = "Library Radio",
            subtitle = "Plex radio from ${library.title}",
            key = "/library/sections/${library.key}/stations/1",
            category = PlexRadioStationCategory.Library,
        ),
        PlexRadioStation(
            id = "library-${library.key}-deep-cuts",
            title = "Deep Cuts Radio",
            subtitle = "Less obvious songs from ${library.title}",
            key = "/library/sections/${library.key}/stations/8",
            category = PlexRadioStationCategory.Library,
        ),
        PlexRadioStation(
            id = "library-${library.key}-random-album",
            title = "Random Album Radio",
            subtitle = "Full albums selected by Plex",
            key = "/library/sections/${library.key}/stations/3",
            category = PlexRadioStationCategory.Library,
        ),
    )

private fun Float?.normalizedRating(): Float? =
    this?.coerceIn(0f, 5f)
        ?.let { kotlin.math.round(it * 2f) / 2f }
        ?.takeIf { it > 0f }
