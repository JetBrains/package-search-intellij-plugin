package org.jetbrains.packagesearch.plugin.gradle.utils

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.core.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.core.nitrite.coroutines.CoroutineObjectRepository
import org.jetbrains.packagesearch.plugin.core.utils.appendEscaped
import org.jetbrains.packagesearch.plugin.core.utils.flow
import org.jetbrains.packagesearch.plugin.gradle.GradleModelCacheEntry
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPathOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    val artifactId = coordinates.artifactId ?: return null
    val groupId = coordinates.groupId ?: return null
    val configuration = unifiedDependency.scope ?: return null
    var currentPsi = psiElement ?: return null
    val isKotlinDependencyInKts = isKts && artifactId.startsWith("kotlin-")
    val version = coordinates.version?.takeIf { it.isNotEmpty() && it.isNotBlank() }

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
                "Cannot find coordinatesStartIndex for dependency $coordinates " + "in ${currentPsi.containingFile.virtualFile.path}"
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
