package com.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.jetbrains.packagesearch.plugin.fus.PackageSearchFUSEvent
import com.jetbrains.packagesearch.plugin.fus.log
import com.jetbrains.packagesearch.plugin.utils.logWarn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry

@Service(Level.APP)
class PackageSearchFUSService(coroutineScope: CoroutineScope) {
    private val eventsChannel: Channel<PackageSearchFUSEvent> = Channel(capacity = Channel.UNLIMITED)

    init {
        eventsChannel.consumeAsFlow()
            .onEach { it.log() }
            .retry {
                logWarn("${this::class.qualifiedName}#eventReportingJob", it) { "Failed to log FUS" }
                true
            }
            .launchIn(coroutineScope)
    }

    fun logEvent(event: PackageSearchFUSEvent) {
        eventsChannel.trySend(event)
    }
}