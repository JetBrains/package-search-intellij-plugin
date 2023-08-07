package com.jetbrains.packagesearch.plugin.core.nitrite.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import com.jetbrains.packagesearch.plugin.core.utils.PKGSInternalAPI

abstract class CoroutineWrapper {
    protected abstract val dispatcher: CoroutineDispatcher

    @PKGSInternalAPI
    suspend fun <T> dispatch(action: suspend CoroutineScope.() -> T) =
        withContext(dispatcher, action)
}