package com.phoebe.app

import com.phoebe.app.sources.LocalLibraryIO
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals

class LocalLyricsDesktopTest {
    @Test
    fun readsSidecarLyricsNextToLocalAudioFile() = runTest {
        val dir = Files.createTempDirectory("phoebe-lyrics-test")
        val audio = dir.resolve("song.mp3")
        val lrc = dir.resolve("song.lrc")
        Files.write(audio, byteArrayOf(0))
        Files.writeString(lrc, "[00:01.00] Hello")

        assertEquals("[00:01.00] Hello", LocalLibraryIO.readLyrics(audio.toUri().toString()))
    }
}
