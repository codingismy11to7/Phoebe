package com.phoebe.app.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.phoebe.app.db.PhoebeDatabase
import com.phoebe.app.domain.MusicLibrary
import com.phoebe.app.domain.PlexPin
import com.phoebe.app.domain.PlexServer
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.platform.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class SessionRepository(
    private val plexClient: PlexClient,
    private val database: PhoebeDatabase,
    private val storage: PlatformStorage,
) {
    private val json = PlexClient.PlexJson
    private val mutableSession = MutableStateFlow<PlexSession?>(null)
    val session: StateFlow<PlexSession?> = mutableSession

    suspend fun restore() {
        val row = withContext(Dispatchers.Default) {
            database.sessionQueries.selectCurrent().awaitAsOneOrNull()
        }
        if (row != null) {
            mutableSession.value = row.toSession()
            return
        }
        val legacy = storage.readText(LegacySessionFile) ?: return
        val parsed = runCatching {
            json.decodeFromString<PlexSession>(legacy)
        }.getOrNull() ?: return
        withContext(Dispatchers.Default) { persist(parsed) }
        mutableSession.value = parsed
        storage.delete(LegacySessionFile)
    }

    suspend fun createPin(): PlexPin = plexClient.createPin()

    suspend fun completePin(pin: PlexPin): Boolean {
        val token = plexClient.pollPin(pin.id) ?: return false
        val session = PlexSession(token = token, userName = plexClient.userName(token))
        save(session)
        return true
    }

    suspend fun servers(): List<PlexServer> {
        val token = mutableSession.value?.token ?: return emptyList()
        return plexClient.servers(token)
    }

    suspend fun libraries(server: PlexServer): List<MusicLibrary> {
        val token = mutableSession.value?.token ?: return emptyList()
        return plexClient.musicLibraries(server, token)
    }

    suspend fun selectServer(server: PlexServer) {
        mutableSession.value?.let { save(it.copy(selectedServer = server, selectedLibrary = null)) }
    }

    suspend fun selectLibrary(library: MusicLibrary) {
        mutableSession.value?.let { save(it.copy(selectedLibrary = library)) }
    }

    suspend fun signOut() {
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
            token = session.token,
            userName = session.userName,
            selectedServerId = server?.id,
            selectedServerName = server?.name,
            selectedServerUri = server?.uri,
            selectedServerOwned = server?.owned?.toDb(),
            selectedLibraryKey = library?.key,
            selectedLibraryTitle = library?.title,
        )
    }

    private fun com.phoebe.app.db.SessionRow.toSession(): PlexSession {
        val server = if (selectedServerId != null && selectedServerName != null && selectedServerUri != null) {
            PlexServer(
                id = selectedServerId,
                name = selectedServerName,
                uri = selectedServerUri,
                owned = (selectedServerOwned ?: 0L).toBool(),
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
        )
    }

    private companion object {
        const val LegacySessionFile = "session.json"
    }
}

private fun Boolean.toDb(): Long = if (this) 1L else 0L
private fun Long.toBool(): Boolean = this != 0L
