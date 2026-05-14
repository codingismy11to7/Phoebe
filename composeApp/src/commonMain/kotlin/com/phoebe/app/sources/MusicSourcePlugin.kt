package com.phoebe.app.sources

import com.phoebe.app.domain.CatalogSnapshot
import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.PlexSession
import com.phoebe.app.domain.serverAuthToken
import com.phoebe.app.data.LocalFileMetadataCache
import com.phoebe.app.data.PlexClient
import io.ktor.client.HttpClient

/**
 * Pluggable music catalog backend (Plex, local folders, future sources).
 */
interface MusicSourcePlugin {
    val pluginId: String

    suspend fun buildCatalog(ctx: SourceBuildContext): CatalogSnapshot
}

data class SourceBuildContext(
    val session: PlexSession?,
    val plexClient: PlexClient,
    val httpClient: HttpClient,
    val localFolders: List<LocalFolderMediaSourceConfig>,
    val localFileMetadataCache: LocalFileMetadataCache? = null,
)

object PlexMusicSourcePlugin : MusicSourcePlugin {
    override val pluginId: String = "plex"

    override suspend fun buildCatalog(ctx: SourceBuildContext): CatalogSnapshot {
        val server = ctx.session?.selectedServer ?: return CatalogSnapshot()
        val library = ctx.session.selectedLibrary ?: return CatalogSnapshot()
        val token = ctx.session.serverAuthToken() ?: return CatalogSnapshot()
        return PlexCatalogBuilder(ctx.plexClient, ctx.httpClient).buildCatalog(server, library, token)
    }
}

object LocalFolderMusicSourcePlugin : MusicSourcePlugin {
    override val pluginId: String = "local_folders"

    override suspend fun buildCatalog(ctx: SourceBuildContext): CatalogSnapshot {
        val enabled = ctx.localFolders.filter { it.enabled }
        if (enabled.isEmpty()) return CatalogSnapshot()
        var acc = CatalogSnapshot()
        for (cfg in enabled) {
            val slice = LocalFolderCatalogBuilder.build(cfg, ctx.localFileMetadataCache)
            acc = CatalogMerge.merge(acc, slice)
        }
        return acc
    }
}
