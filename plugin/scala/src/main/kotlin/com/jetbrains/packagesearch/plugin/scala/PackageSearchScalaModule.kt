@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.scala

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.IconProvider.Icons
import com.jetbrains.packagesearch.plugin.core.data.MavenDependencyModel
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredBaseMavenPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.plugin.core.utils.toUnifiedRepository
import com.jetbrains.packagesearch.plugin.core.utils.validateMavenDeclaredPackageType
import com.jetbrains.packagesearch.plugin.core.utils.validateMavenPackageType
import com.jetbrains.packagesearch.plugin.core.utils.validateRepositoryType
import java.nio.file.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.PackagesType
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.jvmMavenPackages
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.sbt.language.utils.SbtArtifactInfo
import org.jetbrains.sbt.language.utils.SbtDependencyCommon
import org.jetbrains.sbt.language.utils.SbtDependencyUtils
import scala.Tuple3
import scala.collection.immutable.Seq

@Serializable
@SerialName("scala")
data class PackageSearchScalaModule(
    override val name: String,
    override val identity: PackageSearchModule.Identity,
    override val buildFilePath: Path,
    override val declaredKnownRepositories: Map<String, ApiRepository>,
    override val declaredDependencies: List<PackageSearchDeclaredBaseMavenPackage>,
    private val nativeModule: Module,
) : PackageSearchModule.Base {

    override val compatiblePackageTypes: List<PackagesType>
        get() = buildPackageTypes {
            jvmMavenPackages()
        }

    override val availableScopes: List<String>
        get() = listOf("compile", "test", "runtime", "provided")

    override val defaultScope: String?
        get() = null

    override val dependencyMustHaveAScope: Boolean
        get() = false

    override val icon
        get() = Icons.MAVEN

    override suspend fun editModule(action: context(EditModuleContext) () -> Unit) {
        writeAction { action(EditScalaModuleContext) }
    }

    context(EditModuleContext)
    override fun updateDependency(
        declaredPackage: PackageSearchDeclaredPackage,
        newVersion: String?,
        newScope: String?,
    ) {
        removeDependency(declaredPackage)
        val psiFile = buildFilePath.refreshAndFindVirtualFile()
            ?.findPsiFile(nativeModule.project)
            ?: return
        validateMavenDeclaredPackageType(declaredPackage)
        SbtDependencyUtils.addDependency(
            /* expr = */ psiFile,
            /* info = */ SbtArtifactInfo(
                /* groupId = */ declaredPackage.groupId,
                /* artifactId = */ declaredPackage.artifactId,
                /* version = */ newVersion ?: declaredPackage.declaredVersion?.versionName,
                /* configuration = */ newScope ?: declaredPackage.declaredScope
            ),
            /* project = */ nativeModule.project
        )
    }

    context(EditModuleContext)
    override fun addDependency(
        apiPackage: ApiPackage,
        selectedVersion: String,
        selectedScope: String?,
    ) {
        validateMavenPackageType(apiPackage)
        val psiFile = buildFilePath.refreshAndFindVirtualFile()
            ?.findPsiFile(nativeModule.project)
            ?: return

        SbtDependencyUtils.addDependency(
            /* expr = */ psiFile,
            /* info = */ SbtArtifactInfo(
                /* groupId = */ apiPackage.groupId,
                /* artifactId = */ apiPackage.artifactId,
                /* version = */ selectedVersion,
                /* configuration = */ selectedScope
            ),
            /* project = */ nativeModule.project
        )
    }

    context(EditModuleContext)
    override fun removeDependency(declaredPackage: PackageSearchDeclaredPackage) {
        validateMavenDeclaredPackageType(declaredPackage)
        val declared = SbtDependencyUtils.findLibraryDependency(
            /* project = */ nativeModule.project,
            /* module = */ nativeModule,
            /* dependency = */ UnifiedDependency(
                groupId = declaredPackage.groupId,
                artifactId = declaredPackage.artifactId,
                version = declaredPackage.declaredVersion?.versionName,
                configuration = declaredPackage.declaredScope
            ),
            /* versionRequired = */ false,
            /* configurationRequired = */ false
        )
        when (val parent = declared._3().parent) {
            is ScArgumentExprList -> declared._3().delete()
            is ScInfixExpr -> if (parent.left().textMatches(SbtDependencyUtils.LIBRARY_DEPENDENCIES())) parent.delete()
        }
    }

    context(EditModuleContext)
    override fun addRepository(repository: ApiRepository) {
        validateRepositoryType(repository)
        SbtDependencyUtils.addRepository(
            /* expr = */ buildFilePath.refreshAndFindVirtualFile()?.findPsiFile(nativeModule.project) ?: return,
            /* unifiedDependencyRepository = */ repository.toUnifiedRepository(),
            /* project = */ nativeModule.project
        )
    }

    context(EditModuleContext)
    override fun removeRepository(repository: ApiRepository) {
        validateRepositoryType(repository)
    }
}
