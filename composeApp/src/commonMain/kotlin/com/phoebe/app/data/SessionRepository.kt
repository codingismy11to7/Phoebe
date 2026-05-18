package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.JellyfinSyncMode
import com.phoebe.app.domain.MediaProviderType
import com.phoebe.app.domain.PlexPin
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.isEmbyFamily
import com.phoebe.app.domain.isJellyfin
import com.phoebe.app.domain.isPlex
import com.phoebe.app.platform.PhoebeLog
import com.phoebe.app.platform.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import io.ktor.client.HttpClient

class SessionRepository(
    private val plexClient: PlexClient,
    private val jellyfinClient: JellyfinClient = JellyfinClient(HttpClient()),
    private val providerRegistry: MusicProviderRegistry = MusicProviderRegistry(emptyList()),
    private val database: PhoebeDatabase,
    private val storage: PlatformStorage,
) {
    constructor(
        plexClient: PlexClient,
        database: PhoebeDatabase,
        storage: PlatformStorage,
    ) : this(plexClient, JellyfinClient(HttpClient()), MusicProviderRegistry(emptyList()), database, storage)

    private val json = PlexClient.PlexJson
    private val mutableSession = MutableStateFlow<PlexSession?>(null)
    val session: StateFlow<PlexSession?> = mutableSession

    suspend fun restore(refreshConnections: Boolean = true) {
        PhoebeLog.d("SessionRepository") { "restore(refreshConnections=$refreshConnections)" }
        val row = withContext(Dispatchers.Default) {
            database.sessionQueries.selectCurrent().awaitAsOneOrNull()
        }
        if (row != null) {
            mutableSession.value = row.toSession()
        } else {
            val legacy = storage.readText(LegacySessionFile) ?: return
            val parsed = runCatching {
                json.decodeFromString<PlexSession>(legacy)
            }.getOrNull() ?: return
            withContext(Dispatchers.Default) { persist(parsed) }
            mutableSession.value = parsed
            storage.delete(LegacySessionFile)
        }
        if (refreshConnections) refreshSelectedServerConnections()
        PhoebeLog.d("SessionRepository") {
            val s = mutableSession.value
            "restore complete → user=${s?.userName ?: "none"}, server=${s?.selectedServer?.name ?: "none"}, library=${s?.selectedLibrary?.title ?: "none"}"
        }
    }

    /** Refresh server URLs from plex.tv so we pick up LAN addresses for timeline API calls. */
    suspend fun refreshSelectedServerConnections() {
        val current = mutableSession.value ?: return
        val selected = current.selectedServer ?: return
        if (current.token.isBlank()) return
        if (!current.isPlex()) return
        PhoebeLog.v("SessionRepository") { "refreshSelectedServerConnections for '${selected.name}'" }
        val fresh = runCatching { plexClient.servers(current.token) }.getOrNull()
            ?.find { it.id == selected.id }
            ?: return
        if (fresh != selected) {
            PhoebeLog.d("SessionRepository") { "updated server connections for '${fresh.name}'" }
            save(current.copy(selectedServer = fresh))
        }
    }

    suspend fun createPin(): PlexPin = plexClient.createPin()

    suspend fun completePin(pin: PlexPin): Boolean {
        val token = plexClient.pollPin(pin.id) ?: return false
        val session = PlexSession(token = token, userName = plexClient.userName(token), providerType = MediaProviderType.Plex)
        save(session)
        return true
    }

    /**
     * Exchanges an approved Plex pin for a session token, then loads the account's servers in
     * parallel with resolving the Plex username so sign-in does not wait on three serial calls.
     */
    suspend fun completePinAndListServers(pin: PlexPin): List<PlexServer>? {
        val token = plexClient.pollPin(pin.id) ?: return null
        PhoebeLog.d("SessionRepository") { "pin complete, loading servers" }
        return coroutineScope {
            val userNameDeferred = async {
                runCatching { plexClient.userName(token) }.getOrNull() ?: "Plex listener"
            }
            val serversDeferred = async { plexClient.servers(token) }
            save(PlexSession(token = token, userName = userNameDeferred.await(), providerType = MediaProviderType.Plex))
            serversDeferred.await()
        }
    }

    suspend fun signInJellyfin(serverUrl: String, username: String, password: String): PlexServer {
        val session = providerRegistry.adapterFor(MediaProviderType.Jellyfin)
            ?.signIn(serverUrl, username, password)
            ?: run {
                val auth = jellyfinClient.authenticate(serverUrl, username, password)
                PlexSession(
                    token = auth.token,
                    userName = auth.userName,
                    selectedServer = auth.server,
                    providerType = MediaProviderType.Jellyfin,
                    userId = auth.userId,
                )
            }
        save(session)
        return session.selectedServer ?: error("Jellyfin did not return a server.")
    }

    suspend fun signInProvider(type: MediaProviderType, serverUrl: String, username: String, password: String): PlexServer {
        val adapter = providerRegistry.adapterFor(type) ?: error("${type.name} is not available.")
        val session = adapter.signIn(serverUrl, username, password)
        save(session)
        return session.selectedServer ?: error("${type.name} did not return a server.")
    }

    suspend fun startJellyfinQuickConnect(serverUrl: String): JellyfinQuickConnectResult =
        jellyfinClient.initiateQuickConnect(serverUrl)

    suspend fun completeJellyfinQuickConnect(serverUrl: String, secret: String): PlexServer {
        val auth = jellyfinClient.authenticateQuickConnect(serverUrl, secret)
        save(
            PlexSession(
                token = auth.token,
                userName = auth.userName,
                selectedServer = auth.server,
                providerType = MediaProviderType.Jellyfin,
                userId = auth.userId,
            ),
        )
        return auth.server
    }

    suspend fun servers(): List<PlexServer> {
        val session = mutableSession.value ?: return emptyList()
        if (!session.isPlex()) return providerRegistry.adapterFor(session)?.servers(session) ?: listOfNotNull(session.selectedServer)
        val token = session.token
        return plexClient.servers(token)
    }

    suspend fun libraries(server: PlexServer): List<MusicLibrary> {
        val current = mutableSession.value ?: return emptyList()
        if (!current.isPlex()) {
            providerRegistry.adapterFor(current)?.let { return it.libraries(current, server) }
            if (current.isEmbyFamily()) {
                val userId = current.userId ?: return emptyList()
                return jellyfinClient.libraries(server, current.token, userId)
            }
            return emptyList()
        }
        val token = current.token
        val resolved = mutableSession.value?.selectedServer?.takeIf { it.id == server.id } ?: server
        runCatching { plexClient.resolveFastestBase(resolved, resolved.authToken(token)) }
        return plexClient.musicLibraries(resolved, resolved.authToken(token))
    }

    suspend fun selectServer(server: PlexServer, refreshConnections: Boolean = true): PlexServer {
        PhoebeLog.d("SessionRepository") { "selectServer '${server.name}' (refreshConnections=$refreshConnections)" }
        mutableSession.value?.let { save(it.copy(selectedServer = server, selectedLibrary = null)) }
        if (refreshConnections) refreshSelectedServerConnections()
        return mutableSession.value?.selectedServer ?: server
    }

    suspend fun selectLibrary(library: MusicLibrary, jellyfinSyncMode: JellyfinSyncMode? = null) {
        PhoebeLog.d("SessionRepository") { "selectLibrary '${library.title}'" }
        mutableSession.value?.let { session ->
            save(
                session.copy(
                    selectedLibrary = library,
                    jellyfinSyncMode = jellyfinSyncMode ?: session.jellyfinSyncMode,
                ),
            )
        }
    }

    suspend fun signOut() {
        PhoebeLog.d("SessionRepository") { "signOut" }
        mutableSession.value = null
        withContext(Dispatchers.Default) { database.sessionQueries.clear() }
    }

    private suspend fun save(session: PlexSession) {
        mutableSession.value = session
        withContext(Dispatchers.Default) {
            if (session.token.isBlank()) {
                database.sessionQueries.clear()
            } else {
                persist(session)
            }
        }
    }

    private suspend fun persist(session: PlexSession) {
        val server = session.selectedServer
        val library = session.selectedLibrary
            database.sessionQueries.upsert(
                providerType = session.providerType.name,
                token = session.token,
                userName = session.userName,
                userId = session.userId,
                selectedServerId = server?.id,
                selectedServerName = server?.name,
                selectedServerUri = server?.uri,
                selectedServerOwned = server?.owned?.toDb(),
                selectedServerConnectionUris = server?.connectionUris?.toDbList(),
                selectedServerAdvertisedConnectionUris = server?.advertisedConnectionUris?.toDbList(),
                selectedServerLocalConnectionUris = server?.localConnectionUris?.toDbList(),
                selectedServerAccessToken = server?.accessToken,
                selectedServerHttpsRequired = server?.httpsRequired?.toDb(),
                selectedLibraryKey = library?.key,
                selectedLibraryTitle = library?.title,
                jellyfinSyncMode = session.jellyfinSyncMode.name,
            )
    }

    private fun com.phoebe.app.db.SessionRow.toSession(): PlexSession {
        val provider = runCatching { MediaProviderType.valueOf(providerType) }.getOrDefault(MediaProviderType.Plex)
        val server = if (selectedServerId != null && selectedServerName != null && selectedServerUri != null) {
            PlexServer(
                id = selectedServerId,
                name = selectedServerName,
                uri = selectedServerUri,
                owned = (selectedServerOwned ?: 0L).toBool(),
                connectionUris = selectedServerConnectionUris.fromDbList(),
                advertisedConnectionUris = selectedServerAdvertisedConnectionUris.fromDbList(),
                localConnectionUris = selectedServerLocalConnectionUris.fromDbList(),
                accessToken = selectedServerAccessToken,
                httpsRequired = (selectedServerHttpsRequired ?: 0L).toBool(),
            )
        } else {
            null
        }
        val library = if (selectedLibraryKey != null && selectedLibraryTitle != null) {
            MusicLibrary(key = selectedLibraryKey, title = selectedLibraryTitle)
        } else {
            null
        }
        return PlexSession(
            token = token,
            userName = userName,
            selectedServer = server,
            selectedLibrary = library,
            providerType = provider,
            userId = userId,
            jellyfinSyncMode = runCatching { JellyfinSyncMode.valueOf(jellyfinSyncMode) }.getOrDefault(JellyfinSyncMode.Quick),
        )
    }

    private companion object {
        const val LegacySessionFile = "session.json"
    }
}

private fun Boolean.toDb(): Long = if (this) 1L else 0L
private fun Long.toBool(): Boolean = this != 0L

private const val DbListSeparator = "\u001F"

private fun List<String>.toDbList(): String =
    filter { it.isNotBlank() }.joinToString(DbListSeparator)

private fun String?.fromDbList(): List<String> =
    this?.takeIf { it.isNotBlank() }?.split(DbListSeparator).orEmpty()
