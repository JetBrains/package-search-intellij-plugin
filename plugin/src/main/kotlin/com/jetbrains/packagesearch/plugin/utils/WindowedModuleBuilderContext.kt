package com.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository

class WindowedModuleBuilderContext(
    override val project: Project,
    private val knownRepositoriesGetter: () -> Map<String, ApiRepository>,
    private val packagesCache: PackageSearchApiPackageCache,
    override val coroutineScope: CoroutineScope,
) : PackageSearchModuleBuilderContext {

    override val knownRepositories: Map<String, ApiRepository>
        get() = knownRepositoriesGetter()

    private val idRequestsChannel = Channel<Request>()
    private val hashRequestsChannel = Channel<Request>()
    private val idResultsFlow = idRequestsChannel
        .responseFlow("idResultsFlow") { packagesCache.getPackageInfoByIds(it) }

    private val hashResultsFlow = hashRequestsChannel
        .responseFlow("hashResultsFlow") { packagesCache.getPackageInfoByIdHashes(it) }

    override suspend fun getPackageInfoByIds(packageIds: Set<String>) =
        idResultsFlow.awaitResponse(packageIds, idRequestsChannel)

    override suspend fun getPackageInfoByIdHashes(packageIdHashes: Set<String>) =
        hashResultsFlow.awaitResponse(packageIdHashes, hashRequestsChannel)

    private fun Channel<Request>.responseFlow(
        flowName: String,
        retrieveFunction: suspend (Set<String>) -> Map<String, ApiPackage>,
    ) = receiveAsFlow()
        .debounceBatch(1.seconds)
        .map { requests ->
            Response(
                requestIds = requests.map { it.requestId }.toSet(),
                packages = retrieveFunction(requests.flatMap { it.request }.toSet())
            )
        }
        .onEach { PackageSearchLogger.logDebug("${this::class.qualifiedName}#${flowName}") { "response.size = ${it.requestIds.size}" } }
        .shareIn(coroutineScope, SharingStarted.Eagerly, 0)

    private suspend fun Flow<Response>.awaitResponse(
        packageIds: Set<String>,
        requestChannel: Channel<Request>,
    ): Map<String, ApiPackage> = coroutineScope {
        val requestId = UUID.randomUUID().toString()
        val requestedPackages = async(start = CoroutineStart.UNDISPATCHED) {
            filter { requestId in it.requestIds }
                .map { it.packages }
                .first()
        }
        requestChannel.send(Request(requestId, packageIds))
        requestedPackages.await()
    }

}

private data class Request(
    val requestId: String,
    val request: Set<String>,
)

private data class Response(
    val requestIds: Set<String>,
    val packages: Map<String, ApiPackage>,
)

private fun <T> Flow<T>.debounceBatch(duration: Duration): Flow<List<T>> = channelFlow {
    val mutex = Mutex()
    val buffer = mutableListOf<T>()
    var job: Job? = null
    onCompletion {
        mutex.withLock {
            job?.cancel()
            send(buffer.toList())
        }
    }
        .collect {
            mutex.withLock {
                buffer.add(it)
                job?.cancel()
                job = launch {
                    delay(duration)
                    mutex.withLock {
                        val bufferCopy = buffer.toList()
                        send(bufferCopy)
                        buffer.clear()
                    }
                }
            }
        }
}