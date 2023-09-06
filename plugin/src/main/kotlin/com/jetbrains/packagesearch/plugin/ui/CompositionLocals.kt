package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.Application
import com.jetbrains.packagesearch.plugin.core.utils.flow
import com.jetbrains.packagesearch.plugin.services.PackageSearchProjectService
import com.jetbrains.packagesearch.plugin.ui.bridge.isLightTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient

val LocalJson = staticCompositionLocalOf { Json { prettyPrint = true } }

val LocalProjectService = staticCompositionLocalOf<PackageSearchProjectService> {
    error("No ProjectService provided")
}

val LocalProjectCoroutineScope = staticCompositionLocalOf<CoroutineScope> {
    error("No ProjectCoroutineScope provided")
}

val LocalIsActionPerformingState: ProvidableCompositionLocal<MutableState<Boolean>> =
    staticCompositionLocalOf { error("No LocalPackageSearchApiClient provided") }

val LocalPackageSearchApiClient: ProvidableCompositionLocal<PackageSearchApiClient> =
    staticCompositionLocalOf { error("No LocalPackageSearchApiClient provided") }

val LocalDependencyManagers =
    compositionLocalFrom { LocalProjectService.current.dependencyManagers.collectAsState().value }

interface CompositionLocalProvider<T> {

    @get:Composable
    val current: T

}

fun <T> compositionLocalFrom(provide: @Composable () -> T) =
    object : CompositionLocalProvider<T> {
        override val current: T
            @Composable
            get() = provide()
    }

val LocalIsOnlyStableVersions: ProvidableCompositionLocal<MutableState<Boolean>> =
    staticCompositionLocalOf { error("No LocalIsOnlyStableVersions provided") }

val LocalGlobalPopupIdState: ProvidableCompositionLocal<MutableState<String?>> =
    staticCompositionLocalOf { error("No LocalDropDownItemId provided") }

fun Application.lightThemeFlow() =
    messageBus.flow(LafManagerListener.TOPIC) { LafManagerListener { trySend(isLightTheme()) } }

