package com.phoebe.app.player

import com.phoebe.app.data.CatalogRepository
import com.phoebe.app.data.cachedArtworkPathForUrl
import com.phoebe.app.db.PhoebeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resolves a catalog entity to a real file containing its artwork.
 *
 * The car's media app opens artwork through `ImageDecoder.createSource(
 * ContentResolver, uri)`, which needs a seekable descriptor, so bytes are
 * always materialised into a private cache file rather than streamed.
 */
class ArtworkFileResolver(
    private val database: PhoebeDatabase,
    private val catalogRepository: CatalogRepository,
    private val cacheDir: File,
) {
    suspend fun resolve(type: ArtworkType, id: String): File? = withContext(Dispatchers.IO) {
        val thumbUrl = thumbUrlFor(type, id)?.takeIf { it.isNotBlank() } ?: return@withContext null
        val target = File(cacheDir, cacheFileName(thumbUrl))
        if (target.exists() && target.length() > 0L) return@withContext target

        val bytes = catalogRepository.artworkBytes(thumbUrl) ?: return@withContext null
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeBytes(bytes)
        // Rename so a concurrent reader never observes a partial file.
        if (!temp.renameTo(target)) {
            temp.delete()
            return@withContext null
        }
        target
    }

    private fun thumbUrlFor(type: ArtworkType, id: String): String? {
        val queries = database.catalogQueries
        // Each query has its own generated row type, so unwrap inside the branch.
        return when (type) {
            ArtworkType.ALBUM -> queries.selectAlbumThumbUrlById(id).executeAsOneOrNull()?.thumbUrl
            ArtworkType.ARTIST -> queries.selectArtistThumbUrlById(id).executeAsOneOrNull()?.thumbUrl
            ArtworkType.PLAYLIST -> queries.selectPlaylistThumbUrlById(id).executeAsOneOrNull()?.thumbUrl
            ArtworkType.TRACK -> queries.selectTrackThumbUrlById(id).executeAsOneOrNull()?.thumbUrl
        }
    }

    /** Derived from the remote URL so the same artwork is stored once. */
    private fun cacheFileName(thumbUrl: String): String =
        cachedArtworkPathForUrl(thumbUrl).substringAfterLast('/')
}
