package com.phoebe.app

import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.sources.LocalFolderCatalogBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertTrue

class LocalFolderCatalogBuilderDesktopTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun indexesMp3FileInFolder() = runTest {
        val root = temp.newFolder("music")
        File(root, "fixture.mp3").writeBytes(byteArrayOf(0x49, 0x44, 0x33)) // minimal ID3-ish header; jaudiotagger may still treat as audio
        val cfg = LocalFolderMediaSourceConfig(
            id = "lf-test",
            rootUri = root.toURI().toString(),
            label = "Test",
            enabled = true,
        )
        val snap = LocalFolderCatalogBuilder.build(cfg)
        assertTrue(snap.albums.isNotEmpty() || snap.tracksByParent.isNotEmpty(), "expected at least one album or track map entry")
    }
}
