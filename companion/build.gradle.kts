plugins {
    id("AndroidAppConventions")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.gwaboard.companion"

    defaultConfig {
        applicationId = "dev.gwaboard.companion"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    // Shared modules for IPC contract, crypto, and data models
    implementation(project(":shared-models"))
    implementation(project(":shared-ipc"))
    implementation(project(":shared-crypto"))

    // Kotlinx Serialization for profile JSON encoding
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.bundles.testing.android)
}
