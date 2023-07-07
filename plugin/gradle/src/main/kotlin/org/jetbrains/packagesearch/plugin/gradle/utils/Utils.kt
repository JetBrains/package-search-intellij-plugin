package org.jetbrains.packagesearch.plugin.gradle.utils

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import kotlinx.coroutines.flow.Flow
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import org.jetbrains.packagesearch.plugin.core.utils.appendEscaped
import org.jetbrains.packagesearch.plugin.core.utils.flow
import org.jetbrains.packagesearch.plugin.gradle.BaseGradleModuleTransformer
import org.jetbrains.packagesearch.plugin.gradle.GradleModelCacheEntry
import org.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleDeclaredPackage
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPathOrNull

val Module.isGradleSourceSet: Boolean
    get() {
        if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, this)) return false
        return ExternalSystemApiUtil.getExternalModuleType(this) == GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
    }

val Module.gradleIdentityPathOrNull: String?
    get() = CachedModuleDataFinder.getInstance(project)
        .findMainModuleData(this)
        ?.data
        ?.gradleIdentityPathOrNull

suspend fun DeclaredDependency.evaluateDeclaredIndexes(isKts: Boolean): DependencyDeclarationIndexes? {
    return dependencyDeclarationIndexes(
        groupId = coordinates.groupId ?: return null,
        artifactId = coordinates.artifactId ?: return null,
        version = coordinates.version?.takeIf { it.isNotEmpty() && it.isNotBlank() },
        isKts = isKts,
        configuration = unifiedDependency.scope ?: return null,
        psiElement = psiElement
    )
}

suspend fun dependencyDeclarationIndexes(
    groupId: String,
    artifactId:String,
    version: String?,
    isKts: Boolean,
    configuration: String,
    psiElement: PsiElement?
): DependencyDeclarationIndexes? {
    var currentPsi = psiElement ?: return null
    val isKotlinDependencyInKts = isKts && artifactId.startsWith("kotlin-")

    val regexText = buildString {
        when {
            isKotlinDependencyInKts -> {
                // configuration\(kotlin\("(name)", "(version)"\)\)
                append("$configuration\\(\"kotlin\\(\"(")
                appendEscaped(artifactId.removePrefix("kotlin-"))
                append(")")
                if (version != null) {
                    append(", \"(")
                    appendEscaped(version)
                    append(")\"")
                }
                append("\\)\\)")
            }

            else -> {
                // configuration[\s\(]+["'](groupId:artifactId):(version)["']\)?
                append("$configuration[\\s\\(]+[\"'](")
                appendEscaped("$groupId:$artifactId")
                append(")")
                if (version != null) {
                    append(":(")
                    appendEscaped(version)
                    append(")")
                }
                append("[\"']\\)?")
            }
        }
    }
    var attempts = 0
    val compiledRegex = Regex(regexText)

    // why 5? usually it's 3 parents up, maybe 2, sometimes 4. 5 is a safe bet.
    while (attempts < 5) {
        val groups = compiledRegex.find(readAction { currentPsi.text })?.groups
        if (!groups.isNullOrEmpty()) {
            val coordinatesStartIndex = groups[1]?.range?.start?.let { currentPsi.textOffset + it } ?: error(
                "Cannot find coordinatesStartIndex for dependency '$groupId:$artifactId:$version' " +
                        "in ${currentPsi.containingFile.virtualFile.path}"
            )
            return DependencyDeclarationIndexes(wholeDeclarationStartIndex = currentPsi.textOffset,
                coordinatesStartIndex = coordinatesStartIndex,
                versionStartIndex = groups[2]?.range?.start?.let { currentPsi.textOffset + it })
        }
        currentPsi = readAction { runCatching { currentPsi.parent }.getOrNull() } ?: break
        attempts++
    }
    return null
}

suspend fun PackageSearchModuleBuilderContext.getGradleModelRepository(): CoroutineObjectRepository<GradleModelCacheEntry> =
    projectCaches.getRepository<GradleModelCacheEntry>("gradle")

val Project.gradleSyncNotifierFlow
    get() = messageBus.flow(ProjectDataImportListener.TOPIC) {
        object : ProjectDataImportListener {
            override fun onImportFinished(projectPath: String?) {
                trySend(Unit)
            }
        }
    }

val Project.dumbModeStateFlow: Flow<Boolean>
    get() = messageBus.flow(DumbService.DUMB_MODE) {
        val l = object : DumbService.DumbModeListener {
            override fun enteredDumbMode() {
                trySend(true)
            }

            override fun exitDumbMode() {
                trySend(false)
            }
        }
        trySend(DumbService.getInstance(this@dumbModeStateFlow).isDumb)
        l
    }

fun generateAvailableScope(
    declaredDependencies: List<PackageSearchGradleDeclaredPackage>,
    configurationNames: List<String>
): List<String> {
    val usedConfigurations = declaredDependencies.map { it.configuration }
    return BaseGradleModuleTransformer.commonConfigurations
        .filter { it !in configurationNames }
        .plus(usedConfigurations)
        .distinct()
}

fun <T> listOf(item: T, items: List<T>) = buildList {
    add(item)
    addAll(items)
}