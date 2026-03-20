rootProject.name = "GwaBoard"

// Gradle monorepo modules
include(":shared-models")
include(":shared-ipc")
include(":shared-crypto")
include(":keyboard")
include(":companion")

// FlorisBoard upstream fork (git submodule)
includeBuild("florisboard")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
