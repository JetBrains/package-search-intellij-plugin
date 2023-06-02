package org.jetbrains.packagesearch.plugin.core

import com.intellij.openapi.components.Service
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.packagesearch.client.PackageSearchEndpoints
import org.jetbrains.packagesearch.client.buildUrl
import org.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import org.jetbrains.packagesearch.plugin.core.utils.registryStateFlow

@Service(Service.Level.APP)
class PackageSearchApiEndpointsService(scope: CoroutineScope) {
    val endpoints = object : PackageSearchEndpoints {

        fun buildPkgsUrl(path: String) = buildUrl {
            val isLocal = IntelliJApplication
                .registryStateFlow(scope, "org.jetbrains.packagesearch.localhost")
                .value
            protocol = if (isLocal) URLProtocol.HTTP else URLProtocol.HTTPS
            host = if (isLocal) "localhost" else "packagesearch.jetbrains.com"
            encodedPath = "/api/v3/$path"
            port = if (isLocal) 8081 else URLProtocol.HTTPS.defaultPort
        }

        override val knownRepositories
            get() = buildPkgsUrl("known-repositories")
        override val packageInfoByIds
            get() = buildPkgsUrl("package-info-by-ids")
        override val packageInfoByIdHashes
            get() = buildPkgsUrl("package-info-by-id-hashes")
        override val searchPackages
            get() = buildPkgsUrl("search-packages")
        override val getScmsByUrl
            get() = buildPkgsUrl("scms-by-url")
        override val mavenPackageInfoByFileHash
            get() = buildPkgsUrl("maven-package-info-by-file-hash")
    }
}