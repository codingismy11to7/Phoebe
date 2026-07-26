package com.phoebe.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.phoebe.app.player.AndroidPlaybackRuntime
import com.phoebe.app.player.parseArtworkUri
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Serves catalog artwork to the car's media UI.
 *
 * Android Automotive's ImageFetcher only understands `android.resource://`
 * and `content://` URIs — it has no HTTP stack — so remote artwork URLs are
 * silently ignored. This provider bridges the gap: it resolves a catalog id
 * to bytes inside Phoebe's process, where provider credentials are available,
 * and hands back a file descriptor.
 *
 * Read-only and exported, because MediaBrowser clients are arbitrary
 * processes and a signature permission would exclude the car itself.
 */
class PhoebeArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read-only: $uri")
        val (type, id) = parseArtworkUri(uri) ?: throw FileNotFoundException("Malformed artwork uri: $uri")
        val file = runBlocking {
            runCatching {
                AndroidPlaybackRuntime.ensureArtworkResolver().resolve(type, id)
            }.getOrNull()
        } ?: throw FileNotFoundException("No artwork for $type $id")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = "image/*"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Artwork provider is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Artwork provider is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Artwork provider is read-only")
}
