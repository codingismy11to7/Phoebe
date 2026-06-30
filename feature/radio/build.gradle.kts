import java.util.Properties

plugins {
    id("phoebe.feature")
}

fun localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.isFile) return null
    val properties = Properties()
    file.inputStream().use(properties::load)
    return properties.getProperty(name)?.takeIf { it.isNotBlank() }
}

fun secretProperty(name: String, envName: String) =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(envName))
        .orElse(providers.provider { localProperty(name).orEmpty() })
        .map { it.trim() }

val googleMapsApiKey = secretProperty("phoebe.googleMaps.apiKey", "PHOEBE_GOOGLE_MAPS_API_KEY")
val googleMapsWebApiKey = secretProperty("phoebe.googleMaps.webApiKey", "PHOEBE_GOOGLE_MAPS_WEB_API_KEY")
val googleMapsAndroidApiKey = secretProperty("phoebe.googleMaps.androidApiKey", "PHOEBE_GOOGLE_MAPS_ANDROID_API_KEY")
val googleMapsIosApiKey = secretProperty("phoebe.googleMaps.iosApiKey", "PHOEBE_GOOGLE_MAPS_IOS_API_KEY")
val googleMapsDesktopApiKey = secretProperty("phoebe.googleMaps.desktopApiKey", "PHOEBE_GOOGLE_MAPS_DESKTOP_API_KEY")

val radioMapConfigOutput = layout.buildDirectory.dir("generated/radioMapConfig/kotlin")
val generateRadioMapConfig = tasks.register("generateRadioMapConfig") {
    val outputDir = radioMapConfigOutput
    inputs.property("googleMapsApiKey", googleMapsApiKey)
    inputs.property("googleMapsWebApiKey", googleMapsWebApiKey)
    inputs.property("googleMapsAndroidApiKey", googleMapsAndroidApiKey)
    inputs.property("googleMapsIosApiKey", googleMapsIosApiKey)
    inputs.property("googleMapsDesktopApiKey", googleMapsDesktopApiKey)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/phoebe/app/feature/radio/RadioMapBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.phoebe.app.feature.radio

            internal object RadioMapBuildConfig {
                const val googleMapsApiKey: String = "${googleMapsApiKey.get().escapeKotlin()}"
                const val googleMapsWebApiKey: String = "${googleMapsWebApiKey.get().escapeKotlin()}"
                const val googleMapsAndroidApiKey: String = "${googleMapsAndroidApiKey.get().escapeKotlin()}"
                const val googleMapsIosApiKey: String = "${googleMapsIosApiKey.get().escapeKotlin()}"
                const val googleMapsDesktopApiKey: String = "${googleMapsDesktopApiKey.get().escapeKotlin()}"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(radioMapConfigOutput)
            dependencies {
                implementation(project(":feature:library"))
                implementation(project(":ui:media"))
            }
        }
        androidMain {
            dependencies {
                implementation(libs.play.services.maps)
                implementation(libs.android.maps.utils)
                implementation("com.google.android.gms:play-services-base:18.10.0")
            }
        }
        desktopMain {
            dependencies {
                implementation(project(":core:platform"))
                implementation("me.friwi:jcefmaven:146.0.10")
            }
        }
        iosMain {
            dependencies {
                implementation(project(":playback"))
            }
        }
    }
}

tasks.configureEach {
    val compileUsesCommonMainSources =
        name.startsWith("compile") &&
            (name.contains("Kotlin") || name.startsWith("compileAndroid"))
    if (compileUsesCommonMainSources) {
        dependsOn(generateRadioMapConfig)
    }
}

fun String.escapeKotlin(): String =
    buildString(length) {
        this@escapeKotlin.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
