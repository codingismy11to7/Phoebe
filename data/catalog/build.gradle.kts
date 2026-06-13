plugins {
    id("phoebe.data")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
                implementation(project(":data:artwork"))
                implementation(project(":data:database"))
                implementation(project(":data:local-media"))
                implementation(project(":data:network"))
                implementation(project(":data:playlists"))
                implementation(project(":data:session"))
                implementation(project(":data:settings"))
                implementation(project(":data:providers:plex"))
                implementation(project(":data:providers:jellyfin"))
                implementation(project(":data:providers:subsonic"))
                implementation(project(":data:providers:musicassistant"))
                implementation(libs.ktor.client.core)
                implementation(libs.sqldelight.async.extensions)
                implementation(libs.sqldelight.coroutines.extensions)
            }
        }
        commonTest {
            kotlin.srcDir("$rootDir/test-support/network/kotlin")
            dependencies {
                implementation(libs.coroutines.test)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.serialization.json)
            }
        }
        desktopTest {
            kotlin.srcDir("$rootDir/test-support/database/desktop/kotlin")
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}
