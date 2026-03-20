plugins {
    id("AndroidAppConventions")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.gwaboard.keyboard"

    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "dev.gwaboard.keyboard"
        versionCode = 1
        versionName = "0.1.0"

        // NDK/CMake configuration for llama.cpp native build
        externalNativeBuild {
            cmake {
                // Optimize for Tensor G3 (ARMv9) while keeping ARMv8 compatibility
                arguments("-DANDROID_STL=c++_shared")
                cppFlags("-std=c++17", "-O3")
                abiFilters("arm64-v8a")
            }
        }

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Point to the CMakeLists.txt that builds llama.cpp + JNI bridge
    externalNativeBuild {
        cmake {
            version = libs.versions.cmake.get()
            path = file("src/main/cpp/CMakeLists.txt")
        }
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
