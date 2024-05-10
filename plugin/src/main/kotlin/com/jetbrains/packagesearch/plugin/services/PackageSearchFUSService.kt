package com.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.jetbrains.packagesearch.plugin.fus.PackageSearchFUSEvent
import com.jetbrains.packagesearch.plugin.fus.log
import com.jetbrains.packagesearch.plugin.utils.PackageSearchLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.shareIn

@Service(Level.APP)
class PackageSearchFUSService(coroutineScope: CoroutineScope) {
    private val fusEventsChannel: Channel<PackageSearchFUSEvent> = Channel()
    private val fusEventsFlow = fusEventsChannel.consumeAsFlow()
        .shareIn(coroutineScope, SharingStarted.Lazily)

    init {
        fusEventsFlow
            .onEach { it.log() }
            .retry(5) {
                PackageSearchLogger.logWarn(
                    contextName = "${this::class.qualifiedName}#eventReportingJob",
                    throwable = it
                ) { "Failed to log FUS" }
                true
            }
            .launchIn(coroutineScope)
    }

    fun logEvent(event: PackageSearchFUSEvent) {
        fusEventsChannel.trySend(event)
    }
}