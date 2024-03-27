/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jetbrains.packagesearch.plugin.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PriorityAction.Priority.HIGH
import com.intellij.codeInsight.intention.PriorityAction.Priority.LOW
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.utils.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.utils.PackageSearchSettingsService
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion

abstract class PackageSearchInspection : LocalInspectionTool() {

    final override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean,
    ): Array<ProblemDescriptor> {
        val path = file.virtualFile.toNioPathOrNull() ?: return emptyArray()

        val module = file.project.PackageSearchProjectService
            .modulesByBuildFile.value[path] ?: return emptyArray()

        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)

        with(file.project.PackageSearchProjectService) {
            problemsHolder.checkFile(
                file = file,
                module = module
            )
        }

        return problemsHolder.resultsArray
    }

    context(PackageSearchKnownRepositoriesContext)
    abstract fun ProblemsHolder.checkFile(
        file: PsiFile,
        module: PackageSearchModule,
    )

    override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WARNING

}

/**
 * An inspection that flags out-of-date dependencies in supported files, supplying a quick-fix to
 * upgrade them to the latest version.
 *
 * Note that this inspection follows the "only stable" inspection settings.
 *
 */
class PackageUpdateInspection : PackageSearchInspection() {

    @JvmField
    var excludeList: MutableList<String> = mutableListOf()

    override fun getOptionsPane(): OptPane {
        return pane(
            stringList(
                "excludeList",
                PackageSearchBundle.message("packagesearch.inspection.upgrade.excluded.dependencies")
            )
        )
    }

    context(PackageSearchKnownRepositoriesContext)
    override fun ProblemsHolder.checkFile(
        file: PsiFile,
        module: PackageSearchModule,
    ) {
        val packagesByManager =
            buildMap<PackageSearchDependencyManager, List<PackageSearchDeclaredPackage>> {
                when (module) {
                    is PackageSearchModule.Base -> put(module, module.declaredDependencies)
                    is PackageSearchModule.WithVariants -> module.variants.values.forEach {
                        put(it, it.declaredDependencies)
                    }
                }
            }
        val packagesByManagerFiltered = packagesByManager.mapValues { (_, declaredDependencies) ->
            declaredDependencies.filterNot { declared ->
                excludeList.asSequence()
                    .map { it.removeSuffix("*") }
                    .any { declared.id.startsWith(it) }
            }
        }
        packagesByManagerFiltered.forEach outer@{ (manager, dependencies) ->
            dependencies.forEach inner@{ dependency ->
                val versionElement =
                    file.getElementAt(dependency.declarationIndexes.versionStartIndex ?: return@inner)
                        .takeIf { it != file }
                        ?: return@inner

                val targetVersion = when {
                    project.PackageSearchSettingsService.stableOnlyFlow.value ->
                        dependency.remoteInfo?.versions?.latestStable

                    else -> dependency.remoteInfo?.versions?.latest
                } ?: return@inner

                val normalizedVersionIsGarbage = dependency.declaredVersion is NormalizedVersion.Garbage
                val declaredVersionIsMissing = dependency.declaredVersion == null

                if (normalizedVersionIsGarbage || declaredVersionIsMissing) return@inner
                if (dependency.declaredVersion!! >= targetVersion.normalized) return@inner

                registerProblem(
                    psiElement = versionElement,
                    descriptionTemplate = PackageSearchBundle.message(
                        "packagesearch.inspection.upgrade.description",
                        dependency.displayName,
                        targetVersion.normalized.versionName
                    )
                ) {
                    localQuickFixOnPsiElement(
                        familyName = PackageSearchBundle.message("packagesearch.quickfix.upgrade.family"),
                        text = PackageSearchBundle.message(
                            "packagesearch.quickfix.upgrade.action",
                            dependency.displayName,
                            targetVersion.normalized.versionName
                        ),
                        priority = HIGH
                    ) {
                        coroutineScope.launch {
                            module.editModule {
                                manager.updateDependency(
                                    declaredPackage = dependency,
                                    newVersion = targetVersion.normalized.versionName,
                                    newScope = dependency.declaredScope
                                )
                                if (project.PackageSearchSettingsService.installRepositoryIfNeededFlow.value) {
                                    targetVersion.repositoryIds
                                        .firstNotNullOfOrNull { project.PackageSearchProjectService.knownRepositories[it] }
                                        ?.takeIf { it.url !in module.declaredRepositories.map { it.url } }
                                        ?.let { module.addRepository(it) }
                                }
                            }
                        }
                    }
                    localQuickFixOnPsiElement(
                        familyName = PackageSearchBundle.message("packagesearch.quickfix.upgrade.exclude.family"),
                        text = PackageSearchBundle.message(
                            "packagesearch.quickfix.upgrade.exclude.action",
                            dependency.displayName
                        ),
                    ) {
                        excludeList.add(dependency.id)
                        ProjectInspectionProfileManager.getInstance(project).fireProfileChanged()
                    }

                    if (dependency is PackageSearchDeclaredMavenPackage) {
                        localQuickFixOnPsiElement(
                            familyName = PackageSearchBundle.message("packagesearch.quickfix.upgrade.exclude.family"),
                            text = PackageSearchBundle.message(
                                "packagesearch.quickfix.upgrade.exclude.action.maven.group",
                                dependency.groupId
                            ),
                            priority = LOW
                        ) {
                            excludeList.add("maven:${dependency.groupId}:*")
                            ProjectInspectionProfileManager.getInstance(project).fireProfileChanged()
                        }
                    }
                }
            }
        }


    }
}

internal fun PsiFile.getElementAt(offset: Int): PsiElement {
    var elt = findElementAt(offset)
    if (elt == null && offset > 0) {
        elt = findElementAt(offset - 1)
    }
    return elt ?: this
}

internal fun ProblemsHolder.registerProblem(
    psiElement: PsiElement,
    @InspectionMessage descriptionTemplate: String,
    builder: RegisterProblemScope.() -> Unit,
) {
    registerProblem(psiElement, descriptionTemplate, *RegisterProblemScope(psiElement).apply(builder).getQuickfixes())
}

internal class RegisterProblemScope(private val psiElement: PsiElement) {
    private val quickfixes = mutableListOf<LocalQuickFix>()
    fun localQuickFixOnPsiElement(
        @Nls familyName: String,
        @Nls text: String,
        priority: PriorityAction.Priority = PriorityAction.Priority.NORMAL,
        action: () -> Unit,
    ) {
        quickfixes.add(LocalQuickFixOnPsiElement(psiElement, familyName, text, priority, action))
    }

    fun getQuickfixes() = quickfixes.toTypedArray()
}

@Suppress("FunctionName")
internal fun LocalQuickFixOnPsiElement(
    element: PsiElement,
    @Nls familyName: String,
    @Nls text: String,
    priority: PriorityAction.Priority = PriorityAction.Priority.NORMAL,
    action: () -> Unit,
): LocalQuickFix = object : LocalQuickFixOnPsiElement(element), PriorityAction {
    override fun getFamilyName() = familyName
    override fun getText() = text
    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) =
        action()

    override fun getPriority() = priority
}