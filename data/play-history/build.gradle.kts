plugins {
    id("phoebe.data")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
                implementation(project(":data:catalog"))
                implementation(project(":data:database"))
                implementation(project(":data:network"))
                implementation(project(":data:providers:plex"))
                implementation(project(":data:providers:jellyfin"))
                implementation(project(":data:providers:subsonic"))
                implementation(libs.sqldelight.async.extensions)
                implementation(libs.sqldelight.coroutines.extensions)
            }
        }
    }
}
