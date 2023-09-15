@file:Suppress("UnstableApiUsage")

plugins {
    id(packageSearchCatalog.plugins.kotlin.jvm)
    alias(packageSearchCatalog.plugins.kotlin.plugin.serialization)
    `build-config`
}

packagesearch {
    publication {
        isEnabled = false
    }
}

dependencies {
    api(projects.apiMock)
    api(packageSearchCatalog.packagesearch.api.client)
}
