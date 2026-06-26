plugins {
    id("phoebe.ui")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
            }
        }
    }
}
