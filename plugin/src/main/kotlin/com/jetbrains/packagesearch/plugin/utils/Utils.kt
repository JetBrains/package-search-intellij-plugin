@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.util.Function
import com.jetbrains.packagesearch.plugin.core.utils.FlowWithInitialValue
import com.jetbrains.packagesearch.plugin.core.utils.flow
import com.jetbrains.packagesearch.plugin.core.utils.withInitialValue
import io.ktor.client.plugins.logging.Logger
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

// keep the type explicit, otherwise the Kotlin plugin breaks
internal val Project.nativeModules: List<NativeModule>
    get() = ModuleManager.getInstance(this).modules.toList()

internal val Project.nativeModulesFlow: FlowWithInitialValue<List<NativeModule>>
    get() = messageBus.flow(ModuleListener.TOPIC) {
        object : ModuleListener {
            override fun modulesAdded(project: Project, modules: NativeModules) {
                trySend(nativeModules)
            }

            override fun moduleRemoved(project: Project, module: Module) {
                trySend(nativeModules)
            }

            override fun modulesRenamed(
                project: Project,
                modules: MutableList<out Module>,
                oldNameProvider: Function<in Module, String>,
            ) {
                trySend(nativeModules)
            }
        }
    }.withInitialValue(nativeModules)

fun interval(
    interval: Duration,
    emitOnStart: Boolean = false,
) = flow {
    if (emitOnStart) {
        emit(Unit)
    }
    while (true) {
        delay(interval)
        emit(Unit)
    }
}

typealias NativeModules = List<Module>
typealias NativeModule = Module

fun <T> Flow<T?>.startWithNull() = onStart { emit(null) }

@Suppress("FunctionName")
fun KtorDebugLogger() = object : Logger {
    override fun log(message: String) = logDebug(message)
}

@Composable
fun <T> FlowWithInitialValue<T>.collectAsState() =
    collectAsState(initialValue)