@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.utils

import com.intellij.ProjectTopics
import com.intellij.openapi.project.ModuleListener
import com.intellij.util.messages.Topic

val ModuleListenerTopic: Topic<ModuleListener>
    get() = ProjectTopics.MODULES
