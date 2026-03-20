plugins {
    id("android-library-conventions")
}

android {
    namespace = "dev.gwaboard.shared.ipc"
}

dependencies {
    // Shared modules
    api(project(":shared-models"))
    implementation(project(":shared-crypto"))

    // AndroidX Core (for PackageManager, ContentResolver utilities)
    implementation(libs.androidx.core.ktx)

    // Testing
    testImplementation(libs.bundles.testing)
    androidTestImplementation(libs.bundles.testing.android)
}
