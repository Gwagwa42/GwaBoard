import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for modules that use NDK/JNI (llama.cpp integration).
 *
 * Pins the NDK version and configures CMake for native C++ builds.
 * This plugin applies AndroidLibraryConventions first, then layers
 * the NDK-specific configuration on top.
 */
class NdkJniConventions : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        // Apply library conventions as the base
        pluginManager.apply(AndroidLibraryConventions::class.java)

        extensions.configure<LibraryExtension> {
            val catalog = project.versionCatalog()

            ndkVersion = catalog.findVersion("ndk").get().requiredVersion

            defaultConfig {
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

            externalNativeBuild {
                cmake {
                    version = catalog.findVersion("cmake").get().requiredVersion
                    path = file("src/main/cpp/CMakeLists.txt")
                }
            }
        }
    }
}
