package com.jetbrains.packagesearch.plugin.core.nitrite.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

abstract class CoroutineWrapper {
    protected abstract val dispatcher: CoroutineDispatcher

    @InternalAPI
    suspend fun <T> dispatch(action: suspend CoroutineScope.() -> T) =
        withContext(dispatcher, action)
}