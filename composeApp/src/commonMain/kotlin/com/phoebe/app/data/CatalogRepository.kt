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
import com.phoebe.app.domain.LOCAL_PLAYLIST_ID_PREFIX
import com.phoebe.app.domain.Playlist
import com.phoebe.app.domain.Track
import com.phoebe.app.domain.TrackMetadataUpdate
import com.phoebe.app.domain.canAddToLocalPlaylist
import com.phoebe.app.domain.isLocalMediaPlayback
import com.phoebe.app.domain.isLocalPlaylist
import com.phoebe.app.domain.isPlexLibraryTrack
import com.phoebe.app.domain.serverAuthToken
import com.phoebe.app.domain.supportsPlexPlaylists
import com.phoebe.app.platform.PlatformStorage
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
        mutableCatalogSyncState.value = CatalogSyncState(
            phase = CatalogSyncPhase.RestoringCache,
            message = "Restoring your library…",
        )
        val cachedShell = withContext(Dispatchers.Default) { readCatalogShellFromDatabase() }
        if (cachedShell.isNotEmpty()) {
            mutableCatalog.value = cachedShell
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.LoadingSongs,
                message = "Restored albums, loading songs…",
                loadedAlbums = cachedShell.albums.size,
                loadedTracks = 0,
            )
            val cachedTracks = withContext(Dispatchers.Default) { readTracksFromDatabase() }
            if (cachedTracks.isNotEmpty()) {
                val hydrated = mutableCatalog.value.copy(
                    tracksByParent = cachedTracks.tracksByParent,
                    downloads = cachedTracks.downloads,
                )
                mutableCatalog.value = hydrated
            }
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.Complete,
                message = "Library restored.",
                loadedAlbums = mutableCatalog.value.albums.size,
                loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
            )
            return
        }
        val legacy = storage.readText(LegacyCatalogFile) ?: run {
            mutableCatalogSyncState.value = CatalogSyncState()
            return
        }
        val parsed = runCatching {
            json.decodeFromString<CatalogSnapshot>(legacy)
        }.getOrNull() ?: run {
            mutableCatalogSyncState.value = CatalogSyncState()
            return
        }
        withContext(Dispatchers.Default) { persist(parsed) }
        mutableCatalog.value = parsed
        storage.delete(LegacyCatalogFile)
        mutableCatalogSyncState.value = CatalogSyncState(
            phase = CatalogSyncPhase.Complete,
            message = "Library restored.",
        )
    }

    suspend fun refreshAggregated(session: PlexSession?) {
        var stalePlaylists: List<Playlist> = emptyList()
        val snapshot = refreshMutex.withLock {
            pushCatalogRefreshing()
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.LoadingLibrary,
                message = if (mutableCatalog.value.isNotEmpty()) "Refreshing library…" else "Loading your library…",
            )
            try {
                val ctx = SourceBuildContext(
                    session = session,
                    plexClient = plexClient,
                    httpClient = httpClient,
                    localFolders = mediaSourcesRepository.state.value.localFolders,
                )
                val previous = mutableCatalog.value

                val server = session?.selectedServer
                val library = session?.selectedLibrary
                val token = session.serverAuthToken()
                val plexBuilder = PlexCatalogBuilder(plexClient, httpClient)

                val (plexRawMetadata, localRaw) = coroutineScope {
                    val plexDeferred = async {
                        if (server != null && library != null && token != null) {
                            plexBuilder.buildMetadataCatalog(server, library, token)
                        } else {
                            CatalogSnapshot()
                        }
                    }
                    val localDeferred = async { LocalFolderMusicSourcePlugin.buildCatalog(ctx) }
                    plexDeferred.await() to localDeferred.await()
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
                mutableCatalogSyncState.value = CatalogSyncState(
                    phase = CatalogSyncPhase.LoadingSongs,
                    message = "Loaded albums, fetching songs…",
                    loadedAlbums = metadataMerged.albums.size,
                    loadedTracks = reconciled.snapshot.tracksByParent.values.sumOf { it.size },
                )
                yield()

                if (server != null && token != null && plexRawMetadata.albums.isNotEmpty()) {
                    plexBuilder.prefetchAlbumTracks(server, plexRawMetadata.albums, token) { album, tracks ->
                        val parentId = "plex:${album.id}"
                        val prefixedTracks = tracks.map { it.withPlexPrefix() }
                        catalogMergeMutex.withLock {
                            val cur = mutableCatalog.value
                            mutableCatalog.value = cur.copy(
                                tracksByParent = cur.tracksByParent + (parentId to prefixedTracks),
                            )
                            mutableCatalogSyncState.value = CatalogSyncState(
                                phase = CatalogSyncPhase.LoadingSongs,
                                message = "Loaded albums, fetching songs…",
                                loadedAlbums = cur.albums.size,
                                loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
                            )
                        }
                    }
                }

                mutableCatalogSyncState.value = CatalogSyncState(
                    phase = CatalogSyncPhase.FinishingArtwork,
                    message = "Finishing artwork…",
                    loadedAlbums = mutableCatalog.value.albums.size,
                    loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
                )
                plexBuilder.enrichWithTrackArtwork(mutableCatalog.value)
                    .copy(downloads = previous.downloads)
                    .also { next ->
                        yield()
                        mutableCatalog.value = next
                    }
            } catch (error: Throwable) {
                mutableCatalogSyncState.value = CatalogSyncState(
                    phase = CatalogSyncPhase.Failed,
                    message = error.message ?: "Sync failed.",
                )
                throw error
            }
        }
        try {
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.Complete,
                message = "Library refreshed.",
                loadedAlbums = mutableCatalog.value.albums.size,
                loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
            )
            persistAsync(snapshot)
            // Refetch each playlist that grew externally after the main catalog is visible. Each
            // refetch persists its own update, so the full-snapshot write above must happen first.
            for (playlist in stalePlaylists) {
                mutableCatalogSyncState.value = CatalogSyncState(
                    phase = CatalogSyncPhase.RefreshingPlaylists,
                    message = "Refreshing playlists…",
                    loadedAlbums = mutableCatalog.value.albums.size,
                    loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
                )
                runCatching { refetchPlaylistTracksFromPlex(session, playlist) }
                    .onFailure { error ->
                        println("[CatalogRepository] background refetch failed for '${playlist.title}': ${error.message}")
                    }
            }
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.Complete,
                message = "Library refreshed.",
                loadedAlbums = mutableCatalog.value.albums.size,
                loadedTracks = mutableCatalog.value.tracksByParent.values.sumOf { it.size },
            )
        } catch (error: Throwable) {
            mutableCatalogSyncState.value = CatalogSyncState(
                phase = CatalogSyncPhase.Failed,
                message = error.message ?: "Sync failed.",
            )
            throw error
        } finally {
            popCatalogRefreshing()
        }
    }

    private data class ReconciledSnapshot(
        val snapshot: CatalogSnapshot,
        val stalePlaylists: List<Playlist>,
    )

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

        return ReconciledSnapshot(
            snapshot = merged.copy(
                playlists = reconciledPlaylists + localPlaylists,
                tracksByParent = preservedTracks + merged.tracksByParent,
                downloads = previous.downloads,
            ),
            stalePlaylists = staleForRefetch,
        )
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
            val tracks = plexClient.children(server, rating, session.serverAuthToken()!!).map { it.withPlexPrefix() }
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
                            val tracks = plexClient.children(server, rating, token).map { it.withPlexPrefix() }
                            catalogMergeMutex.withLock {
                                val cur = mutableCatalog.value
                                publish(
                                    cur.copy(tracksByParent = cur.tracksByParent + (album.id to tracks)),
                                    persist = false,
                                )
                            }
                        }.onFailure { e ->
                            println("[CatalogRepository] album track fetch failed for '${album.title}': ${e.message}")
                        }
                    }
                }.awaitAll()
            }
            publish(mutableCatalog.value, persist = true)
        }
    }

    suspend fun tracksForPlaylist(session: PlexSession?, playlist: Playlist): List<Track> {
        if (playlist.isLocalPlaylist()) {
            return mutableCatalog.value.tracksByParent[playlist.id].orEmpty()
        }
        val snapshot = mutableCatalog.value
        val playlistMeta = snapshot.playlists.find { it.id == playlist.id } ?: playlist
        val existing = snapshot.tracksByParent[playlist.id]
        if (!existing.isNullOrEmpty() && existing.size >= playlistMeta.trackCount) return existing
        refetchPlaylistTracksFromPlex(session, playlistMeta)
        return mutableCatalog.value.tracksByParent[playlist.id].orEmpty()
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
            println("[CatalogRepository] createPlexPlaylist '$title' failed: ${error.message}")
            return null
        }
        val prefixedPlaylist = created.copy(id = "plex:${created.id}")
        val prefixedTracks = initialTracks.filter { plexRatingKey(it.id) != null }
        val snapshot = mutableCatalog.value
        val nextPlaylists = (snapshot.playlists.filterNot { it.id == prefixedPlaylist.id } + prefixedPlaylist)
        val nextTracks = if (prefixedTracks.isEmpty()) {
            snapshot.tracksByParent
        } else {
            snapshot.tracksByParent + (prefixedPlaylist.id to prefixedTracks)
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
        println("[CatalogRepository] addTracksToPlaylist entry → playlist='${playlist.title}' (${playlist.id}), tracks=${tracks.map { it.id }}")
        if (tracks.isEmpty()) return
        if (playlist.isLocalPlaylist()) {
            addTracksToLocalPlaylist(playlist, tracks)
            return
        }
        if (!playlist.id.startsWith("plex:")) {
            println("[CatalogRepository] addTracksToPlaylist: ignoring non-Plex playlist ${playlist.id}")
            return
        }
        if (session?.supportsPlexPlaylists() != true) {
            println("[CatalogRepository] addTracksToPlaylist: Plex session not ready")
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
            println("[CatalogRepository] addTracksToPlaylist: nothing to add after Plex filters, skipping")
            return
        }

        val server = s.selectedServer
        val token = s.serverAuthToken()
        val playlistRating = plexRatingKey(playlist.id)
        val ratingKeys = toAdd.mapNotNull { plexRatingKey(it.id) }
        println("[CatalogRepository] plex branch: hasServer=${server != null}, hasToken=${token != null}, playlistRating=$playlistRating, ratingKeys=$ratingKeys")
        if (server != null && token != null && playlistRating != null && ratingKeys.isNotEmpty()) {
            val machineId = resolveMachineIdentifier(server, token)
            println("[CatalogRepository] resolved machineIdentifier='$machineId' (server.id was '${server.id}')")
            runCatching {
                plexClient.addTracksToPlaylist(server, token, machineId, playlistRating, ratingKeys)
            }.onFailure { error ->
                println("[CatalogRepository] addTracksToPlaylist failed for '${playlist.title}': ${error.message}")
            }.onSuccess { result ->
                println("[CatalogRepository] Plex sync OK for '${playlist.title}': leafCountAdded=$result")
            }
        } else {
            println("[CatalogRepository] skipping Plex sync — missing one of server/token/playlistRating/ratingKeys")
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
                println("[CatalogRepository] updateTrackMetadata Plex sync failed for '${existing.title}': ${error.message}")
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
            )
        }
        val albums = database.catalogQueries.selectAlbums().awaitAsList().map {
            Album(
                id = it.id,
                title = it.title,
                artist = it.artist,
                year = it.year?.toInt(),
                thumbUrl = it.thumbUrl,
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
    }
}

data class MetadataUpdateResult(
    val savedLocally: Boolean,
    val plexAttempted: Boolean,
    val plexSynced: Boolean,
)
