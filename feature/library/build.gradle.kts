plugins {
    id("phoebe.feature")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":data:catalog"))
                implementation(project(":data:play-history"))
                implementation(project(":ui:media"))
            }
        }
        desktopMain {
            dependencies {
                implementation(project(":ui:preview"))
            }
        }
    }
}
