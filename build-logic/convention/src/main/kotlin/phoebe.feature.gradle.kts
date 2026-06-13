import phoebe.libs

plugins {
    id("phoebe.compose.library")
    id("phoebe.metro")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":domain"))
                implementation(project(":navigation"))
                implementation(project(":ui:core"))
                implementation(libs.findLibrary("coroutines-core").get())
                implementation(libs.findLibrary("lifecycle-viewmodel").get())
                implementation(libs.findLibrary("lifecycle-viewmodel-compose").get())
                implementation(libs.findLibrary("lifecycle-runtime-compose").get())
            }
        }
        named("commonTest") {
            dependencies {
                implementation(libs.findLibrary("coroutines-test").get())
            }
        }
    }
}
