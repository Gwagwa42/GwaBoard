// Enable version catalog access inside buildSrc so convention plugins
// can reference libs.* dependency coordinates.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
