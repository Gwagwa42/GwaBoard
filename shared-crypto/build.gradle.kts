plugins {
    id("AndroidLibraryConventions")
}

android {
    namespace = "dev.gwaboard.shared.crypto"
}

dependencies {
    // AndroidX Security — used alongside direct Keystore APIs for future compatibility
    implementation(libs.androidx.security.crypto)

    // Testing
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
}
