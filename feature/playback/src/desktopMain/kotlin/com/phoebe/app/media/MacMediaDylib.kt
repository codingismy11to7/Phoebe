package com.phoebe.app.media

import java.io.File

internal fun loadMacMediaDylib(): Boolean {
    val prop = System.getProperty("phoebe.mediakeys.lib")?.trim()?.takeIf { it.isNotEmpty() }
    val appResourcesDir = System.getProperty("compose.application.resources.dir")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val candidates = buildList {
        if (prop != null) add(File(prop))
        if (appResourcesDir != null) add(File(appResourcesDir, "libPhoebeMediaKeys.dylib"))
        val ud = System.getProperty("user.dir") ?: return@buildList
        add(File(ud, "composeApp/build/native/macos/libPhoebeMediaKeys.dylib"))
        add(File(ud, "build/native/macos/libPhoebeMediaKeys.dylib"))
    }.distinctBy { it.absolutePath }
    for (f in candidates) {
        if (f.isFile) {
            val loaded = try {
                System.load(f.absolutePath)
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
            if (loaded) return true
        }
    }
    return false
}
