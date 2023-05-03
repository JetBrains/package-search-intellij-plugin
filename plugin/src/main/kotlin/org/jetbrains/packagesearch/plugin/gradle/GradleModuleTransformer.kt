@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.flow.*
import org.dizitart.no2.objects.filters.ObjectFilters
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.plugin.data.WithIcon.PathSourceType.ClasspathResources
import org.jetbrains.packagesearch.plugin.data.PackageSearchDeclaredDependency
import org.jetbrains.packagesearch.plugin.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.extensions.DependencyDeclarationIndexes
import org.jetbrains.packagesearch.plugin.extensions.PackageSearchModuleBuilderContext
import org.jetbrains.packagesearch.plugin.extensions.PackageSearchModuleTransformer
import org.jetbrains.packagesearch.plugin.extensions.ProjectContext
import org.jetbrains.packagesearch.plugin.gradle.PackageSearchGradleModelNodeProcessor.Companion.getGradleModelRepository
import org.jetbrains.packagesearch.plugin.maven.packageId
import org.jetbrains.packagesearch.plugin.utils.*
import org.jetbrains.packagesearch.plugin.utils.appendEscaped
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.util.GradleConstants
import com.intellij.openapi.module.Module as NativeModule


class GradleModuleTransformer : PackageSearchModuleTransformer {

    companion object Utils {

        val gradleHomePathString: String
            get() = System.getenv("GRADLE_HOME")
                ?: System.getProperty("user.home")

        val gradleHome
            get() = gradleHomePathString.toNioPath()

        val globalGradlePropertiesPath
            get() = gradleHome.resolve("gradle.properties")

        context(ProjectContext)
        val knownGradleAncillaryFilesFiles
            get() = listOf("gradle.properties", "local.properties", "gradle/libs.versions.toml")

        fun DeclaredDependency.evaluateDeclaredIndexes(isKts: Boolean): DependencyDeclarationIndexes? {
            val artifactId = coordinates.artifactId ?: return null
            val groupId = coordinates.groupId ?: return null
            val configuration = unifiedDependency.scope ?: return null
            var currentPsi = psiElement ?: return null
            val isKotlinDependencyInKts = isKts && artifactId.startsWith("kotlin-")
            val version = coordinates.version
                ?.takeIf { it.isNotEmpty() && it.isNotBlank() }

            val regexText = buildString {
                when {
                    isKotlinDependencyInKts -> {
                        // configuration\(kotlin\("(name)", "(version)"\)\)
                        append("$configuration(\"kotlin\\(\"(")
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
                val groups = compiledRegex.find(currentPsi.text)?.groups
                if (!groups.isNullOrEmpty()) {
                    val coordinatesStartIndex = groups[1]?.range?.start
                        ?.let { currentPsi.textOffset + it }
                        ?: error(
                            "Cannot find coordinatesStartIndex for dependency $coordinates " +
                                    "in ${currentPsi.containingFile.virtualFile.path}"
                        )
                    return DependencyDeclarationIndexes(
                        wholeDeclarationStartIndex = currentPsi.textOffset,
                        coordinatesStartIndex = coordinatesStartIndex,
                        versionStartIndex = groups[2]?.range?.start
                            ?.let { currentPsi.textOffset + it }
                    )
                }
                currentPsi = kotlin.runCatching { currentPsi.parent }.getOrNull() ?: break
                attempts++
            }
            return null
        }

        /**
         * This function takes a module as input and returns its associated [ExternalProject] if one exists
         */
        fun Module.findExternalProject(): ExternalProject? {
            // Check if the module is associated with an external system (in this case, Gradle)
            if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, this)) {
                // Get the system ID of the module
                val moduleId = ExternalSystemModulePropertyManager.getInstance(this).getExternalSystemId()

                // Check if the module is a "fake" module representing a source set
                if (moduleId != GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY) {
                    // Get the path to the associated Gradle project
                    val projectPath = ExternalSystemApiUtil.getExternalProjectPath(this)

                    // If the project path is not null, the module is associated with a Gradle project
                    if (projectPath != null) {
                        // Get the ExternalProjectInfo object for the Gradle project
                        val projectInfo = ExternalSystemApiUtil.findProjectInfo(
                            /* project = */ project,
                            /* systemId = */ GradleConstants.SYSTEM_ID,
                            /* projectPath = */ projectPath
                        )

                        // If the ExternalProjectInfo object is not null, retrieve the ExternalProject
                        if (projectInfo != null) {
                            // Depending on the structure of projectInfo.externalProjectStructure, you may need to navigate this data structure differently
                            return projectInfo.externalProjectStructure?.let { it.data as? ExternalProject }
                        }
                    }
                }
            }

            // If the module is not associated with a Gradle project or represents a source set, return null
            return null
        }
    }

    override val moduleSerializer
        get() = PackageSearchGradleModule.serializer()

    override val versionSerializer
        get() = PackageSearchDeclaredGradleDependency.serializer()

    context(ProjectContext)
    private suspend fun getModuleChangesFlow(buildFile: String?, projectDir: String): Flow<Unit> {
        val allFiles = if (buildFile != null) {
            knownGradleAncillaryFilesFiles + buildFile
        } else {
            knownGradleAncillaryFilesFiles
        }
        val buildFileChanges = buildFile?.let { buildFile ->
            project.filesChangedEventFlow
                .flatMapLatest { it.map { it.path }.asFlow() }
                .filter { filePath -> allFiles.any { filePath.endsWith(it) } }
                .mapUnit()
        } ?: emptyFlow()
        return merge(
            watchFileChanges(globalGradlePropertiesPath),
            buildFileChanges,
            getGradleModelRepository(project)
                .changes(ObjectFilters.eq("_id", projectDir))
                .mapUnit()
        )
    }

    context(PackageSearchModuleBuilderContext)
    private suspend fun Module.toPackageSearch(gradleProject: ExternalProject): PackageSearchGradleModule? {
        val packageSearchGradleModel = getGradleModelRepository(project)
            .find(ObjectFilters.eq("_id", gradleProject.projectDir.absolutePath))
            .find { it.id == gradleProject.projectDir.absolutePath }
            ?.data
            ?.takeIf { !it.isKotlinMultiplatformApplied }
            ?: return null

        val availableKnownRepositories = packageSearchGradleModel
            .repositories
            .toSet()
            .let { availableGradleRepositories ->
                knownRepositories.filterValues {
                    it is ApiMavenRepository
                            && it.alternateUrls.intersect(availableGradleRepositories).isNotEmpty()
                }
            }

        val isKts = gradleProject.buildFile?.extension == "kts"

        return PackageSearchGradleModule(
            name = gradleProject.name,
            projectDirPath = gradleProject.projectDir.absolutePath,
            buildFilePath = gradleProject.buildFile?.absolutePath,
            declaredKnownRepositories = getDeclaredKnownRepositories(),
            declaredDependencies = getDeclaredDependencies(isKts),
            availableKnownRepositories = availableKnownRepositories,
            packageSearchModel = packageSearchGradleModel
        )
    }

    context(PackageSearchModuleBuilderContext)
    override fun buildModule(nativeModule: NativeModule): Flow<PackageSearchModule?> =
        flow {
            val gradleProject = nativeModule.findExternalProject()
            if (gradleProject == null) {
                emit(null)
                return@flow
            }
            emit(nativeModule.toPackageSearch(gradleProject))
            getModuleChangesFlow(gradleProject.buildFile?.absolutePath, gradleProject.projectDir.absolutePath)
                .collect { emit(nativeModule.toPackageSearch(gradleProject)) }
        }

    context(PackageSearchModuleBuilderContext)
    private suspend fun Module.getDeclaredDependencies(isKts: Boolean): List<PackageSearchDeclaredDependency> {
        val declaredDependencies = readAction {
            DependencyModifierService.getInstance(project)
                .declaredDependencies(this)
        }

        val remoteInfo = declaredDependencies
            .asSequence()
            .filter { it.coordinates.artifactId != null && it.coordinates.groupId != null }
            .map { it.packageId }
            .distinct()
            .map { ApiPackage.hashPackageId(it) }
            .toSet()
            .let { getPackageInfoByIdHashes(it) }

        return declaredDependencies
            .associateBy { it.packageId }
            .mapNotNull { (packageId, declaredDependency) ->
                PackageSearchDeclaredGradleDependency(
                    id = packageId,
                    declaredVersion = NormalizedVersion.from(declaredDependency.coordinates.version),
                    latestStableVersion = remoteInfo[packageId]
                        ?.versions
                        ?.latest
                        ?.normalized
                        ?.takeIf { it.isStable }
                        ?: remoteInfo[packageId]
                            ?.versions
                            ?.all
                            ?.find { it.normalized.isStable }
                            ?.normalized
                                ?: NormalizedVersion.Missing,
                    latestVersion = remoteInfo[packageId]?.versions?.latest?.normalized ?: NormalizedVersion.Missing,
                    remoteInfo = remoteInfo[packageId],
                    icon = ClasspathResources("icons/maven.svg"),
                    module = declaredDependency.coordinates.groupId ?: return@mapNotNull null,
                    name = declaredDependency.coordinates.artifactId ?: return@mapNotNull null,
                    configuration = declaredDependency.unifiedDependency.scope
                        ?: error(
                            "No scope available for ${declaredDependency.unifiedDependency}" +
                                    " in module $name"
                        ),
                    declarationIndexes = declaredDependency.evaluateDeclaredIndexes(isKts),
                )
            }
    }

    context(PackageSearchModuleBuilderContext)
    private suspend fun NativeModule.getDeclaredKnownRepositories(): Map<String, ApiRepository> {
        val declaredDependencies = readAction {
            DependencyModifierService.getInstance(project)
                .declaredRepositories(this)
        }
            .mapNotNull { it.id }
        return knownRepositories.filterKeys { it in declaredDependencies }
    }

    context(ProjectContext)
    override suspend fun updateDependencies(
        module: PackageSearchModule,
        installedPackages: List<PackageSearchDeclaredDependency>,
        knownRepositories: List<ApiRepository>,
    ) {
        val updates =
            installedPackages.filterIsInstance<PackageSearchDeclaredGradleDependency>()
                .filter { it.latestVersion != null && it.declaredVersion != it.latestVersion }
                .map {
                    val oldDescriptor = UnifiedDependency(
                        groupId = it.module,
                        artifactId = it.name,
                        version = it.declaredVersion?.versionName,
                        configuration = it.configuration
                    )
                    val newDescriptor = UnifiedDependency(
                        groupId = it.module,
                        artifactId = it.name,
                        version = it.latestVersion?.versionName ?: it.declaredVersion?.versionName,
                        configuration = it.configuration
                    )
                    oldDescriptor to newDescriptor
                }
        writeAction {
            updates.forEach { (oldDescriptor, newDescriptor) ->
                DependencyModifierService.getInstance(project)
                    .updateDependency(module.nativeModule, oldDescriptor, newDescriptor)
            }
        }
    }

    context(ProjectContext)
    override suspend fun installDependency(
        module: PackageSearchModule,
        apiPackage: ApiPackage,
        selectedVersion: String,
    ) {
        val mavenApiPackage = apiPackage as? ApiMavenPackage ?: return

        val descriptor = UnifiedDependency(
            groupId = mavenApiPackage.groupId,
            artifactId = mavenApiPackage.artifactId,
            version = selectedVersion,
            configuration = null
        )
        writeAction {
            DependencyModifierService.getInstance(project)
                .addDependency(module.nativeModule, descriptor)
        }
    }

    context(ProjectContext)
    override suspend fun removeDependency(
        module: PackageSearchModule,
        installedPackage: PackageSearchDeclaredDependency,
    ) {
        val gradleDependency =
            installedPackage as? PackageSearchDeclaredGradleDependency ?: return

        val descriptor = UnifiedDependency(
            groupId = gradleDependency.module,
            artifactId = gradleDependency.name,
            version = gradleDependency.declaredVersion?.versionName,
            configuration = gradleDependency.configuration
        )

        writeAction {
            DependencyModifierService.getInstance(project)
                .removeDependency(module.nativeModule, descriptor)
        }
    }
}
