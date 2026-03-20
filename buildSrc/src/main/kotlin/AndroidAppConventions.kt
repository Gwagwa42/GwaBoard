import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for Android application modules (keyboard, companion).
 *
 * Applies consistent SDK versions, Kotlin JVM target, and default
 * configuration across all app modules in the monorepo.
 */
class AndroidAppConventions : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            val catalog = project.versionCatalog()

            compileSdk = catalog.findVersion("compileSdk").get().requiredVersion.toInt()

            defaultConfig {
                minSdk = catalog.findVersion("minSdk").get().requiredVersion.toInt()
                targetSdk = catalog.findVersion("targetSdk").get().requiredVersion.toInt()

                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
            }

            buildFeatures {
                buildConfig = true
            }
        }

        configureKotlinJvmTarget()
    }
}
