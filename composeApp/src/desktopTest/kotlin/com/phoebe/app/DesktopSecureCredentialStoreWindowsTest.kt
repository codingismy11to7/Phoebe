package com.phoebe.app

import com.phoebe.app.platform.SecureCredentialKey
import com.phoebe.app.platform.createSecureCredentialStore
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class DesktopSecureCredentialStoreWindowsTest {
    @Test
    fun listenBrainzTokenRoundTripInWindowsCredentialManager() = runTest {
        val os = System.getProperty("os.name").lowercase(Locale.US)
        assumeTrue("win" in os)

        val store = createSecureCredentialStore()
        assumeTrue(store.availability.canWrite)

        val key = SecureCredentialKey.ListenBrainzUserToken
        store.delete(key)
        try {
            store.write(key, "desktop-roundtrip-token")
            assertEquals("desktop-roundtrip-token", store.read(key))
        } finally {
            store.delete(key)
        }
    }
}
