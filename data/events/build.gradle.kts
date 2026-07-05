import java.util.Properties

plugins {
    id("phoebe.data")
}

fun localProperty(name: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.isFile) return null
    val properties = Properties()
    file.inputStream().use(properties::load)
    return properties.getProperty(name)?.takeIf { it.isNotBlank() }
}

fun configProperty(name: String, envName: String) =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(envName))
        .orElse(providers.provider { localProperty(name).orEmpty() })
        .map { it.trim().trimEnd('/') }

val eventsBackendUrl = configProperty("phoebe.events.backendUrl", "PHOEBE_EVENTS_BACKEND_URL")
val eventsConfigOutput = layout.buildDirectory.dir("generated/eventsConfig/kotlin")

val generateEventsBuildConfig = tasks.register("generateEventsBuildConfig") {
    val outputDir = eventsConfigOutput
    inputs.property("eventsBackendUrl", eventsBackendUrl)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/phoebe/app/data/EventsBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.phoebe.app.data

            internal object EventsBuildConfig {
                const val productionBackendUrl: String = "${eventsBackendUrl.get().escapeKotlin()}"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(eventsConfigOutput)
            dependencies {
                implementation(project(":core:platform"))
                implementation(project(":data:network"))
                implementation(project(":data:settings"))
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
        commonTest {
            kotlin.srcDir("$rootDir/test-support/network/kotlin")
            dependencies {
                implementation(libs.coroutines.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
    }
}

tasks.configureEach {
    val compileUsesCommonMainSources =
        name.startsWith("compile") &&
            (name.contains("Kotlin") || name.startsWith("compileAndroid"))
    if (compileUsesCommonMainSources) {
        dependsOn(generateEventsBuildConfig)
    }
}

private fun String.escapeKotlin(): String =
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
