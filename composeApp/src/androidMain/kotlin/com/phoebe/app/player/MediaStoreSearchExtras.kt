package com.phoebe.app.player

/**
 * MediaStore playlist search extras/constants removed from the public SDK surface but still
 * sent by voice/search intents.
 */
internal object MediaStoreSearchExtras {
    const val EXTRA_MEDIA_PLAYLIST: String = "android.media.extra.PLAYLIST"
    const val PLAYLIST_ENTRY_CONTENT_TYPE: String = "vnd.android.cursor.item/audio-playlist"
}
