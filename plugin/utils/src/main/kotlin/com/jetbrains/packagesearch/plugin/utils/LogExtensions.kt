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

interface PluginLogger {

    companion object {
        fun buildMessageFrom(
            contextName: String? = null,
            throwable: Throwable? = null,
            messageProvider: (StringProvider)? = null,
        ) = buildString {

            if (!contextName.isNullOrBlank()) append(contextName)
            if (isNotEmpty()) append(" - ")
            if (messageProvider != null) append(messageProvider())
            if (throwable != null) appendThrowable(throwable)
        }

        fun buildMessageFrom(
            contextName: String? = null,
            trace: Array<StackTraceElement>? = null,
            messageProvider: (StringProvider)? = null,
        ) = buildString {

            if (!contextName.isNullOrBlank()) append(contextName)
            if (isNotEmpty()) append(" - ")
            if (messageProvider != null) append(messageProvider())
            if (trace != null) appendStackTrace(trace)
        }

    }

    fun logError(message: String, throwable: Throwable? = null)
    fun logError(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String)

    fun logWarn(message: String, throwable: Throwable? = null)
    fun logWarn(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String)

    fun logInfo(message: String, throwable: Throwable? = null)
    fun logInfo(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String)

    fun logDebug(message: String, throwable: Throwable? = null)
    fun logDebug(contextName: String? = null, throwable: Throwable? = null, message: String? = null)
    fun logDebug(contextName: String? = null, throwable: Throwable? = null, messageProvider: () -> String)

    fun logTrace(message: String)
    fun logTrace(throwable: Throwable)

    fun logTODO()
}

typealias StringProvider = () -> String

internal fun StringBuilder.appendThrowable(throwable: Throwable) {
    appendLine("${throwable::class.qualifiedName}: ${throwable.message}")
    appendStackTrace(throwable.stackTrace)
}

internal fun StringBuilder.appendStackTrace(stackTrace: Array<StackTraceElement>) {
    appendLine("Stacktrace: ${stackTrace.first().format()}")
    stackTrace
        .drop(1)
        .joinTo(this, separator = "\n") { "\t" + it.format() }
}

internal fun StackTraceElement.format() = "$className.$methodName($fileName:$lineNumber)"