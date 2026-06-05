package com.phoebe.app.updates

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopUpdateInstallerCommandTest {
    @Test
    fun windowsHelperRunsMsiAndRelaunchesWhenPossible() {
        val script = windowsInstallerHelperScript(
            msiPath = "C:\\Users\\Ada\\Downloads\\Phoebe 1.2.3.msi",
            relaunchCommand = "C:\\Program Files\\Phoebe\\Phoebe.exe",
        )

        assertTrue(script.contains("msiexec /i \"C:\\Users\\Ada\\Downloads\\Phoebe 1.2.3.msi\" /passive /norestart"))
        assertTrue(script.contains("start \"\" \"C:\\Program Files\\Phoebe\\Phoebe.exe\""))
    }

    @Test
    fun macHelperOpensPkgInstallerAndRelaunchesAfterItCloses() {
        val script = macPkgInstallerHelperScript("/Users/ada/Downloads/Phoebe 1.2.3.pkg")

        assertTrue(script.contains("open -W '/Users/ada/Downloads/Phoebe 1.2.3.pkg'"))
        assertTrue(script.contains("open -b com.phoebe.app || open -a Phoebe"))
    }

    @Test
    fun linuxFlatpakHelperEscapesToHostWhenRunningInsideFlatpak() {
        val script = linuxInstallerHelperScript(
            filePath = "/tmp/Phoebe 1.2.3.flatpak",
            flatpak = true,
            insideFlatpak = true,
            relaunchCommand = "phoebe",
        )

        assertTrue(script.contains("flatpak-spawn --host sh -c"))
        assertTrue(script.contains("flatpak install --user -y"))
        assertTrue(script.contains("flatpak install -y"))
    }

    @Test
    fun linuxDebHelperUsesPkexecDpkgWithRepairFallback() {
        val script = linuxInstallerHelperScript(
            filePath = "/tmp/Phoebe 1.2.3.deb",
            flatpak = false,
            insideFlatpak = false,
            relaunchCommand = null,
        )

        assertTrue(script.contains("pkexec sh -c"))
        assertTrue(script.contains("dpkg -i"))
        assertTrue(script.contains("apt-get install -f -y"))
        assertTrue(script.contains("phoebe >/dev/null 2>&1 &"))
    }
}
