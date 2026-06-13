plugins {
    id("phoebe.ui")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":ui:media"))
            }
        }
    }
}
