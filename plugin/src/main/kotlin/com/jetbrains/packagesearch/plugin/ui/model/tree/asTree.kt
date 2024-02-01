package com.jetbrains.packagesearch.plugin.ui.model.tree

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.ui.model.hasUpdates
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.buildTree

internal fun List<PackageSearchModule>.asTree(stableOnly: Boolean): Tree<TreeItemModel> =
    buildTree {
        groupBy { it.identity.group }
            .map { (_, modules) ->
                val sortedItems = modules.sortedBy { it.identity.path }
                val roots = sortedItems.filter { it.identity.path == ":" }.toSet()
                when {
                    roots.isNotEmpty() -> roots.forEach { module ->
                        addElements(
                            sortedItems = sortedItems - roots,
                            currentData = module,
                            onlyStable = stableOnly,
                            isRoot = true
                        )
                    }

                    else -> sortedItems.forEach { module ->
                        addLeaf(
                            data = module.asViewModel(stableOnly),
                            id = module.identity
                        )
                    }
                }
            }
    }

private fun TreeGeneratorScope<TreeItemModel>.addElements(
    sortedItems: List<PackageSearchModule>,
    currentData: PackageSearchModule,
    onlyStable: Boolean,
    isRoot: Boolean = false,
) {
    val children = sortedItems
        .filter {
            val toRemove = buildString {
                append(currentData.identity.path)
                if (!isRoot) append(":")
            }
            it.identity.path.removePrefix(toRemove)
                .run { isNotEmpty() && !contains(":") }
        }
        .toSet()
    if (children.isNotEmpty()) {
        addNode(
            data = currentData.asViewModel(onlyStable),
            id = currentData.identity,
        ) {
            children.forEach {
                addElements(
                    sortedItems = sortedItems - children,
                    currentData = it,
                    onlyStable = onlyStable
                )
            }
        }
    } else {
        addLeaf(
            data = currentData.asViewModel(onlyStable),
            id = currentData.identity,
        )
    }

}

private fun PackageSearchModule.asViewModel(onlyStable: Boolean) =
    TreeItemModel(
        id = identity,
        text = name,
        hasUpdates = when (this) {
            is PackageSearchModule.Base ->
                declaredDependencies.any { it.hasUpdates(onlyStable) }

            is PackageSearchModule.WithVariants ->
                variants.any { it.value.declaredDependencies.any { it.hasUpdates(onlyStable) } }
        },
        icon = icon
    )