import phoebe.libs

plugins {
    id("phoebe.kmp.library")
    kotlin("plugin.serialization")
    id("phoebe.metro")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":domain"))
                implementation(libs.findLibrary("coroutines-core").get())
                implementation(libs.findLibrary("serialization-json").get())
            }
        }
        named("commonTest") {
            dependencies {
                implementation(libs.findLibrary("coroutines-test").get())
            }
        }
    }
}
