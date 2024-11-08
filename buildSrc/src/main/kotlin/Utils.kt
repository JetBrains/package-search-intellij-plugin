import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.PluginDependenciesSpecScope
import org.gradle.plugin.use.PluginDependency

fun PluginDependenciesSpecScope.id(plugin: Provider<PluginDependency>) =
    id(plugin.get().pluginId)

const val PACKAGE_SEARCH_PLUGIN_ID = "com.jetbrains.packagesearch.intellij-plugin"
const val INTELLIJ_VERSION = "243.21565.129"

fun String.containsAny(toDelete: List<String>) =
    toDelete.any { it in this }


val JAR_NAMES_TO_REMOVE = listOf(
    "kotlin-stdlib",
    "slf4j",
    "kotlin-reflect",
    "kotlinx-coroutines",
    "logback",
)
