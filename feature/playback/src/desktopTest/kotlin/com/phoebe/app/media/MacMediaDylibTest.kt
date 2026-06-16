package com.phoebe.app.media

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MacMediaDylibTest {
    @Test
    fun candidatesPreferExplicitOverrideThenPackagedArm64Resource() {
        val candidates = macMediaDylibCandidates(
            prop = "/override/libPhoebeMediaKeys.dylib",
            appResourcesDir = "/app/resources",
            userDir = "/repo",
            osArch = "aarch64",
        )

        assertEquals(
            listOf(
                File("/override/libPhoebeMediaKeys.dylib"),
                File("/app/resources/macos-arm64/libPhoebeMediaKeys.dylib"),
                File("/app/resources/libPhoebeMediaKeys.dylib"),
                File("/repo/composeApp/build/native/macos/libPhoebeMediaKeys.dylib"),
                File("/repo/build/native/macos/libPhoebeMediaKeys.dylib"),
            ),
            candidates,
        )
    }

    @Test
    fun candidatesUsePackagedX64ResourceForIntelArchitectures() {
        assertEquals(
            File("/app/resources/macos-x64/libPhoebeMediaKeys.dylib"),
            macMediaDylibCandidates(
                prop = null,
                appResourcesDir = "/app/resources",
                userDir = null,
                osArch = "x86_64",
            ).first(),
        )

        assertEquals(
            File("/app/resources/macos-x64/libPhoebeMediaKeys.dylib"),
            macMediaDylibCandidates(
                prop = null,
                appResourcesDir = "/app/resources",
                userDir = null,
                osArch = "amd64",
            ).first(),
        )
    }
}
