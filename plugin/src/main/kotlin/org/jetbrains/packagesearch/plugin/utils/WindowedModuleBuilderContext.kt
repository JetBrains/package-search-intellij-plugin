package org.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchApiPackagesProvider
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineNitrite
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun <T> windowedBuilderContext(
    project: Project,
    knownRepositories: Map<String, ApiRepository>,
    packagesCache: PackageSearchApiPackageCache,
    projectCaches: CoroutineNitrite,
    applicationCaches: CoroutineNitrite,
    action: suspend CoroutineScope.(context: PackageSearchModuleBuilderContext) -> T,
): T = coroutineScope {
    windowedBuilderContext(
        project = project,
        knownRepositories = knownRepositories,
        projectCaches = projectCaches,
        applicationCaches = applicationCaches,
        packagesCache = packagesCache,
    ) {
        action(this@coroutineScope, this)
    }
}

inline fun <T> CoroutineScope.windowedBuilderContext(
    project: Project,
    knownRepositories: Map<String, ApiRepository>,
    packagesCache: PackageSearchApiPackageCache,
    projectCaches: CoroutineNitrite,
    applicationCaches: CoroutineNitrite,
    action: PackageSearchModuleBuilderContext.() -> T,
): T {
    val context = WindowedModuleBuilderContext(
        project = project,
        knownRepositories = knownRepositories,
        packagesCache = packagesCache,
        coroutineScope = this,
        projectCaches = projectCaches,
        applicationCaches = applicationCaches,
    )
    return context.action()
}

class WindowedModuleBuilderContext(
    override val project: Project,
    override val knownRepositories: Map<String, ApiRepository>,
    private val packagesCache: PackageSearchApiPackagesProvider,
    coroutineScope: CoroutineScope,
    override val projectCaches: CoroutineNitrite,
    override val applicationCaches: CoroutineNitrite,
) : PackageSearchModuleBuilderContext {

    private val idRequestsChannel = Channel<Request>()
    private val hashRequestsChannel = Channel<Request>()

    private val idResultsFlow = idRequestsChannel
        .responseFlow(coroutineScope) { packagesCache.getPackageInfoByIds(it) }

    private val hashResultsFlow = hashRequestsChannel
        .responseFlow(coroutineScope) { packagesCache.getPackageInfoByIdHashes(it) }

    override suspend fun getPackageInfoByIds(
        packageIds: Set<String>,
    ) = idResultsFlow.awaitResponse(packageIds, idRequestsChannel)

    override suspend fun getPackageInfoByIdHashes(
        packageIdHashes: Set<String>,
    ) = hashResultsFlow.awaitResponse(packageIdHashes, hashRequestsChannel)

    private fun Channel<Request>.responseFlow(
        coroutineScope: CoroutineScope,
        retrieveFunction: suspend (Set<String>) -> Map<String, ApiPackage>,
    ) = receiveAsFlow()
        .debounceBatch(1.seconds)
        .map { requests ->
            Response(
                ids = requests.map { it.requestId }.toSet(),
                packages = retrieveFunction(requests.flatMap { it.request }.toSet())
            )
        }
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed(), 0)

    private suspend fun Flow<Response>.awaitResponse(
        packageIds: Set<String>,
        requestChannel: Channel<Request>,
    ): Map<String, ApiPackage> = coroutineScope {
        val id = UUID.randomUUID().toString()
        val res = async(start = CoroutineStart.UNDISPATCHED) {
            filter { id in it.ids }
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

fun <T> Flow<T>.debounceBatch(duration: Duration): Flow<List<T>> = channelFlow {
    val mutex = Mutex()
    val buffer = mutableListOf<T>()
    var job: Job? = null
    collect {
        mutex.withLock {
            buffer.add(it)
            job?.cancel()
            job = launch {
                delay(duration)
                mutex.withLock {
                    send(buffer.toList())
                    buffer.clear()
                }
            }
        }
    }
}