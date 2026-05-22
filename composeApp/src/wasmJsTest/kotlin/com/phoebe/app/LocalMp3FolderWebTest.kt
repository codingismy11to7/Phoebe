@file:OptIn(ExperimentalWasmJsInterop::class)

package com.phoebe.app

import com.phoebe.app.domain.LocalFolderMediaSourceConfig
import com.phoebe.app.domain.Track
import com.phoebe.app.sources.LocalFolderCatalogBuilder
import com.phoebe.app.sources.LocalLibraryIO
import com.phoebe.app.sources.resolveWebLocalAudioUri
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMp3FolderWebTest {

    @Test
    fun testLocalFolderUriIndexesMp3FilesOnWasm() = runTest {
        val snapshot = LocalFolderCatalogBuilder.build(
            LocalFolderMediaSourceConfig(
                id = "web-test",
                rootUri = "phoebe-test://music?files=alpha.mp3|nested/beta.mp3|notes.txt",
                label = "Web MP3s",
                enabled = true,
            ),
        )

        val tracks = snapshot.tracksByParent.values.flatten()
        assertEquals(listOf("alpha", "beta"), tracks.map { it.title }.sorted())
        assertTrue(tracks.any { it.localUri?.endsWith("alpha.mp3") == true })
    }

    @Test
    fun webLocalFileExistsAndPlaybackUsesLocalUri() = runTest {
        val uri = "phoebe-test://music/alpha.mp3"
        assertTrue(LocalLibraryIO.fileExists(uri))

        val track = Track(
            id = "local:alpha",
            title = "alpha",
            artist = "Web test files",
            album = "Web MP3 folder",
            durationMs = 0L,
            streamUrl = "https://stream.example/alpha",
            downloadUrl = "",
            localUri = uri,
        )
        val player = RecordingAudioPlayer()
        player.play(listOf(track), 0)

        assertEquals(uri, player.lastUri)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun browserPickedFolderIndexesRegisteredFiles() = runTest {
        val rootUri = seedBrowserLocalFolder()
        val snapshot = LocalFolderCatalogBuilder.build(
            LocalFolderMediaSourceConfig(
                id = "web-picked",
                rootUri = rootUri,
                label = "Picked MP3s",
                enabled = true,
            ),
        )

        val tracks = snapshot.tracksByParent.values.flatten().sortedBy { it.title }
        assertEquals(listOf("alpha", "beta"), tracks.map { it.title })
        assertEquals(listOf("Web Artist"), snapshot.artists.map { it.title })
        assertEquals(listOf("Web Album"), snapshot.albums.map { it.title })
        assertTrue(tracks.all { it.artist == "Web Artist" && it.album == "Web Album" })
        assertTrue(tracks.all { it.localUri?.startsWith("phoebe-web-file://web-test-folder/") == true })
        assertTrue(tracks.all { LocalLibraryIO.fileExists(it.localUri.orEmpty()) })
        assertTrue(resolveWebLocalAudioUri(tracks.first().localUri.orEmpty()).startsWith("blob:"))
        assertEquals("[00:01.00]hello", LocalLibraryIO.readLyrics(tracks.first().localUri.orEmpty()))
    }
}

@JsFun(
    """
    () => {
        const store = globalThis.__phoebeLocalFileStore ||
            (globalThis.__phoebeLocalFileStore = { folders: new Map(), files: new Map() });
        const id = "web-test-folder";
        const folderLabel = "Picked Music";
        const rootUri = "phoebe-web-folder://" + id + "/" + encodeURIComponent(folderLabel);
        for (const key of Array.from(store.files.keys())) {
            if (String(key).startsWith("phoebe-web-file://" + id + "/")) {
                store.files.delete(key);
            }
        }
        const encodeText = (text) => new TextEncoder().encode(text);
        const makeFrame = (id, text) => {
            const textBytes = encodeText(text);
            const payload = new Uint8Array(1 + textBytes.length);
            payload[0] = 3;
            payload.set(textBytes, 1);
            const header = new Uint8Array(10);
            header[0] = id.charCodeAt(0);
            header[1] = id.charCodeAt(1);
            header[2] = id.charCodeAt(2);
            header[3] = id.charCodeAt(3);
            const size = payload.length;
            header[4] = (size >>> 24) & 0xff;
            header[5] = (size >>> 16) & 0xff;
            header[6] = (size >>> 8) & 0xff;
            header[7] = size & 0xff;
            const out = new Uint8Array(header.length + payload.length);
            out.set(header, 0);
            out.set(payload, header.length);
            return out;
        };
        const concat = (arrays) => {
            const total = arrays.reduce((sum, bytes) => sum + bytes.length, 0);
            const out = new Uint8Array(total);
            let offset = 0;
            for (const bytes of arrays) {
                out.set(bytes, offset);
                offset += bytes.length;
            }
            return out;
        };
        const taggedMp3Bytes = (title) => {
            const frames = concat([
                makeFrame("TIT2", title),
                makeFrame("TPE1", "Web Artist"),
                makeFrame("TALB", "Web Album")
            ]);
            const header = new Uint8Array(10);
            header[0] = 0x49;
            header[1] = 0x44;
            header[2] = 0x33;
            header[3] = 3;
            const size = frames.length;
            header[6] = (size >>> 21) & 0x7f;
            header[7] = (size >>> 14) & 0x7f;
            header[8] = (size >>> 7) & 0x7f;
            header[9] = size & 0x7f;
            return concat([header, frames, new Uint8Array([0xff, 0xfb, 0x90, 0x64])]);
        };
        const makeAudio = (relativePath, type, title) => {
            const name = relativePath.split("/").pop();
            const ext = name.includes(".") ? name.split(".").pop().toLowerCase() : "";
            const parentPath = relativePath.split("/").slice(0, -1).join("/");
            const encodedPath = relativePath.split("/").map(encodeURIComponent).join("/");
            const file = new File([taggedMp3Bytes(title)], name, { type, lastModified: 1234 });
            const uri = "phoebe-web-file://" + id + "/" + encodedPath;
            const stored = { file, folderId: id, folderLabel, uri, objectUrl: null, relativePath, name, parentPath, ext };
            store.files.set(uri, stored);
            return stored;
        };
        const files = [
            makeAudio("alpha.mp3", "audio/mpeg", "alpha"),
            makeAudio("nested/beta.mp3", "audio/mpeg", "beta")
        ];
        const textFiles = new Map();
        textFiles.set("alpha.lrc", {
            relativePath: "alpha.lrc",
            file: new File(["[00:01.00]hello"], "alpha.lrc", { type: "text/plain", lastModified: 1235 })
        });
        store.folders.set(id, { id, rootUri, label: folderLabel, files, textFiles });
        return rootUri;
    }
    """,
)
private external fun seedBrowserLocalFolder(): String
