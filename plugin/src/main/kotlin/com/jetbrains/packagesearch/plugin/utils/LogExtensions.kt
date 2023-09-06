/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

@file:Suppress("unused")

package com.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.jetbrains.packagesearch.plugin.FeatureFlags
import kotlinx.coroutines.CancellationException

private const val pluginId = "com.jetbrains.packagesearch.intellij-plugin"
private val logger = Logger.getInstance("#$pluginId")

fun logError(throwable: Throwable? = null, contextName: String? = null, messageProvider: () -> String) {
    logError(buildMessageFrom(contextName, messageProvider), throwable)
}

fun logError(message: String, throwable: Throwable? = null) {
    if (isNotLoggable(throwable)) return
    logger.error(message, throwable)
}

fun logWarn(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logWarn(buildMessageFrom(contextName, messageProvider), throwable)
}

fun logWarn(message: String, throwable: Throwable? = null) {
    if (isNotLoggable(throwable)) return
    logger.warn(message, throwable)
}

fun logInfo(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logInfo(buildMessageFrom(contextName, messageProvider), throwable)
}

fun logInfo(message: String, throwable: Throwable? = null) {
    if (isNotLoggable(throwable)) return
    logger.info(message, throwable)
}

fun logDebug(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String) {
    logDebug(buildMessageFrom(contextName, messageProvider), throwable)
}

fun logDebug(contextName: String? = null, throwable: Throwable? = null, message: String? = null) {
    logDebug(buildMessageFrom(contextName, message = message), throwable)
}

fun logDebug(message: String, throwable: Throwable? = null) {
    if (!FeatureFlags.useDebugLogging || isNotLoggable(throwable)) return
    if (!logger.isDebugEnabled) warnNotLoggable()
    logger.debug(message, throwable)
}

private inline fun catchAndSuppress(action: () -> Unit) {
    try {
        action()
    } catch (_: Throwable) {
    }
}

fun logTrace(message: String) = catchAndSuppress {
    if (!FeatureFlags.useDebugLogging) return
    if (!logger.isTraceEnabled) warnNotLoggable()

    logger.trace(message)
}

fun logTrace(throwable: Throwable) = catchAndSuppress {
    if (!FeatureFlags.useDebugLogging) return
    if (!logger.isTraceEnabled) warnNotLoggable()

    logger.trace(throwable)
}

private fun warnNotLoggable() {
    logger.warn(
        """
        |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        |Debug logging not enabled. Make sure you have a line like this:
        |      #${pluginId}:trace
        |in your debug log settings (Help | Diagnostic Tools | Debug Log Settings)
        |then restart the IDE.
        |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        |""".trimMargin()
    )
}

private fun buildMessageFrom(
    contextName: String?,
    messageProvider: (() -> String)? = null,
    message: String? = null
) = buildString {

    if (!contextName.isNullOrBlank()) {
        append(contextName)
        append(' ')
    }

    if (isNotEmpty()) append("- ")

    messageProvider?.let { append(it()) }
    message?.let { append(it) }
}

private fun isLoggable(ex: Throwable?) = when (ex) {
    is CancellationException, is ProcessCanceledException -> false
    else -> true
}

private fun isNotLoggable(ex: Throwable?) = !isLoggable(ex)