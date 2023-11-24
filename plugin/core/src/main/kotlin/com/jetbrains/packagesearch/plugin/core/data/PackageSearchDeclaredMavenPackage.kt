package com.jetbrains.packagesearch.plugin.core.data

import org.jetbrains.packagesearch.api.v3.ApiMavenPackage

interface PackageSearchDeclaredMavenPackage : PackageSearchDeclaredPackage {
    val groupId: String
    val artifactId: String
    override val remoteInfo: ApiMavenPackage?

    override val coordinates: String
        get() = "$groupId:$artifactId"

    override val displayName: String
        get() = remoteInfo?.name ?: artifactId
}