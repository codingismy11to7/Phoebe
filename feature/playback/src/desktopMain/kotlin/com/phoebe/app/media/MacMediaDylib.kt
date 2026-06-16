package com.phoebe.app.media

import java.io.File

internal fun loadMacMediaDylib(): Boolean {
    val prop = System.getProperty("phoebe.mediakeys.lib")?.trim()?.takeIf { it.isNotEmpty() }
    val appResourcesDir = System.getProperty("compose.application.resources.dir")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val candidates = macMediaDylibCandidates(
        prop = prop,
        appResourcesDir = appResourcesDir,
        userDir = System.getProperty("user.dir"),
        osArch = System.getProperty("os.arch"),
    )
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

internal fun macMediaDylibCandidates(
    prop: String?,
    appResourcesDir: String?,
    userDir: String?,
    osArch: String?,
): List<File> = buildList {
    if (prop != null) add(File(prop))
    if (appResourcesDir != null) {
        add(File(appResourcesDir, "${macMediaKeysResourceDirName(osArch)}/libPhoebeMediaKeys.dylib"))
        add(File(appResourcesDir, "libPhoebeMediaKeys.dylib"))
    }
    if (userDir != null) {
        add(File(userDir, "composeApp/build/native/macos/libPhoebeMediaKeys.dylib"))
        add(File(userDir, "build/native/macos/libPhoebeMediaKeys.dylib"))
    }
}.distinctBy { it.absolutePath }

private fun macMediaKeysResourceDirName(osArch: String?): String =
    when (osArch) {
        "aarch64" -> "macos-arm64"
        "x86_64", "amd64" -> "macos-x64"
        else -> "macos"
    }
