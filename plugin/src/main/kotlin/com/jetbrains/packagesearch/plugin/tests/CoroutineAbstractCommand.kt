package com.jetbrains.packagesearch.plugin.tests

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.concurrency.Promise

abstract class CoroutineAbstractCommand(text: String, line: Int) : AbstractCommand(text, line) {

    @Service(Service.Level.PROJECT)
    private class TestCoroutineScopeService(val coroutineScope: CoroutineScope)

    final override fun _execute(context: PlaybackContext): Promise<Any> = context.project
        .service<TestCoroutineScopeService>()
        .coroutineScope
        .promise { executeAsync(context) }

    abstract suspend fun executeAsync(context: PlaybackContext)
}