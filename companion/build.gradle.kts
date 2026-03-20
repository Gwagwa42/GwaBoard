plugins {
    id("AndroidAppConventions")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.gwaboard.companion"

    defaultConfig {
        applicationId = "dev.gwaboard.companion"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Shared modules for IPC contract, crypto, and data models
    implementation(project(":shared-models"))
    implementation(project(":shared-ipc"))
    implementation(project(":shared-crypto"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)

    // Activity Compose + Navigation
    implementation(libs.androidx.activity.compose)
    implementation(libs.navigation.compose)

    // Material Icons (extended set for outlined icons)
    implementation(libs.compose.material.icons.extended)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Koin DI
    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // Kotlinx Serialization for profile JSON encoding
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.bundles.testing.android)
}
