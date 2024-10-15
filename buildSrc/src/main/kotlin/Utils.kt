import java.io.File
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.PluginDependenciesSpecScope
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.version
import org.gradle.plugin.use.PluginDependency
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension

fun PluginDependenciesSpecScope.id(plugin: Provider<PluginDependency>) =
    id(plugin.get().pluginId)

const val PACKAGE_SEARCH_PLUGIN_ID = "com.jetbrains.packagesearch.intellij-plugin"
const val INTELLIJ_VERSION = "243.19420.21"

fun String.containsAny(toDelete: List<String>) =
    toDelete.any { it in this }


val JAR_NAMES_TO_REMOVE = listOf(
    "kotlin-stdlib",
    "slf4j",
    "kotlin-reflect",
    "kotlinx-coroutines",
    "logback",
)
