import phoebe.libs

plugins {
    id("phoebe.kmp.library")
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(libs.findLibrary("serialization-json").get())
            }
        }
    }
}
