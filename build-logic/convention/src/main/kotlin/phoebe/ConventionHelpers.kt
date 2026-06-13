package phoebe

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.libraryNamespace(): String {
    val suffix = path
        .removePrefix(":")
        .split(":")
        .joinToString(".") { segment ->
            segment
                .replace(Regex("[^A-Za-z0-9_]"), "_")
                .lowercase()
        }
    return "com.phoebe.$suffix"
}

internal fun Project.configurePhoebeKmp(
    extension: KotlinMultiplatformExtension,
) {
    extensions.configure(BasePluginExtension::class.java) {
        archivesName.set(path.removePrefix(":").replace(":", "-"))
    }

    extension.apply {
        jvmToolchain(17)
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }

        jvm("desktop")

        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs {
            browser()
        }

        iosArm64()
        iosSimulatorArm64()

        sourceSets.apply {
            all {
                languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                languageSettings.optIn("kotlinx.coroutines.FlowPreview")
            }
            named("commonTest") {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }
    }
}
