package com.jetbrains.packagesearch.plugin.utils

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.packagesearch.plugin.FeatureFlags
import com.jetbrains.packagesearch.plugin.core.PackageSearch
import java.util.concurrent.atomic.AtomicBoolean

@Service(Level.APP)
class IntelliJLogger : PluginLogger {

    private val logger: Logger = Logger.getInstance("#${PackageSearch.pluginId}")
    private val warned = AtomicBoolean(false)

    override fun logError(message: String, throwable: Throwable?) {
        logger.error(message, throwable)
    }

    override fun logError(contextName: String?, throwable: Throwable?, messageProvider: () -> String) {
        logError(buildMessageFrom(contextName, messageProvider), throwable)
    }

    override fun logWarn(message: String, throwable: Throwable?) {
        logger.warn(message, throwable)
    }

    override fun logWarn(contextName: String?, throwable: Throwable?, messageProvider: () -> String) {
        logWarn(buildMessageFrom(contextName, messageProvider), throwable)
    }

    override fun logInfo(message: String, throwable: Throwable?) {
        logger.info(message, throwable)
    }

    override fun logInfo(contextName: String?, throwable: Throwable?, messageProvider: () -> String) {
        logInfo(buildMessageFrom(contextName, messageProvider), throwable)
    }

    override fun logDebug(message: String, throwable: Throwable?) {
        if (!logger.isDebugEnabled) warnNotLoggable()
        logger.debug(message, throwable)
    }

    override fun logDebug(contextName: String?, throwable: Throwable?, message: String?) {
        logDebug(buildMessageFrom(contextName, message = message), throwable)
    }

    override fun logDebug(contextName: String?, throwable: Throwable?, messageProvider: () -> String) {
        logDebug(buildMessageFrom(contextName, messageProvider), throwable)
    }

    override fun logTrace(message: String) {
        if (!FeatureFlags.useDebugLogging || !logger.isTraceEnabled) warnNotLoggable()
        logger.trace(message)
    }

    override fun logTrace(throwable: Throwable) {
        if (!FeatureFlags.useDebugLogging || !logger.isTraceEnabled) warnNotLoggable()
        logger.trace(throwable)
    }

    override fun logTODO() {
        logWarn("This feature is not implemented yet") {
            "Stacktrace:\n" + Thread.currentThread().stackTrace.joinToString("\n") {
                "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            }
        }
    }

    private fun warnNotLoggable() {
        if (warned.getAndSet(true)) return
        logger.warn(
            """
            |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            |Debug logging not enabled. Make sure you have a line like this:
            |      #${PackageSearch.pluginId}:trace
            |in your debug log settings (Help | Diagnostic Tools | Debug Log Settings)
            |then restart the IDE.
            |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            |""".trimMargin()
        )
    }
}

fun buildMessageFrom(
    contextName: String?,
    messageProvider: (() -> String)? = null,
    message: String? = null,
): String = buildString {
    if (!contextName.isNullOrBlank()) {
        append(contextName)
        append(' ')
    }
    if (isNotEmpty()) append("- ")
    messageProvider?.let { append(it()) }
    message?.let { append(it) }
}