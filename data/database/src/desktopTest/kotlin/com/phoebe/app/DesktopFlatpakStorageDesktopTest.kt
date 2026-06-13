package com.phoebe.app

import com.phoebe.app.data.db.desktopDataDirectoryName
import com.phoebe.app.data.db.desktopDatabaseRoot
import com.phoebe.app.data.db.flatpakDesktopDatabaseRoot
import com.phoebe.app.data.db.flatpakSandboxOverride
import com.phoebe.app.data.db.flatpakXdgDataHomeOverride
import com.phoebe.app.data.db.localStorageDirectoryName
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class DesktopFlatpakStorageDesktopTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun clearOverrides() {
        System.clearProperty("phoebe.storage.root")
        flatpakSandboxOverride = null
        flatpakXdgDataHomeOverride = null
    }

    @After
    fun cleanup() {
        System.clearProperty("phoebe.storage.root")
        flatpakSandboxOverride = null
        flatpakXdgDataHomeOverride = null
    }

    @Test
    fun flatpakDesktopDatabaseRootUsesXdgDataHome() {
        val dataHome = temp.newFolder("xdg-data")
        flatpakSandboxOverride = { true }
        flatpakXdgDataHomeOverride = { dataHome.absolutePath }
        assertEquals(
            File(dataHome, localStorageDirectoryName()).canonicalFile,
            flatpakDesktopDatabaseRoot()?.canonicalFile,
        )
        assertEquals(
            File(dataHome, localStorageDirectoryName()).canonicalFile,
            desktopDatabaseRoot().canonicalFile,
        )
    }

    @Test
    fun nonFlatpakDesktopDatabaseRootUsesUserHome() {
        val home = temp.newFolder("home")
        val previousHome = System.getProperty("user.home")
        flatpakSandboxOverride = { false }
        try {
            System.setProperty("user.home", home.absolutePath)
            assertNull(flatpakDesktopDatabaseRoot())
            assertEquals(
                File(home, desktopDataDirectoryName()).canonicalFile,
                desktopDatabaseRoot().canonicalFile,
            )
        } finally {
            System.setProperty("user.home", previousHome)
        }
    }

    @Test
    fun desktopDatabaseRootRecreatesMissingOverrideDirectory() {
        val root = File(temp.root, "deleted-storage-root")
        System.setProperty("phoebe.storage.root", root.absolutePath)

        assertEquals(root.canonicalFile, desktopDatabaseRoot().canonicalFile)
        assertTrue(root.isDirectory)
    }
}
