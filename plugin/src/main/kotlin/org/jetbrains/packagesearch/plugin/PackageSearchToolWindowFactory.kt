package org.jetbrains.packagesearch.plugin;

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.*
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.utils.PackageSearchService
import java.io.File
import kotlin.concurrent.thread

inline fun <T> Iterable<T>.applyOnEach(action: T.() -> Unit) =
    forEach { it.action() }

class PackageSearchToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        project.PackageSearchService.coroutineScope.launch {
            val jsonFLow = PackageSearchModuleBaseTransformerUtils.extensionsFlow
                .map { transformers ->
                    Json {
                        serializersModule = SerializersModule {
                            polymorphic(PackageSearchModule.WithVariants::class) {
                                transformers.filterIsInstance<PackageSearchModuleTransformer.WithVariants>()
                                    .applyOnEach { registerModuleSerializer() }
                            }
                            polymorphic(PackageSearchModule.Base::class) {
                                transformers.filterIsInstance<PackageSearchModuleTransformer.Base>()
                                    .applyOnEach { registerModuleSerializer() }
                            }
                            polymorphic(PackageSearchDeclaredDependency::class) {
                                transformers.applyOnEach { registerVersionSerializer() }
                            }
                        }
                    }
                }
            project.PackageSearchService.modules
                .zip(jsonFLow) { modules, json ->
                    json.encodeToString(modules)
                }
                .collect { text ->
                    withContext(Dispatchers.IO) {
                        File("C:\\Users\\lamba\\IdeaProjects\\pkgs-v2\\modules.json")
                            .writeText(text)
                    }
                }
        }
    }
}

private fun ToolWindow.addComposeTab(
    title: String,
    isLockable: Boolean = false,
    content: @Composable () -> Unit,
) = contentManager.addContent(
    contentManager.factory.createContent(
        ComposePanel().apply { setContent(content) },
        title,
        isLockable
    )
)

