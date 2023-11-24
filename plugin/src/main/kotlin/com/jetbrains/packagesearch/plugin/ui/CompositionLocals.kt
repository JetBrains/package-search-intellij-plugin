package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.service
import com.jetbrains.packagesearch.plugin.core.utils.flow
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.ui.bridge.isLightTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApi


val LocalComponentManager: ProvidableCompositionLocal<ComponentManager> =
    staticCompositionLocalOf { error("No LocalComponentManager provided") }

@Composable
inline fun <reified T : Any> viewModel() = LocalComponentManager.current.service<T>()

