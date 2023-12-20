package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.plugin.ui.bridge.LocalPackageSearchDropdownLinkStyle
import com.jetbrains.packagesearch.plugin.ui.bridge.PackageSearchDropdownLinkStyle
import com.jetbrains.packagesearch.plugin.ui.bridge.PackageSearchGlobalColors
import com.jetbrains.packagesearch.plugin.ui.bridge.PackageSearchTabStyle
import com.jetbrains.packagesearch.plugin.ui.bridge.PackageSearchTreeStyle
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.ui.component.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalLazyTreeStyle

@Composable
internal fun PackageSearchTheme(project: Project, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalComponentManager provides project,
        LocalGlobalColors provides PackageSearchGlobalColors(),
        LocalDefaultTabStyle provides PackageSearchTabStyle(),
        LocalLazyTreeStyle provides PackageSearchTreeStyle(),
        LocalPackageSearchDropdownLinkStyle provides PackageSearchDropdownLinkStyle(),
    ) {
        content()
    }
}
