package org.jetbrains.packagesearch.plugin.ui.bridge

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.latestStableOrNull
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import org.jetbrains.jewel.RawJarResourceLoader
import org.jetbrains.jewel.foundation.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.tree.buildTree
import org.jetbrains.jewel.getJarPath
import org.jetbrains.packagesearch.api.v3.ApiPackage
import com.jetbrains.packagesearch.plugin.PackageSearchToolWindowFactory
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageSearchAction
import java.awt.Desktop
import java.net.URI
import javax.swing.UIDefaults
import javax.swing.UIManager

fun java.awt.Color.toComposeColor(): Color {
    return Color(red, green, blue, alpha)
}

fun UIDefaults.getComposeColor(key: String): Color? {
    return getColor(key)?.toComposeColor()
}

fun UIDefaults.getComposeColorOrUnspecified(key: String): Color {
    return getColor(key)?.toComposeColor().let {
        println("No color in LAF for $key, fallback to Color.Unspecified")
        Color.Unspecified
    }
}


@OptIn(ExperimentalComposeUiApi::class)
val packageSearchResourceLoader = RawJarResourceLoader(
    jars = buildList {
        getJarPath<PackageSearchModule>()?.let { add(it) }
        getJarPath<PackageSearchToolWindowFactory>()?.let { add(it) }
    }
)


fun List<PackageSearchModuleData>.generateData() =
    buildTree {
        groupBy { it.module.identity.group }
            .values
            .forEach {
                val sortedItems = it.sortedBy { it.module.identity.path }
                sortedItems.filter { it.module.identity.path == ":" }
                    .forEach { addData(sortedItems, it) }
            }
    }

fun TreeGeneratorScope<PackageSearchModuleData>.addData(
    sortedItems: List<PackageSearchModuleData>,
    currentData: PackageSearchModuleData
) {
    val children = sortedItems
        .filter { it.module.identity.path.startsWith(currentData.module.identity.path) }
    if (children.isNotEmpty()) addNode(currentData, id = currentData.module.identity) {
        children.forEach { addData(sortedItems.subList(sortedItems.indexOf(currentData) + 1, sortedItems.size), it) }
    }
    else addLeaf(currentData, id = currentData.module.identity)
}

fun openLinkInBrowser(url: String) {
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.BROWSE)) {
        val uri = URI(url)
        desktop.browse(uri)
    } // todo never tested before

}


fun pickComposeColorFromLaf(key: String) =
    UIManager.getLookAndFeelDefaults().getComposeColor(key) ?: Color.Unspecified


fun isLightTheme(): Boolean {
    val laf = UIManager.getLookAndFeelDefaults()
    val brightness = laf.getComposeColor("ToolWindow.background")?.getBrightness() ?: 200f
    return brightness < 128
}

private fun Color.getBrightness() = (red * 299 + green * 587 + blue * 114) / 1000


internal fun ApiPackage.getPackageActions(
    selectedModules: List<PackageSearchModuleData>,
    localProjectService: PackageSearchProjectService,
    dependencyManager: PackageSearchDependencyManager
): Pair<PackageSearchAction, List<PackageSearchAction>> {
    //get install package data for each selected module
    val installPackageDataList = selectedModules.flatMap {
        when (val module = it.module) {
            is PackageSearchModule.Base -> {

                listOf(versions.latestStable?.normalized?.let {
                    module.getInstallData(
                        this,
                        it.versionName, module.defaultScope ?: module.availableScopes.first()
                    )
                })
            }

            is PackageSearchModule.WithVariants -> {
                module.variants
                    .values
                    .map {
                        versions.latestStable?.normalized?.versionName?.let { version ->
                            it.getInstallData(this, version, module.defaultScope ?: module.availableScopes.first())
                        }
                    }
            }
        }
    }.filterNotNull()
    return PackageSearchAction(
        "Add",
    ) {
        installPackageDataList.forEach {
            dependencyManager.installDependency(
                localProjectService, it
            )
        }
    } to emptyList()
}


internal fun PackageSearchDeclaredPackage.getPackageActions(
    selectedModules: List<PackageSearchModuleData>,
    localProjectService: PackageSearchProjectService,
    selectedVersion: String? = latestStableOrNull?.normalized?.versionName,
    newScope: String? = null,
): Pair<PackageSearchAction?, List<PackageSearchAction>> {
    val defaultAction = selectedVersion?.let {
        PackageSearchAction("Upgrade") {
            selectedModules.forEach { packageSearchModuleData ->
                packageSearchModuleData.dependencyManager.updateDependencies(
                    localProjectService,
                    listOf(getUpdateData(it, newScope)),
                    localProjectService.knownRepositoriesStateFlow.value.values.toList()
                )
            }
        }
    }
    val actions = buildList {
        add(
            PackageSearchAction("Remove") {
                selectedModules.forEach { packageSearchModuleData ->
                    packageSearchModuleData.dependencyManager.removeDependency(
                        localProjectService,
                        getDeleteData()
                    )
                }
            }
        )
    }
    return defaultAction to actions
}