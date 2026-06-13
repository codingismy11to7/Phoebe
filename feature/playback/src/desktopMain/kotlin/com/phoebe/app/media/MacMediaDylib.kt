package com.phoebe.app.media

import java.io.File

internal fun loadMacMediaDylib(): Boolean {
    val prop = System.getProperty("phoebe.mediakeys.lib")?.trim()?.takeIf { it.isNotEmpty() }
    val candidates = buildList {
        if (prop != null) add(File(prop))
        val ud = System.getProperty("user.dir") ?: return@buildList
        add(File(ud, "composeApp/build/native/macos/libPhoebeMediaKeys.dylib"))
        add(File(ud, "build/native/macos/libPhoebeMediaKeys.dylib"))
    }
    for (f in candidates) {
        if (f.isFile) {
            return try {
                System.load(f.absolutePath)
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
        }
    }
    return false
}
