package org.jetbrains.packagesearch.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.Application
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.core.utils.flow
import com.jetbrains.packagesearch.plugin.services.ModulesState
import org.jetbrains.jewel.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme
import org.jetbrains.packagesearch.api.v3.http.PackageSearchApiClient
import com.jetbrains.packagesearch.plugin.LocalProjectService
import com.jetbrains.packagesearch.plugin.ui.SplitPane
import org.jetbrains.packagesearch.plugin.services.PackageSearchComposeTunnel
import org.jetbrains.packagesearch.plugin.ui.bridge.generateData
import org.jetbrains.packagesearch.plugin.ui.bridge.isLightTheme
import org.jetbrains.packagesearch.plugin.ui.bridge.packageSearchResourceLoader


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UI(
    composeTunnel: PackageSearchComposeTunnel,
    apiClient: PackageSearchApiClient,
    isActionPerforming: MutableState<Boolean>,
) {
    val lightMode by IntelliJApplication.lightThemeFlow().collectAsState(isLightTheme())
    val detailsExpanded = composeTunnel.infoTabStateFlow.collectAsState()

    val swingCompat by remember { mutableStateOf(false) }
    val theme = if (!lightMode) IntUiTheme.dark() else IntUiTheme.light()
    val windowBackground = if (lightMode) {
        IntUiTheme.light().palette.grey(13)
    } else {
        IntUiTheme.dark().palette.grey(2)
    }
    IntUiTheme(theme, swingCompat) {
        CompositionLocalProvider(
            LocalResourceLoader provides packageSearchResourceLoader,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(windowBackground)
                    .padding(top = 2.dp, bottom = 0.dp, start = 2.dp, end = 2.dp)
            ) {
                val moduleProviderState by LocalProjectService.current.modules.collectAsState()
                when (val moduleProvider = moduleProviderState) {
                    is ModulesState.Loading -> IndeterminateHorizontalProgressBar(Modifier.fillMaxWidth())
                    is ModulesState.Ready -> {
                        val tree = remember(moduleProvider) {
                            moduleProvider.modules.generateData()
                        }
                        SplitPane(detailsExpanded, tree, apiClient, isActionPerforming)
                    }
                }
            }
        }
    }
}

fun Application.lightThemeFlow() = IntelliJApplication.messageBus.flow(LafManagerListener.TOPIC) {
    object : LafManagerListener {
        override fun lookAndFeelChanged(source: LafManager) {
            trySend(isLightTheme())
        }
    }
}