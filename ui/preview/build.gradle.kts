plugins {
    id("phoebe.ui")
}

extensions.extraProperties.set("phoebeIosTargets", false)

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":ui:media"))
            }
        }
    }
}
