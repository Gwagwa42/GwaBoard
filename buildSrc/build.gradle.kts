plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Android Gradle Plugin — required for convention plugins that configure Android modules
    implementation(libs.android.gradle.plugin)
    // Kotlin Gradle Plugin — required for configuring Kotlin compilation options
    implementation(libs.kotlin.gradle.plugin)
}
