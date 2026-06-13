plugins {
    id("phoebe.feature")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
            }
        }
    }
}
