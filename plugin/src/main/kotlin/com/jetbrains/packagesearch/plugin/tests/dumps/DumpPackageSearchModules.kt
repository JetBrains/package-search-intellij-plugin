package com.jetbrains.packagesearch.plugin.tests.dumps

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.packagesearch.plugin.tests.CoroutineAbstractCommand
import com.jetbrains.packagesearch.plugin.tests.PKGS_TEST_DATA_OUTPUT_DIR
import com.jetbrains.packagesearch.plugin.tests.TestResult
import com.jetbrains.packagesearch.plugin.tests.map
import com.jetbrains.packagesearch.plugin.tests.toSerializable
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import com.jetbrains.performancePlugin.CreateCommand
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class DumpPackageSearchModules(text: String, line: Int) : CoroutineAbstractCommand(text, line) {
    companion object {
        const val DUMP_NAME = "dumpPackageSearchModules"
        const val PREFIX = CMD_PREFIX + DUMP_NAME
        val COMMAND
            get() = PREFIX to CreateCommand(::DumpPackageSearchModules)
        const val DUMP_FILE_NAME = "modules.json"
    }

    override suspend fun executeAsync(context: PlaybackContext) {
        val json by lazy {
            Json { prettyPrint = true }
        }
        withContext(Dispatchers.EDT) {
            ToolWindowManager.getInstance(context.project)
                .getToolWindow("PackageSearch")
                ?.activate(null)
        }
        context.project.PackageSearchProjectService
            .modulesStateFlow
            .timeout(10.minutes)
            .debounce(20.seconds)
            .filter { it.isNotEmpty() }
            .map { it.associate { it.name to it.toSerializable() } }
            .map { TestResult(value = it) }
            .catch { emit(TestResult(error = it.toSerializable())) }
            .map { json.encodeToString(it) }
            .map(Dispatchers.IO) { PKGS_TEST_DATA_OUTPUT_DIR.resolve("modules.json").writeText(it) }
            .first()
    }


}