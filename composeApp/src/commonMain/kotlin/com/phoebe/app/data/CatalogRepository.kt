package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.Album
import com.phoebe.app.domain.Artist
import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.CatalogSyncPhase
import com.phoebe.app.domain.CatalogSyncState
import com.phoebe.app.domain.DownloadItem
import com.phoebe.app.domain.DownloadState
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.LIKED_SONGS_PLAYLIST_TITLE
import com.phoebe.app.domain.LOCAL_PLAYLIST_ID_PREFIX
import com.phoebe.app.domain.PENDING_LIKED_SONGS_PLAYLIST_ID
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.domain.canAddToLocalPlaylist
import com.phoebe.app.domain.canAddToPlexPlaylist
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.domain.isLikedSongsPlaylist
import com.phoebe.app.domain.isPlexLibraryTrack
import com.phoebe.app.domain.serverAuthToken
import com.phoebe.app.domain.supportsPlexPlaylists
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.random.Random

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
                mutableCatalog.value = hydrated
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
        withContext(Dispatchers.Default) { persist(parsed) }
        mutableCatalog.value = parsed
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
                        val albumsDeferred = async { plexClient.albums(server, library, token) }
                        val artistsDeferred = async { plexClient.artists(server, library, token) }
                        val playlistsDeferred = async { plexClient.playlists(server, token) }

                        val rawAlbums = albumsDeferred.await()
                        if (rawAlbums.isNotEmpty()) {
                            publishPlexMetadataPartial(
                                raw = CatalogSnapshot(albums = rawAlbums),
                                previous = previous,
                                session = session,
                                message = "Found albums…",
                            )
                            yield()
                        }

                        val rawPlaylists = playlistsDeferred.await()
                        if (rawPlaylists.isNotEmpty()) {
                            publishPlexMetadataPartial(
                                raw = CatalogSnapshot(albums = rawAlbums, playlists = rawPlaylists),
                                previous = previous,
                                session = session,
                                message = "Found playlists…",
                            )
                            yield()
                        }

                        val rawArtists = artistsDeferred.await()
                        val artistsResolved = enrichArtistAlbumCountsOnly(
                            enrichArtistArtwork(rawArtists, rawAlbums).ifEmpty {
                                rawAlbums.groupBy { it.artist }.values.map { list ->
                                    val first = list.first()
                                    Artist(
                                        id = "album-artist-${first.id}",
                                        title = first.artist,
                                        thumbUrl = first.thumbUrl,
                                        albumCount = list.size,
                                    )
                                }
                            },
                            rawAlbums,
                        )
                        CatalogSnapshot(
                            artists = artistsResolved,
                            albums = rawAlbums,
                            playlists = rawPlaylists,
                        ) to localDeferred.await()
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

        // If Plex reports a playlist grew, keep the stale tracks visible and refetch after the
        // main catalog is published so the detail panel updates in place.
        val staleForRefetch = mutableListOf<Playlist>()
        val reconciledPlaylists = merged.playlists.map { p ->
            val cached = preservedTracks[p.id]
            val cachedSize = cached?.size ?: 0
            when {
                cached != null && p.trackCount > cachedSize -> {
                    staleForRefetch += p
                    p
                }
                cachedSize > p.trackCount -> p.copy(trackCount = cachedSize)
                else -> p
            }
        }

        val reconciled = merged.copy(
                playlists = reconciledPlaylists + localPlaylists,
                tracksByParent = preservedTracks + merged.tracksByParent,
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
            album.copy(dateAddedMs = added)
        }
        val previousArtists = previous.artists.associateBy { it.id }
        val artists = next.artists.map { artist ->
            val added = artist.dateAddedMs
                ?: previousArtists[artist.id]?.dateAddedMs
                ?: albums
                    .filter { it.artist.equals(artist.title, ignoreCase = true) }
                    .mapNotNull { it.dateAddedMs }
                    .maxOrNull()
            artist.copy(dateAddedMs = added)
        }
        return next.copy(artists = artists, albums = albums, tracksByParent = tracksByParent)
    }

    private fun preserveTrackDateAdded(existing: List<Track>, incoming: List<Track>): List<Track> {
        if (existing.isEmpty()) return incoming
        val existingById = existing.associateBy { it.id }
        return incoming.map { track ->
            val previousAdded = existingById[track.id]?.dateAddedMs
            if (previousAdded != null) track.copy(dateAddedMs = previousAdded) else track
        }
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
    private suspend fun refetchPlaylistTracksFromPlex(session: PlexSession?, playlist: Playlist) {
        withCatalogRefreshing {
            val rating = plexRatingKey(playlist.id) ?: return@withCatalogRefreshing
            val server = session?.selectedServer ?: return@withCatalogRefreshing
            val token = session.serverAuthToken() ?: return@withCatalogRefreshing
            val tracks = plexClient.playlistTracks(server, playlist.copy(id = rating), token)
                .map { it.withPlexPrefix() }
                .let { preserveTrackDateAdded(mutableCatalog.value.tracksByParent[playlist.id].orEmpty(), it) }
            publish(
                mutableCatalog.value.copy(
                    tracksByParent = mutableCatalog.value.tracksByParent + (playlist.id to tracks),
                    playlists = mutableCatalog.value.playlists.map { p ->
                        if (p.id == playlist.id) {
                            p.copy(
                                trackCount = tracks.size,
                                thumbUrl = p.thumbUrl ?: tracks.firstNotNullOfOrNull { it.thumbUrl },
                            )
                        } else p
                    },
                ),
                persist = true,
            )
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
        if (!existing.isNullOrEmpty() && existing.size >= playlistMeta.trackCount) return existing
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
        return mutableCatalog.value.tracksByParent[liked.id].orEmpty().any { it.id == trackId }
    }

    suspend fun toggleLikedTrack(session: PlexSession?, track: Track): Boolean {
        return toggleLikedTrackRemote(session, track)
    }

    suspend fun toggleLikedTrackLocally(track: Track): Boolean {
        if (!track.canAddToPlexPlaylist()) return false
        val playlist = ensureLocalLikedSongsPlaylist()
        val snapshot = mutableCatalog.value
        val existing = snapshot.tracksByParent[playlist.id].orEmpty()
        val isLiked = existing.any { it.id == track.id }
        val updated = if (isLiked) {
            existing.filterNot { it.id == track.id }
        } else {
            existing + track
        }
        publishLikedSongs(playlist, updated)
        return !isLiked
    }

    suspend fun syncLikedSongsPlaylist(session: PlexSession?): Boolean {
        if (session?.supportsPlexPlaylists() != true) return false
        val localPlaylist = mutableCatalog.value.playlists.firstOrNull { it.isLikedSongsPlaylist() } ?: return false
        val desiredTracks = mutableCatalog.value.tracksByParent[localPlaylist.id].orEmpty()
        val remotePlaylist = mutableCatalog.value.playlists.firstOrNull {
            it.isLikedSongsPlaylist() && it.id != PENDING_LIKED_SONGS_PLAYLIST_ID
        } ?: run {
            if (desiredTracks.isEmpty()) return false
            return createPlaylist(session, LIKED_SONGS_PLAYLIST_TITLE, desiredTracks) != null
        }

        val remoteTracks = runCatching {
            refetchPlaylistTracksFromPlex(session, remotePlaylist)
            mutableCatalog.value.tracksByParent[remotePlaylist.id].orEmpty()
        }.getOrElse {
            mutableCatalog.value.tracksByParent[remotePlaylist.id].orEmpty()
        }
        val desiredIds = desiredTracks.map { it.id }.toSet()
        val remoteIds = remoteTracks.map { it.id }.toSet()
        val toAdd = desiredTracks.filterNot { it.id in remoteIds }
        val toRemove = remoteTracks.filter { it.id !in desiredIds && it.playlistItemId != null }
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
        if (session?.supportsPlexPlaylists() != true || !track.canAddToPlexPlaylist()) return false
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
                .firstOrNull { it.id == track.id }
            val removable = localLikedTrack?.takeIf { it.playlistItemId != null } ?: run {
                runCatching { refetchPlaylistTracksFromPlex(session, remotePlaylist) }
                mutableCatalog.value.tracksByParent[remotePlaylist.id].orEmpty()
                    .firstOrNull { it.id == track.id && it.playlistItemId != null }
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
        if (!track.canAddToPlexPlaylist()) return false
        val playlist = findOrCreateLikedSongsPlaylist(session) ?: return false
        val fresh = runCatching {
            refetchPlaylistTracksFromPlex(session, playlist)
            mutableCatalog.value.tracksByParent[playlist.id].orEmpty()
        }.getOrElse {
            mutableCatalog.value.tracksByParent[playlist.id].orEmpty()
        }
        val existing = fresh.firstOrNull { it.id == track.id }
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
                tracksByParent = snapshot.tracksByParent + (playlist.id to (existing + toAdd)),
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
        val updated = existing + toAdd
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

    suspend fun download(track: Track) {
        if (track.downloadUrl.isBlank()) {
            updateDownload(track, DownloadState.Failed, progress = 0f)
            return
        }
        updateDownload(track, DownloadState.Downloading, progress = 0.1f)
        runCatching {
            val bytes = httpClient.get(track.downloadUrl).body<ByteArray>()
            val localUri = storage.writeBytes("downloads/${track.id}.audio", bytes)
            val offlineTrack = track.copy(localUri = localUri)
            val updatedTracks = mutableCatalog.value.tracksByParent.mapValues { (_, tracks) ->
                tracks.map { if (it.id == track.id) offlineTrack else it }
            }
            publish(mutableCatalog.value.copy(tracksByParent = updatedTracks), persist = true)
            updateDownload(offlineTrack, DownloadState.Complete, progress = 1f)
        }.onFailure {
            updateDownload(track, DownloadState.Failed, progress = 0f)
        }
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

    private suspend fun persist(snapshot: CatalogSnapshot) {
        database.transaction {
            database.catalogQueries.clearTrackParents()
            database.catalogQueries.clearTracks()
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
                    localUri = track.localUri,
                    year = track.year?.toLong(),
                    genre = track.genre,
                    filepath = track.filepath,
                    audioCodec = track.audioCodec,
                    bitrateKbps = track.bitrateKbps?.toLong(),
                    dateAddedMs = track.dateAddedMs,
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
            )
        }
        val playlists = database.catalogQueries.selectPlaylists().awaitAsList().map {
            Playlist(
                id = it.id,
                title = it.title,
                trackCount = it.trackCount.toInt(),
                key = it.plKey,
                thumbUrl = it.thumbUrl,
            )
        }
        return CatalogSnapshot(
            artists = artists,
            albums = albums,
            playlists = playlists,
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
                    localUri = row.localUri,
                    year = row.year?.toInt(),
                    genre = row.genre,
                    filepath = row.filepath,
                    audioCodec = row.audioCodec,
                    bitrateKbps = row.bitrateKbps?.toInt(),
                    dateAddedMs = row.dateAddedMs,
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
            downloads.isNotEmpty()

    private fun plexRatingKey(id: String): String? =
        if (id.startsWith("plex:")) id.removePrefix("plex:") else null

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
        const val MaxDecadeTrackPages = 4
    }
}

data class MetadataUpdateResult(
    val savedLocally: Boolean,
    val plexAttempted: Boolean,
    val plexSynced: Boolean,
)
