package com.phoebe.app

import com.phoebe.app.data.db.desktopDataDirectoryName
import com.phoebe.app.data.db.desktopDatabaseRoot
import com.phoebe.app.data.db.flatpakDesktopDatabaseRoot
import com.phoebe.app.data.db.flatpakXdgDataHomeOverride
import com.phoebe.app.data.db.localStorageDirectoryName
import com.phoebe.app.player.DesktopSandboxPlayback
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopFlatpakStorageDesktopTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun clearOverrides() {
        System.clearProperty("phoebe.storage.root")
        DesktopSandboxPlayback.flatpakSandboxOverride = null
        flatpakXdgDataHomeOverride = null
    }

    @After
    fun cleanup() {
        System.clearProperty("phoebe.storage.root")
        DesktopSandboxPlayback.flatpakSandboxOverride = null
        flatpakXdgDataHomeOverride = null
    }

    @Test
    fun flatpakDesktopDatabaseRootUsesXdgDataHome() {
        val dataHome = temp.newFolder("xdg-data")
        DesktopSandboxPlayback.flatpakSandboxOverride = { true }
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
        DesktopSandboxPlayback.flatpakSandboxOverride = { false }
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
}
