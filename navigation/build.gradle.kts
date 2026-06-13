plugins {
    id("phoebe.compose.library")
    id("phoebe.metro")
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
                implementation(project(":domain"))
                implementation(project(":ui:core"))
                implementation(libs.jetbrains.navigation3.ui)
                implementation(libs.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
