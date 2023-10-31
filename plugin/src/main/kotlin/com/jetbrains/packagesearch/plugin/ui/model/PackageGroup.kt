package com.jetbrains.packagesearch.plugin.ui.model

import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import org.jetbrains.packagesearch.api.v3.ApiPackage

inline fun buildRemotePackageGroups(
    searchFilter: String,
    builder: PackageGroupsBuilder.() -> Unit,
) =
    PackageGroupsBuilder(searchFilter).apply(builder).getRemotes()

inline fun buildDeclaredPackageGroups(
    searchFilter: String,
    builder: PackageGroupsBuilder.() -> Unit,
) =
    PackageGroupsBuilder(searchFilter).apply(builder).getDeclared()

class PackageGroupsBuilder(private val searchQuery: String) {

    @JvmInline
    value class Remotes(val value: List<PackageGroup.Remote>)

    @JvmInline
    value class Declared(val value: List<PackageGroup.Declared>)

    private var remotes = emptyList<PackageGroup.Remote>()
    private var declared = emptyList<PackageGroup.Declared>()

    fun setSearchResults(data: SearchData.Results, selectedModules: List<PackageSearchModuleData>) {
        remotes = when (data) {
            SearchData.Results.Empty -> emptyList()
            is SearchData.SingleBaseModule.Results -> {
                val selectedModuleData = selectedModules.singleOrNull()
                val module = selectedModuleData
                    ?.module as? PackageSearchModule.Base

                module?.declaredDependencies
                    ?.map { it.id }
                    ?.let { declaredDependencyIds ->
                        listOf(
                            PackageGroup.Remote.FromBaseModule(
                                module = module,
                                packages = data.results.filter { it.id !in declaredDependencyIds },
                                dependencyManager = selectedModuleData.dependencyManager
                            )
                        )
                    } ?: emptyList()
            }

            is SearchData.MultipleModules.Results -> listOf(
                PackageGroup.Remote.FromMultipleModules(
                    moduleData = selectedModules,
                    packages = data.results,
                ),
            )

            is SearchData.SingleModuleWithVariants.Results -> {
                val selectedModuleData = selectedModules.singleOrNull()
                val module = selectedModuleData
                    ?.module as? PackageSearchModule.WithVariants
                    ?: return
                data.results.map { results ->
                    val variantNames = results.searchData.compatibleVariants.map { it.name }
                    PackageGroup.Remote.FromVariants(
                        module = module,
                        packages = results.results,
                        badges = results.searchData.compatibleVariants.first().attributes.map { it.value },
                        compatibleVariants = module.variants.filterKeys { it in variantNames }.values.toList(),
                        dependencyManager = selectedModuleData.dependencyManager
                    )
                }
            }
        }
    }

    fun setLocal(selectedModules: List<PackageSearchModuleData>) {
        declared = when {
            selectedModules.isEmpty() -> emptyList()
            selectedModules.size == 1 -> when (val module = selectedModules.first().module) {
                is PackageSearchModule.Base -> {
                    val filteredDependencies = module.declaredDependencies
                        .filter {
                            it.id.contains(
                                searchQuery,
                                true
                            ) || it.displayName.contains(searchQuery, true)
                        }

                    if (filteredDependencies.isEmpty()) emptyList()
                    else listOf(
                        PackageGroup.Declared.FromBaseModule(
                            module = module,
                            filteredDependencies = filteredDependencies,
                            dependencyManager = selectedModules.first().dependencyManager
                        )
                    )
                }

                is PackageSearchModule.WithVariants ->
                    module.variants
                        .mapNotNull { (_, variant) ->
                            val filteredDependencies = variant.declaredDependencies
                                .filter {
                                    it.id.contains(searchQuery, true) || it.displayName.contains(
                                        other = searchQuery,
                                        ignoreCase = true,
                                    )
                                }

                            if (filteredDependencies.isEmpty()) return@mapNotNull null
                            PackageGroup.Declared.FromVariant(
                                module = module,
                                variant = variant,
                                filteredDependencies = filteredDependencies,
                                dependencyManager = selectedModules.first().dependencyManager
                            )
                        }
            }

            else -> selectedModules.map { (module, dependencyManager) ->
                when (module) {
                    is PackageSearchModule.Base -> PackageGroup.Declared.FromBaseModule(
                        module = module,
                        filteredDependencies = module.declaredDependencies
                            .filter {
                                it.id.contains(
                                    searchQuery,
                                    true
                                ) || it.displayName.contains(searchQuery, true)
                            },
                        dependencyManager = dependencyManager
                    )

                    is PackageSearchModule.WithVariants -> PackageGroup.Declared.FromModuleWithVariantsCompact(
                        module = module,
                        filteredDependencies = module.variants.values
                            .flatMap {
                                it.declaredDependencies.filter {
                                    it.id.contains(searchQuery, true) || it.displayName.contains(
                                        other = searchQuery,
                                        ignoreCase = true,
                                    )
                                }
                            },
                        dependencyManager = dependencyManager
                    )
                }
            }
        }
    }

    fun build() = buildList {
        addAll(declared)
        addAll(remotes)
    }

    fun getDeclared() = Declared(declared)

    private val variantGroupComparator
        get() = compareBy<PackageGroup.Remote> {
            when (it) {
                is PackageGroup.Remote.FromVariants -> it.badges.size
                else -> 0
            }
        }
            .thenBy {
                when (it) {
                    is PackageGroup.Remote.FromVariants -> it.compatibleVariants.joinToString()
                    else -> ""
                }
            }

    fun getRemotes() = Remotes(remotes.sortedWith(variantGroupComparator).reversed())
}

operator fun PackageGroupsBuilder.Declared.plus(remotes: PackageGroupsBuilder.Remotes) =
    value + remotes.value

operator fun PackageGroupsBuilder.Remotes.plus(declared: PackageGroupsBuilder.Declared) =
    declared.value + value

fun findCommonStrings(lists: List<List<String>>): List<String> {
    // If there are no nested lists or if any of the nested lists are empty, return an empty list
    if (lists.isEmpty() || lists.any { it.isEmpty() }) return emptyList()

    // Start with the first list
    val commonStrings = lists[0].toMutableSet()

    // For each subsequent list, retain only those strings that are common
    for (i in 1 until lists.size) {
        commonStrings.retainAll(lists[i].toSet())
    }

    return commonStrings.toList()
}

sealed interface PackageGroup {

    @JvmInline
    value class Id(val value: String)

    enum class State {
        OPEN {
            override fun toggle() = COLLAPSED
        },
        COLLAPSED {
            override fun toggle() = OPEN
        },
        ;

        abstract fun toggle(): State
    }

    val id: Id
    val size: Int

    sealed interface Declared : PackageGroup {
        val module: PackageSearchModule
        val filteredDependencies: List<PackageSearchDeclaredPackage>
        val dependencyManager: PackageSearchDependencyManager

        data class FromVariant(
            override val module: PackageSearchModule.WithVariants,
            val variant: PackageSearchModuleVariant,
            override val filteredDependencies: List<PackageSearchDeclaredPackage.WithVariant>,
            override val dependencyManager: PackageSearchDependencyManager,
        ) : Declared {
            override val id: Id
                get() = Id("Local.FromModuleWithVariants [module = ${module.identity}, variant = ${variant.name}]")

            override val size: Int
                get() = filteredDependencies.size
        }

        data class FromModuleWithVariantsCompact(
            override val module: PackageSearchModule.WithVariants,
            override val filteredDependencies: List<PackageSearchDeclaredPackage.WithVariant>,
            override val dependencyManager: PackageSearchDependencyManager,
        ) : Declared {
            override val id: Id
                get() = Id(
                    "Local.FromModuleWithVariantsCompact [module = ${module.identity}, " +
                            "variants = ${filteredDependencies.joinToString { it.variantName }}]",
                )

            override val size: Int
                get() = filteredDependencies.size
        }

        data class FromBaseModule(
            override val module: PackageSearchModule.Base,
            override val filteredDependencies: List<PackageSearchDeclaredPackage>,
            override val dependencyManager: PackageSearchDependencyManager,
        ) : Declared {
            override val id: Id
                get() = Id("Local.FromBaseModule [module = ${module.identity}]")

            override val size: Int
                get() = filteredDependencies.size
        }
    }

    sealed interface Remote : PackageGroup {

        val packages: List<ApiPackage>

        data class FromBaseModule(
            val module: PackageSearchModule.Base,
            override val packages: List<ApiPackage>,
            val dependencyManager: PackageSearchDependencyManager,
        ) : Remote {
            override val id: Id
                get() = Id("Remote.FromBaseModule [module = ${module.identity}]")
            override val size: Int
                get() = packages.size
        }

        data class FromVariants(
            val module: PackageSearchModule.WithVariants,
            override val packages: List<ApiPackage>,
            val badges: List<String>,
            val compatibleVariants: List<PackageSearchModuleVariant>,
            val dependencyManager: PackageSearchDependencyManager,
        ) : Remote {
            override val id: Id
                get() = Id("Remote.FromVariant [module = ${module.identity}, variants = ${compatibleVariants.joinToString { it.name }}, badges = ${badges.toString()}]")
            override val size: Int
                get() = packages.size
        }

        data class FromMultipleModules(
            val moduleData: List<PackageSearchModuleData>,
            override val packages: List<ApiPackage>,
        ) : Remote {
            override val id: Id
                get() = Id("Remote.FromMultipleModules [modules = ${moduleData.joinToString { it.module.identity.toString() }}]")
            override val size: Int
                get() = packages.size
        }
    }
}
