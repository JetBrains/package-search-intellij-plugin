package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.service


val LocalComponentManager: ProvidableCompositionLocal<ComponentManager> =
    staticCompositionLocalOf { error("No LocalComponentManager provided") }

@Composable
inline fun <reified T : Any> viewModel() = LocalComponentManager.current.service<T>()

