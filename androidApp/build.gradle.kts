val phoebeVersionName = providers.gradleProperty("phoebe.versionName")
    .orElse(providers.environmentVariable("PHOEBE_VERSION_NAME"))
    .orElse("1.0.0")

val phoebeVersionCode = providers.gradleProperty("phoebe.versionCode")
    .orElse(providers.environmentVariable("PHOEBE_VERSION_CODE"))
    .map(String::toInt)
    .orElse(1)

fun providerValue(name: String, envName: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(envName)).orNull

plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.phoebe.androidapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phoebe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = phoebeVersionCode.get()
        versionName = phoebeVersionName.get()
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    val releaseStoreFile = providerValue("phoebe.android.signing.storeFile", "PHOEBE_ANDROID_SIGNING_STORE_FILE")
    val releaseStorePassword = providerValue("phoebe.android.signing.storePassword", "PHOEBE_ANDROID_SIGNING_STORE_PASSWORD")
    val releaseKeyAlias = providerValue("phoebe.android.signing.keyAlias", "PHOEBE_ANDROID_SIGNING_KEY_ALIAS")
    val releaseKeyPassword = providerValue("phoebe.android.signing.keyPassword", "PHOEBE_ANDROID_SIGNING_KEY_PASSWORD")

    val hasReleaseSigning =
        releaseStoreFile != null &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
}
