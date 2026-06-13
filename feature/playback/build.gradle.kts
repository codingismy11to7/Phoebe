plugins {
    id("phoebe.feature")
}

kotlin {
    sourceSets {
        commonMain {
                dependencies {
                    implementation(project(":core:platform"))
                    implementation(project(":data:listenbrainz"))
                    implementation(project(":playback"))
                    implementation(project(":ui:media"))
                    implementation(libs.haze.blur)
                }
            }
        desktopMain {
            dependencies {
                implementation(libs.jnativehook)
            }
        }
    }
}
