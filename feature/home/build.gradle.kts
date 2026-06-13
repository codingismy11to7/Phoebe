plugins {
    id("phoebe.feature")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":data:catalog"))
                api(project(":data:play-history"))
                implementation(project(":ui:media"))
            }
        }
    }
}
