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
    implementation(project(":shared-models"))
    implementation(project(":shared-crypto"))

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.coroutines)

    // AndroidX
    implementation(libs.androidx.core.ktx)

    // Testing
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
