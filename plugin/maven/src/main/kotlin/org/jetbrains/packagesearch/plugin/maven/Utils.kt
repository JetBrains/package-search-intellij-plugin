@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.maven

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.packagesearch.plugin.extensions.DependencyDeclarationIndexes
import java.nio.file.Path

suspend fun Project.findMavenProjectFor(module: Module): MavenProject? =
    MavenProjectsManager.getInstance(this).let { readAction { it.findProject(module) } }

suspend fun DeclaredDependency.evaluateDeclaredIndexes() = readAction {
    val children: Array<PsiElement> = (psiElement as? XmlTag)
        ?.children
        ?: return@readAction null
    val xmlTag = children.filterIsInstance<XmlText>()
        .find { it is Navigatable && it.canNavigate() }
        ?: return@readAction null
    DependencyDeclarationIndexes(
        wholeDeclarationStartIndex = xmlTag.textOffset,
        coordinatesStartIndex = xmlTag.textOffset,
        versionStartIndex = children.filterIsInstance<XmlTag>()
            .find { it.name == "version" }
            ?.children
            ?.filterIsInstance<XmlText>()
            ?.firstOrNull()
            ?.textOffset
    )
}



