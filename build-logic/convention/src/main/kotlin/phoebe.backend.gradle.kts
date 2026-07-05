import phoebe.libs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(22)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_22)
    }
}

dependencies {
    implementation(libs.findLibrary("coroutines-core").get())
    implementation(libs.findLibrary("serialization-json").get())
    implementation(libs.findLibrary("ktor-server-core").get())
    implementation(libs.findLibrary("ktor-server-netty").get())
    implementation(libs.findLibrary("ktor-server-content-negotiation").get())
    implementation(libs.findLibrary("ktor-server-cors").get())
    implementation(libs.findLibrary("ktor-server-status-pages").get())
    implementation(libs.findLibrary("ktor-serialization-json").get())
    runtimeOnly(libs.findLibrary("slf4j-simple").get())

    testImplementation(kotlin("test"))
    testImplementation(libs.findLibrary("coroutines-test").get())
    testImplementation(libs.findLibrary("ktor-server-test-host").get())
}
