@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.externalSystem.DependencyModifierService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlText
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredBaseMavenPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import com.jetbrains.packagesearch.plugin.core.utils.asMavenApiPackage
import com.jetbrains.packagesearch.plugin.core.utils.icon
import com.jetbrains.packagesearch.plugin.core.utils.toDirectory
import com.jetbrains.packagesearch.plugin.scala.PackageSearchScalaModule
import com.jetbrains.packagesearch.plugin.scala.ScalaAccessor
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlinx.coroutines.flow.map
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackageTypes
import org.jetbrains.packagesearch.api.v3.search.jvmMavenPackages
import org.jetbrains.packagesearch.maven.POM_XML_NAMESPACE
import org.jetbrains.packagesearch.maven.ProjectObjectModel
import org.jetbrains.packagesearch.maven.decodeFromString
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.sbt.language.utils.SbtDependencyCommon
import org.jetbrains.sbt.language.utils.SbtDependencyUtils
import scala.Tuple3
import scala.collection.immutable.Seq

fun VirtualFile.asRegularFile() = File(path)
fun String.suffixIfNot(s: String) = if (endsWith(s)) this else this + s

val xml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
    }
}

private fun buildMavenParentHierarchy(pomFile: File): String {
    val pom = xml.decodeFromString<ProjectObjectModel>(POM_XML_NAMESPACE, pomFile.readText())
    val parentFile = pom.parent?.relativePath
        ?.let { pomFile.parentFile.resolve(it) }
        ?: return ":"
    val projectName = pom.artifactId ?: pomFile.parentFile.name
    val parentHierarchy = buildMavenParentHierarchy(parentFile)
    return parentHierarchy.suffixIfNot(":") + projectName
}

context(PackageSearchModuleBuilderContext)
suspend fun Module.toPackageSearch(): PackageSearchScalaModule {
    val declaredDependencies = declaredSbtDependencies()
    val pomPath = Path(TODO())
    return PackageSearchScalaModule(
        name = mavenProject.mavenId.artifactId ?: mavenProject.name ?: pomPath.parent.name,
        identity = PackageSearchModule.Identity(
            group = "maven",
            path = ":",
            projectDir = pomPath.parent.toDirectory(),
        ),
        buildFilePath = pomPath,
        declaredKnownRepositories = getDeclaredKnownRepositories(),
        declaredDependencies = declaredDependencies,
        availableScopes = commonScopes.plus(declaredDependencies.mapNotNull { it.declaredScope }).distinct(),
        compatiblePackageTypes = buildPackageTypes {
            jvmMavenPackages()
        },
        nativeModule = this
    )
}

context(PackageSearchModuleBuilderContext)
suspend fun Module.getDeclaredDependencies(): List<PackageSearchDeclaredBaseMavenPackage> {
    val declaredDependencies = readAction {
        MavenProjectsManager.getInstance(project)
            .findProject(this@getDeclaredDependencies)
            ?.file
            ?.let { MavenDomUtil.getMavenDomProjectModel(project, it) }
            ?.dependencies
            ?.dependencies
            ?.mapNotNull {
                MavenDependencyModel(
                    groupId = it.groupId.stringValue ?: return@mapNotNull null,
                    artifactId = it.artifactId.stringValue ?: return@mapNotNull null,
                    version = it.version.stringValue,
                    scope = it.scope.stringValue,
                    indexes = DependencyDeclarationIndexes(
                        declarationStartIndex = it.xmlElement?.textOffset ?: return@mapNotNull null,
                        versionStartIndex = it.version.xmlTag?.children
                            ?.firstOrNull { it is XmlText }
                            ?.textOffset
                    )
                )
            }
            ?: emptyList()
    }.distinct()

    val distinctIds = declaredDependencies
        .asSequence()
        .map { it.packageId }
        .distinct()

    val remoteInfo = getPackageInfoByIdHashes(distinctIds.map { ApiPackage.hashPackageId(it) }.toSet())

    return declaredDependencies
        .associateBy { it.packageId }
        .mapNotNull { (packageId, declaredDependency) ->
            PackageSearchDeclaredBaseMavenPackage(
                id = packageId,
                declaredVersion = declaredDependency.version?.let { NormalizedVersion.fromStringOrNull(it) },
                remoteInfo = remoteInfo[packageId]?.asMavenApiPackage(),
                groupId = declaredDependency.groupId,
                artifactId = declaredDependency.artifactId,
                declaredScope = declaredDependency.scope,
                declarationIndexes = declaredDependency.indexes,
                icon = remoteInfo[packageId]?.icon ?: IconProvider.Icons.MAVEN
            )
        }
}

context(PackageSearchModuleBuilderContext)
suspend fun Module.getDeclaredKnownRepositories(): Map<String, ApiRepository> {
    val declaredDependencies = readAction {
        DependencyModifierService.getInstance(project)
            .declaredRepositories(this)
    }
        .mapNotNull { it.id }
    return knownRepositories.filterKeys { it in declaredDependencies }
}

fun Module.declaredSbtDependencies() =
    SbtDependencyUtils.getLibraryDependenciesOrPlaces(
        /* sbtFileOpt = */ SbtDependencyUtils.getSbtFileOpt(this),
        /* project = */ project,
        /* module = */ this,
        /* mode = */ SbtDependencyUtils.GetMode.`GetDep$`.`MODULE$`
    )
        .asSequence()
        .mapNotNull {
            if (it._1() !is ScExpression || it._3() !is ScExpression) null
            else Tuple3(it._1() as ScExpression, it._2(), it._3() as ScExpression)
        }
        .mapNotNull {
            val libDepArr = SbtDependencyUtils.processLibraryDependencyFromExprAndString(it, true)
                .asSequence()
                .mapNotNull { it as? String }
                .toList()

            when {
                libDepArr.size < 3 || libDepArr.size > 4 -> null
                else -> {
                    val scope = when (libDepArr.size) {
                        3 -> SbtDependencyCommon.defaultLibScope()
                        else -> libDepArr[3]
                    }

                    val scalaVer = ScalaAccessor.getScalaVersion(this)
                        .fold({ "" }, { it.minor() })

                    com.jetbrains.packagesearch.plugin.core.data.MavenDependencyModel(
                        groupId = libDepArr[0],
                        artifactId = when {
                            SbtDependencyUtils.isScalaLibraryDependency(it._1()) ->
                                SbtDependencyUtils.buildScalaArtifactIdString(
                                    /* groupId = */ libDepArr[0],
                                    /* artifactId = */ libDepArr[1],
                                    /* scalaVer = */ scalaVer
                                )

                            else -> libDepArr[1]
                        },
                        version = libDepArr[2],
                        scope = scope,
                        indexes = DependencyDeclarationIndexes(
                            declarationStartIndex = it._1().textRange.startOffset,
                            versionStartIndex = it._3().textRange.startOffset
                        )
                    )
                }
            }
        }

// convert Scala Seq to Kotlin Sequence
fun <A> Seq<A>.asSequence(): Sequence<A> = sequence {
    val i = iterator()
    while (i.hasNext()) {
        yield(i.next())
    }
}
