package org.jetbrains.packagesearch.client

import io.ktor.http.*

interface PackageSearchEndpoints {
    val knownRepositories: Url
    val packageInfoByIds: Url
    val packageInfoByIdHashes: Url
    val searchPackages: Url
    val getScmsByUrl: Url
    val mavenPackageInfoByFileHash: Url
}