@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.maven

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import com.jetbrains.packagesearch.plugin.core.extensions.DependencyDeclarationIndexes
import java.io.File

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
        declarationStartIndex = xmlTag.textOffset,
        versionStartIndex = children.filterIsInstance<XmlTag>()
            .find { it.name == "version" }
            ?.children
            ?.filterIsInstance<XmlText>()
            ?.firstOrNull()
            ?.textOffset
    )
}

fun VirtualFile.asRegularFile() = File(path)
fun String.suffixIfNot(s: String) = if (endsWith(s)) this else this + s

