plugins {
    id("phoebe.data")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:platform"))
                implementation(project(":data:network"))
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
        commonTest {
            kotlin.srcDir("$rootDir/test-support/network/kotlin")
            kotlin.srcDir("$rootDir/test-support/plex/kotlin")
            dependencies {
                implementation(libs.coroutines.test)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.serialization.json)
            }
        }
    }
}
