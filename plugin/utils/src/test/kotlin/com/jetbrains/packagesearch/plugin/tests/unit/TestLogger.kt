package com.jetbrains.packagesearch.plugin.tests.unit

import com.jetbrains.packagesearch.plugin.utils.PluginLogger

object TestLogger : PluginLogger {
    override fun logError(message: String, throwable: Throwable?) {
        System.err.print(buildMessage("ERROR: ", message, throwable))
    }

    override fun logError(contextName: String?, throwable: Throwable?, messageProvider: () -> String) {
        logError(buildMessageFrom(contextName, messageProvider = messageProvider), throwable)
    }

    override fun logWarn(message: String, throwable: Throwable?) {
        System.err.print(buildMessage("WARN: ", message, throwable))
    }

    override fun logWarn(contextName: String?, throwable: Throwable?, messageProvider: () -> String) {
        logWarn(buildMessageFrom(contextName, messageProvider = messageProvider), throwable)
    }

    override fun logInfo(message: String, throwable: Throwable?) {
        print(buildMessage("INFO: ", message, throwable))
    }

    override fun logInfo(contextName: String?, throwable: Throwable?, messageProvider: () -> String) {
        logInfo(buildMessageFrom(contextName, messageProvider = messageProvider), throwable)
    }

    override fun logDebug(message: String, throwable: Throwable?) {
        print(buildMessage("DEBUG: ", message, throwable))
    }

    override fun logDebug(contextName: String?, throwable: Throwable?, message: String?) {
        logDebug(buildMessageFrom(contextName, messageProvider = { message }), throwable)
    }

    override fun logTrace(message: String) {
        // This object does not implement trace logging
    }

    override fun logTrace(throwable: Throwable) {
        // This object does not implement trace logging
    }

    override fun logTODO() {
        System.err.print(buildMessage("TODO: ", "This feature is not implemented yet", null))
    }

    override fun logDebug(contextName: String?, throwable: Throwable?, messageProvider: () -> String) {
        logDebug(buildMessageFrom(contextName, messageProvider = messageProvider), throwable)
    }

    private fun buildMessageFrom(
        contextName: String?,
        messageProvider: () -> String?,
        message: String? = null,
    ) = buildString {
        if (!contextName.isNullOrBlank()) {
            append(contextName)
            append(' ')
        }
        if (isNotEmpty()) append("- ")

        messageProvider()?.let { append(it) }
        message?.let { append(it) }
    }

    private fun buildMessage(
        logPrefix: String,
        message: String,
        throwable: Throwable?,
    ) = buildString {
        appendLine(logPrefix + message)
        if (throwable != null) {
            appendLine("${throwable::class.qualifiedName}: ${throwable.message}")
            appendLine(throwable.stackTraceToString())
        }
    }
}
