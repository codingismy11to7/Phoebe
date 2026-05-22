package com.phoebe.app

import com.phoebe.app.domain.toLocalFolderDisplayPath
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalFolderDisplayPathTest {

    @Test
    fun fileUriDisplaysDecodedPath() {
        assertEquals(
            "/Users/music/Café Albums",
            "file:///Users/music/Caf%C3%A9%20Albums".toLocalFolderDisplayPath(),
        )
    }

    @Test
    fun androidTreeUriDisplaysReadableTreePath() {
        assertEquals(
            "primary/Music",
            "content://com.android.externalstorage.documents/tree/primary%3AMusic".toLocalFolderDisplayPath(),
        )
    }

    @Test
    fun webFolderDisplaysSelectedFolderLabel() {
        assertEquals(
            "Browser folder: My Albums",
            "phoebe-web-folder://web-id/My%20Albums".toLocalFolderDisplayPath(),
        )
    }
}
