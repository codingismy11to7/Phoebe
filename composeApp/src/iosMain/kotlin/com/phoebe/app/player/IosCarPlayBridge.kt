package com.phoebe.app.player

import com.phoebe.app.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object IosCarPlayBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playTracks: ((List<Track>, Int) -> Unit)? = null

    fun rootParentId(): String = BrowseMediaIds.ROOT

    fun bindPlayback(handler: (List<Track>, Int) -> Unit) {
        playTracks = handler
    }

    fun fetchChildren(parentId: String, onResult: (List<CarPlayBrowseItem>) -> Unit) {
        val source = IosPlaybackRuntime.browseSource()
        if (source == null) {
            onResult(emptyList())
            return
        }
        scope.launch {
            val items = runCatching { source.getBrowseItems(parentId) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                onResult(items)
            }
        }
    }

    fun playMediaId(mediaId: String) {
        val source = IosPlaybackRuntime.browseSource() ?: return
        val handler = playTracks ?: return
        scope.launch {
            val tracks = runCatching { source.expandForPlayback(mediaId) }.getOrDefault(emptyList())
            if (tracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    handler(tracks, 0)
                }
            }
        }
    }
}
