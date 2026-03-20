import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.gradle.kotlin.dsl.configure

/**
 * Resolve the version catalog named "libs" from any project.
 */
fun Project.versionCatalog(): VersionCatalog =
    extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Configure the Kotlin JVM target to 17, matching the Java compile options
 * set in both app and library convention plugins.
 */
fun Project.configureKotlinJvmTarget() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
