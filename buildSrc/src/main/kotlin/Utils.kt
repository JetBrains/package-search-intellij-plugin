import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.PluginDependenciesSpecScope
import org.gradle.plugin.use.PluginDependency

fun PluginDependenciesSpecScope.id(plugin: Provider<PluginDependency>) =
    id(plugin.get().pluginId)