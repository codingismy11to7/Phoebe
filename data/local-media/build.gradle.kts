plugins {
    id("phoebe.compose.library")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
                implementation(project(":data:database"))
                implementation(project(":domain"))
                implementation(libs.serialization.json)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.documentfile)
            }
        }
        desktopMain {
            dependencies {
                implementation(libs.jaudiotagger)
            }
        }
    }
}
