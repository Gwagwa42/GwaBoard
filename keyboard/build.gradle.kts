plugins {
    id("AndroidAppConventions")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.gwaboard.keyboard"

    defaultConfig {
        applicationId = "dev.gwaboard.keyboard"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Shared modules
    implementation(project(":shared-models"))
    implementation(project(":shared-ipc"))
    implementation(project(":shared-crypto"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // Compose (for future suggestion bar UI)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)

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
