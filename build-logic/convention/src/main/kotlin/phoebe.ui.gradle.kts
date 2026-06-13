import phoebe.libs

plugins {
    id("phoebe.compose.library")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":domain"))
                implementation(libs.findLibrary("coroutines-core").get())
            }
        }
    }
}
