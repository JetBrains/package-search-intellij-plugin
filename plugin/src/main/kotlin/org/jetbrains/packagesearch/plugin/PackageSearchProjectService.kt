@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.application
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.packagesearch.client.PackageSearchRemoteApiClient
import org.jetbrains.packagesearch.client.PackageSearchEndpoints
import org.jetbrains.packagesearch.client.buildUrl
import org.jetbrains.packagesearch.plugin.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.nitrite.*
import org.jetbrains.packagesearch.plugin.utils.*
import kotlin.time.Duration.Companion.days

@Service(Level.PROJECT)
class PackageSearchProjectService(
    private val project: Project,
    coroutineScope: CoroutineScope,
) {

    @Service(Level.APP)
    class ApiClientService {
        val client = PackageSearchRemoteApiClient(
            endpoints = application.service<ApiEndpointsService>().endpoints,
        )
    }

    @Service(Level.APP)
    class ApiEndpointsService {
        val endpoints = object : PackageSearchEndpoints {

            fun buildPkgsUrl(path: String) = buildUrl {
                protocol = URLProtocol.HTTP
                host = "localhost"
                encodedPath = "/api/v3/$path"
            }

            override val knownRepositories = buildPkgsUrl("known-repositories")
            override val packageInfoByIds = buildPkgsUrl("package-info-by-ids")
            override val packageInfoByIdHashes = buildPkgsUrl("package-info-by-id-hashes")
            override val searchPackages = buildPkgsUrl("search-packages")
            override val mavenPackageInfoByFileHash = buildPkgsUrl("maven-package-info-by-file-hash")
        }
    }

    val knownRepositoriesStateFlow =
        interval(1.days) {
            getRepositories(
                repoCache = application.service<PackageSearchCaches>().getRepositoryCache(),
                apiClient = application.service<ApiClientService>().client
            )
        }
            .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), emptyMap())

    val modules = project
        .getNativeModulesStateFlow(coroutineScope)
        .zip(PackageSearchModuleTransformer.extensionsFlow) { nativeModules, transformerExtensions ->
            windowedBuilderContext { context ->
                transformerExtensions.flatMap { transformer ->
                    nativeModules.map { module ->
                        transformer.buildModule(context, module)
                    }
                }
            }
        }
        .flatMapLatest { combine(it) { it.filterNotNull() } }
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), emptyList())

    private suspend fun <T> windowedBuilderContext(
        action: suspend CoroutineScope.(context: PackageSearchModuleBuilderContext) -> T,
    ): T = windowedBuilderContext(
        project = project,
        knownRepositories = knownRepositoriesStateFlow.value,
        packagesCache = application.service<PackageSearchCaches>().getApiPackageCache(),
        action = action
    )
}
