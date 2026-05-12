import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

val javaFxClassifier = when {
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) &&
        System.getProperty("os.arch") == "aarch64" -> "mac-aarch64"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "mac"
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "win"
    System.getProperty("os.arch") == "aarch64" -> "linux-aarch64"
    else -> "linux"
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)
    androidTarget()

    jvm("desktop")

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "phoebe.js"
            }
        }
        binaries.executable()
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting
        val wasmJsMain by getting
        val androidInstrumentedTest by getting
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.async.extensions)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.sqldelight.primitive.adapters)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.coroutines.test)
        }
        desktopTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.serialization.json)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.sqldelight.async.extensions)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.sqldelight.primitive.adapters)
        }
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.coroutines.test)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.rules)
            implementation(libs.androidx.test.ext.junit)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.activity.compose)
            implementation("androidx.compose.ui:ui-test-junit4")
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.serialization.json)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.sqldelight.async.extensions)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.sqldelight.primitive.adapters)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.runtime)
        }
        wasmJsTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.documentfile)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.session)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jnativehook)
            implementation(libs.coroutines.swing)
            implementation(libs.jaudiotagger)
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.sqlite.driver)
            implementation("org.openjfx:javafx-base:${libs.versions.javafx.get()}:$javaFxClassifier")
            implementation("org.openjfx:javafx-graphics:${libs.versions.javafx.get()}:$javaFxClassifier")
            implementation("org.openjfx:javafx-media:${libs.versions.javafx.get()}:$javaFxClassifier")
            implementation("org.openjfx:javafx-swing:${libs.versions.javafx.get()}:$javaFxClassifier")
            // javax.sound.sampled SPI: FLAC / Ogg Vorbis / MP3 decoded streams (JavaFX Media does not support these).
            implementation(libs.soundlibs.mp3spi)
            implementation(libs.soundlibs.vorbisspi)
            implementation(libs.jflac.codec)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.web.worker.driver)
            implementation(npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqldelight.get()))
            implementation(npm("sql.js", "1.8.0"))
            implementation(devNpm("copy-webpack-plugin", "9.1.0"))
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("PhoebeDatabase") {
            packageName.set("com.phoebe.app.db")
            generateAsync.set(true)
        }
    }
}

android {
    namespace = "com.phoebe.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phoebe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

compose.desktop {
    application {
        mainClass = "com.phoebe.app.MainKt"
        val mediaKeysDylibPath =
            layout.buildDirectory.get().asFile.resolve("native/macos/libPhoebeMediaKeys.dylib").absolutePath
        jvmArgs += listOf("-Dphoebe.mediakeys.lib=$mediaKeysDylibPath")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Phoebe"
            packageVersion = "1.0.0"
            val iconsDir = project.layout.projectDirectory.dir("src/desktopMain/resources/icons")
            macOS {
                iconFile.set(iconsDir.file("icon.icns").asFile)
            }
            windows {
                iconFile.set(iconsDir.file("icon.ico").asFile)
            }
            linux {
                iconFile.set(iconsDir.file("icon.png").asFile)
            }
        }
    }
}

dependencies {
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val compileMacMediaKeysNative = tasks.register<Exec>("compileMacMediaKeysNative") {
    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
    val outDir = layout.buildDirectory.dir("native/macos").get().asFile
    val outFile = File(outDir, "libPhoebeMediaKeys.dylib")
    val src = layout.projectDirectory.file("native/macos/MediaKeysBridge.m").asFile
    inputs.file(src)
    outputs.file(outFile)
    doFirst { outDir.mkdirs() }
    val javaHome = System.getProperty("java.home") ?: error("java.home is not set")
    commandLine(
        "clang",
        "-dynamiclib",
        "-fobjc-arc",
        "-framework",
        "Foundation",
        "-framework",
        "MediaPlayer",
        "-I$javaHome/include",
        "-I$javaHome/include/darwin",
        "-mmacosx-version-min=11.0",
        "-o",
        outFile.absolutePath,
        src.absolutePath,
    )
}

tasks.named("compileKotlinDesktop") { dependsOn(compileMacMediaKeysNative) }
