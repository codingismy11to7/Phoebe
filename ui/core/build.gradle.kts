plugins {
    id("phoebe.ui")
}

kotlin {
    sourceSets {
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
            }
        }
    }
}
