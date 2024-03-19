package com.jetbrains.packagesearch.plugin.maven

enum class CommonRepositories(val urls: List<String>) {
    MAVEN_CENTRAL(listOf("https://repo1.maven.org/maven2/")),
    MAVEN_CENTRAL_GOOGLE_MIRROR(
        listOf(
            "https://maven-central.storage-download.googleapis.com/maven2",
            "https://maven-central-eu.storage-download.googleapis.com/maven2/",
            "https://maven-central-asia.storage-download.googleapis.com/maven2/"
        )
    )
}