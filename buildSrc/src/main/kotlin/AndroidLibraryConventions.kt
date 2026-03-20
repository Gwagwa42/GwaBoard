import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Convention plugin for Android library modules (shared-models, shared-ipc, shared-crypto).
 *
 * Enforces the same SDK versions as app modules and sets up
 * consistent Kotlin compilation options.
 */
class AndroidLibraryConventions : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<LibraryExtension> {
                val catalog = project.versionCatalog()

                compileSdk = catalog.findVersion("compileSdk").get().requiredVersion.toInt()

                defaultConfig {
                    minSdk = catalog.findVersion("minSdk").get().requiredVersion.toInt()

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")
                }

                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
                }
            }

            configureKotlinJvmTarget()

            tasks.withType<Test> {
                useJUnit()
            }
        }
    }
}
