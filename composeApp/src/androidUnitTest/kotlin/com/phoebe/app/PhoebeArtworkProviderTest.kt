package com.phoebe.app

import android.app.Application
import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PhoebeArtworkProviderTest {
    private val provider = PhoebeArtworkProvider()

    @Test
    fun rejectsWriteModes() {
        assertFailsWith<FileNotFoundException> {
            provider.openFile(Uri.parse("content://com.phoebe.app.artwork/album/1"), "w")
        }
    }

    @Test
    fun rejectsMalformedUri() {
        assertFailsWith<FileNotFoundException> {
            provider.openFile(Uri.parse("content://com.phoebe.app.artwork/album"), "r")
        }
    }

    @Test
    fun rejectsUnknownType() {
        assertFailsWith<FileNotFoundException> {
            provider.openFile(Uri.parse("content://com.phoebe.app.artwork/bogus/1"), "r")
        }
    }
}
