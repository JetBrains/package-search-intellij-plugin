package org.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.project.Project
import com.intellij.util.flow.debounceBatch
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchApiPackagesProvider
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.nitrite.PackageSearchApiPackageCache
import java.util.*
import kotlin.time.Duration.Companion.seconds

suspend fun <T> windowedBuilderContext(
    project: Project,
    knownRepositories: Map<String, ApiRepository>,
    packagesCache: PackageSearchApiPackageCache,
    action: suspend CoroutineScope.(context: PackageSearchModuleBuilderContext) -> T,
): T = coroutineScope {
    windowedBuilderContext(
        project = project,
        knownRepositories = knownRepositories,
        packagesCache = packagesCache
    ) {
        action(this@coroutineScope, this)
    }
}

inline fun <T> CoroutineScope.windowedBuilderContext(
    project: Project,
    knownRepositories: Map<String, ApiRepository>,
    packagesCache: PackageSearchApiPackageCache,
    action: PackageSearchModuleBuilderContext.() -> T,
) = WindowedModuleBuilderContext(
    project = project,
    knownRepositories = knownRepositories,
    packagesCache = packagesCache,
    coroutineScope = this,
).run(action)

class WindowedModuleBuilderContext(
    override val project: Project,
    override val knownRepositories: Map<String, ApiRepository>,
    private val packagesCache: PackageSearchApiPackagesProvider,
    coroutineScope: CoroutineScope,
) : PackageSearchModuleBuilderContext {

    private val idRequestsChannel = Channel<Request>()
    private val hashRequestsChannel = Channel<Request>()

    private val idResultsFlow = idRequestsChannel
        .responseFlow(coroutineScope) { packagesCache.getPackageInfoByIds(it) }

    private val hashResultsFlow = hashRequestsChannel
        .responseFlow(coroutineScope) { packagesCache.getPackageInfoByIdHashes(it) }

    override suspend fun getPackageInfoByIds(
        packageIds: Set<String>,
    ): Map<String, ApiPackage> = awaitResponse(packageIds, idRequestsChannel, idResultsFlow)

    override suspend fun getPackageInfoByIdHashes(
        packageIdHashes: Set<String>,
    ) = awaitResponse(packageIdHashes, hashRequestsChannel, hashResultsFlow)

    private fun Channel<Request>.responseFlow(
        coroutineScope: CoroutineScope,
        retrieveFunction: suspend (Set<String>) -> Map<String, ApiPackage>,
    ) = consumeAsFlow()
        .debounceBatch(1.seconds)
        .map { requests ->
            Response(
                ids = requests.map { it.requestId }.toSet(),
                packages = retrieveFunction(requests.flatMap { it.request }.toSet())
            )
        }
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed(), 0)

    private suspend fun awaitResponse(
        packageIds: Set<String>,
        requestChannel: Channel<Request>,
        responseSharedFlow: Flow<Response>,
    ): Map<String, ApiPackage> = coroutineScope {
        val id = UUID.randomUUID().toString()
        val res = async(start = CoroutineStart.UNDISPATCHED) {
            responseSharedFlow
                .filter { id in it.ids }
                .map { it.packages }
                .first()
        }
        requestChannel.send(Request(id, packageIds))
        res.await()
    }
}

private data class Request(
    val requestId: String,
    val request: Set<String>,
)

private data class Response(
    val ids: Set<String>,
    val packages: Map<String, ApiPackage>,
)

