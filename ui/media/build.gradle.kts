plugins {
    id("phoebe.ui")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
                implementation(project(":data:artwork"))
                implementation(project(":data:play-history"))
                implementation(project(":data:playlists"))
                implementation(project(":data:providers:jellyfin"))
                implementation(project(":domain"))
                implementation(project(":ui:core"))
                implementation(libs.ktor.client.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.coroutines.test)
            }
        }
        desktopTest {
            dependencies {
                implementation(libs.compose.ui.test.junit4)
            }
        }
    }
}
