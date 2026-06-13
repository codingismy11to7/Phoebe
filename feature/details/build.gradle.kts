plugins {
    id("phoebe.feature")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
                implementation(project(":data:catalog"))
                implementation(project(":data:local-media"))
                implementation(project(":data:play-history"))
                implementation(project(":data:playlists"))
                implementation(project(":feature:library"))
                implementation(project(":ui:media"))
                implementation(libs.ktor.client.core)
            }
        }
    }
}
