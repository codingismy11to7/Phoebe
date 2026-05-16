package com.phoebe.app.data.db

import com.phoebe.app.db.PhoebeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun PhoebeDatabase.clearAllAppData() = withContext(Dispatchers.Default) {
    transaction {
        downloadsQueries.clearAll()
        playHistoryQueries.clearAll()
        lyricsQueries.clear()
        libraryPrefsQueries.clear()
        mediaSourcesQueries.clear()
        sessionQueries.clear()

        catalogQueries.clearTrackParents()
        catalogQueries.clearTracks()
        catalogQueries.clearCollectionTags()
        catalogQueries.clearCollectionValues()
        catalogQueries.clearCollectionValueLoads()
        catalogQueries.clearArtists()
        catalogQueries.clearAlbums()
        catalogQueries.clearPlaylists()
        catalogQueries.clearLocalFileMetadataCache()
    }
}
