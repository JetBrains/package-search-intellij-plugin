dependencyResolutionManagement {
    versionCatalogs {
        create("packageSearchCatalog") {
            from(files("../gradle/packagesearch.versions.toml"))
        }
    }
}