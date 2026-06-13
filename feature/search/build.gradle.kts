plugins {
    id("phoebe.feature")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":data:catalog"))
                implementation(project(":ui:media"))
            }
        }
    }
}
