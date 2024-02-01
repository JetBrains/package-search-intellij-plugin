package com.jetbrains.packagesearch.plugin.maven

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject

class MavenSyncStateService(private val project: Project) : MavenImportListener {

    @Service(Service.Level.PROJECT)
    class State : MutableStateFlow<Boolean> by MutableStateFlow(false)

    override fun importStarted() {
        project.service<State>().value = true
    }

    override fun importFinished(importedProjects: MutableCollection<MavenProject>, newModules: MutableList<Module>) {
        project.service<State>().value = false
    }
}