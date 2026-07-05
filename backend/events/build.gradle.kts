plugins {
    id("phoebe.backend")
}

application {
    mainClass.set("com.phoebe.app.backend.events.MainKt")
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    testImplementation(libs.ktor.client.mock)
}
