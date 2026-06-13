plugins {
    id("phoebe.feature")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":data:local-media"))
                implementation(project(":data:providers:jellyfin"))
            }
        }
    }
}
