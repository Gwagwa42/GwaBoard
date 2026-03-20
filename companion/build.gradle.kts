plugins {
    id("AndroidAppConventions")
    alias(libs.plugins.compose.compiler)
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

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.bundles.testing.android)
}
