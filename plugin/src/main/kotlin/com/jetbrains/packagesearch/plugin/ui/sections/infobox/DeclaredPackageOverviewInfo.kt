import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.intellij.openapi.components.service
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.getAvailableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import com.jetbrains.packagesearch.plugin.ui.model.InfoBoxDetail
import com.jetbrains.packagesearch.plugin.ui.model.ScopeSelectionDropdown
import com.jetbrains.packagesearch.plugin.ui.model.VersionSelectionDropdown
import com.jetbrains.packagesearch.plugin.ui.sections.infobox.DefaultActionButton
import com.jetbrains.packagesearch.plugin.ui.sections.infobox.PackageOverviewNameId
import com.jetbrains.packagesearch.plugin.ui.sections.infobox.packageTypeName
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.DeclaredPackageMoreActionPopup
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.evaluateUpgrade
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.latestVersion
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ExternalLink
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.LocalIconData
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.bridge.SwingBridgeService
import org.jetbrains.jewel.bridge.retrieveStatelessIcon
import org.jetbrains.jewel.foundation.onHover
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.GitHub

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DeclaredPackageOverviewInfo(
    selectedPackage: InfoBoxDetail.Package.DeclaredPackage,
    selectedPackageVariant: PackageSearchModule.WithVariants? = null,
) {
    val svgLoader = service<SwingBridgeService>().svgLoader
    val service = LocalPackageSearchService.current
    val iconData = LocalIconData.current
    val resourceLoader = LocalResourceLoader.current
    var openActionPopup by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 8.dp)) {

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Top) {
            PackageOverviewNameId(
                modifier = Modifier.weight(1f),
                selectedPackage.declaredDependency.displayName,
                selectedPackage.declaredDependency.id,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
                // defaultAction - can be upgraded
                val newVersion = selectedPackage.declaredDependency.evaluateUpgrade()?.versionName
                if (newVersion != null) {
                    DefaultActionButton(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.actions.update")) {
                        selectedPackage.dependencyManager.updateDependencies(
                            context = service,
                            data = listOf(
                                selectedPackage.declaredDependency.getUpdateData(
                                    newVersion
                                )
                            )
                        )
                    }
                }
                var backGroundColor by remember { mutableStateOf(Color.Transparent) }
                // more icon
                Box(modifier = Modifier
                    .background(backGroundColor, RoundedCornerShape(4.dp))
                    .padding(4.dp)
                    .onHover {
                        if (it) {
                            backGroundColor = pickComposeColorFromLaf("ActionButton.hoverBackground")
                        } else {
                            backGroundColor = Color.Transparent
                        }
                    }
                    .clickable {
                        openActionPopup = true
                    }
                ) {
                    val painterProvider = retrieveStatelessIcon(
                        iconPath = "general/chevron-down.svg",
                        svgLoader = svgLoader,
                        iconData = iconData
                    )
                    val painter by painterProvider.getPainter(LocalResourceLoader.current)
                    Icon(painter, null)

                    if (openActionPopup) {
                        val contentOffsetX = with(LocalDensity.current) { 100.dp.toPx().toInt() + 1 }
                        val contentOffsetY = with(LocalDensity.current) { 32.dp.toPx().toInt() + 1 }
                        val borderColor: Color =
                            remember(IntelliJTheme.isDark) { pickComposeColorFromLaf("OnePixelDivider.background") }
                        val backgroundColor: Color =
                            remember(IntelliJTheme.isDark) { pickComposeColorFromLaf("PopupMenu.background") }
                        Popup(
                            offset = IntOffset(-contentOffsetX, contentOffsetY),
                            onDismissRequest = { openActionPopup = false },
                            properties = PopupProperties(focusable = true),
                            onPreviewKeyEvent = { false },
                            onKeyEvent = { false },
                        ) {
                            Box(
                                modifier =
                                Modifier.width(200.dp)
                                    .clip(shape = RoundedCornerShape(10.dp))
                                    .border(
                                        width = 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .background(color = backgroundColor),
                            ) {
                                DeclaredPackageMoreActionPopup(
                                    dependencyManager = selectedPackage.dependencyManager,
                                    module = selectedPackage.module,
                                    packageSearchDeclaredPackage = selectedPackage.declaredDependency,
                                    onDismissRequest = { openActionPopup = false },
                                )
                            }
                        }
                    }
                }

            }
        }


        // versions
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(
                    modifier = Modifier.defaultMinSize(90.dp),
                    text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.version")
                )
                val availableVersion =
                    selectedPackage.declaredDependency.remoteInfo?.getAvailableVersions(LocalIsOnlyStableVersions.current.value)
                        ?.let {
                            it - selectedPackage.declaredDependency.declaredVersion
                        } ?: emptyList()
                val latestVersion = selectedPackage.declaredDependency.remoteInfo?.latestVersion

                if (latestVersion != null) {
                    VersionSelectionDropdown(
                        declaredVersion = selectedPackage.declaredDependency.declaredVersion,
                        availableVersions = availableVersion,
                        latestVersion = latestVersion.normalized,
                        updateLambda = { newVersion ->
                            selectedPackage.dependencyManager.updateDependencies(
                                context = service,
                                data = listOf(selectedPackage.declaredDependency.getUpdateData(newVersion = newVersion))
                            )
                        },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(
                    modifier = Modifier.defaultMinSize(90.dp),
                    text = PackageSearchBundle.message("packagesearch.terminology.dependency.configuration")
                )
                ScopeSelectionDropdown(
                    actualScope = selectedPackage.declaredDependency.scope,
                    availableScope = selectedPackage.module.availableScopes,
                    mustHaveScope = selectedPackage.module.dependencyMustHaveAScope,
                    updateLambda = { newScope ->
                        selectedPackage.dependencyManager.updateDependencies(
                            context = service,
                            data = listOf(
                                selectedPackage.declaredDependency.getUpdateData(
                                    newVersion = null,
                                    newScope = newScope
                                )
                            )
                        )
                    },
                )
            }
            //sourceset only for multiplatform
            if (selectedPackageVariant != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LabelInfo(
                        modifier = Modifier.defaultMinSize(90.dp),
                        text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.sourceSet")
                    )
                    //dropdown variant
                    Text(selectedPackageVariant.mainVariant.name)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(
                    modifier = Modifier.defaultMinSize(90.dp),
                    text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.type")
                )


                val painter = remember(svgLoader) {
                    retrieveStatelessIcon(selectedPackage.declaredDependency.icon.lightIconPath, svgLoader, iconData)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painter.getPainter(resourceLoader).value, null)
                    val typeName = selectedPackage
                        .declaredDependency
                        .remoteInfo
                        ?.packageTypeName
                    Text(typeName ?: PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.typeUnknown"))
                }

            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelInfo(
                    modifier = Modifier.defaultMinSize(90.dp),
                    text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.repo")
                )
                Text(selectedPackage.module.declaredKnownRepositories.keys.joinToString(", ").removeSuffix(", "))
            }

            selectedPackage.declaredDependency.remoteInfo?.authors.let {
                if (!it.isNullOrEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LabelInfo(
                            modifier = Modifier.defaultMinSize(90.dp),
                            text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.authors")
                        )
                        Text(it.map { it.name }.joinToString(", "))
                    }
                }
            }
        }
        selectedPackage.declaredDependency.remoteInfo
            ?.description
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                Text(modifier = Modifier.padding(2.dp), text = it)
            }

        selectedPackage.declaredDependency.remoteInfo?.let { OtherLinks(it) }
        // licenses

    }
}

@Composable
fun ColumnScope.OtherLinks(apiPackage: ApiPackage) {
    val scope = LocalPackageSearchService.current.coroutineScope
    val svgLoader = service<SwingBridgeService>().svgLoader
    //repo link
    apiPackage.scm?.let {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            val repoName = when (it) {
                is GitHub -> PackageSearchBundle.message("packagesearch.ui.toolwindow.link.github")
            }
            ExternalLink(
                repoName,
                resourceLoader = LocalResourceLoader.current,
                onClick = {
                    scope.launch {
                        openLinkInBrowser(it.url)
                    }
                },
            )
            if ((it.stars != null)) {
                val painterProvider = retrieveStatelessIcon(
                    iconPath = "icons/Rating.svg",
                    svgLoader = svgLoader,
                    iconData = IntUiTheme.iconData
                )
                val painter by painterProvider.getPainter(LocalResourceLoader.current)
                Icon(painter = painter, "Github Stars")
                LabelInfo(it.stars.toString())
            }
        }

        it.license?.let { scmLincenseFile ->
            ExternalLink(
                scmLincenseFile.name ?: it.url,
                resourceLoader = LocalResourceLoader.current,
                onClick = {
                    scope.launch {
                        openLinkInBrowser(scmLincenseFile.htmlUrl ?: scmLincenseFile.url)
                    }
                },
            )
        }
        //project site
        (it as? GitHub)?.let {
            ExternalLink(
                PackageSearchBundle.message("packagesearch.ui.toolwindow.link.projectSite.capitalized"),
                resourceLoader = LocalResourceLoader.current,
                onClick = {
                    scope.launch {
                        openLinkInBrowser(it.htmlUrl)
                    }
                },
            )
        }
        (it as? GitHub)?.readmeUrl?.let {
            ExternalLink(
                PackageSearchBundle.message("packagesearch.ui.toolwindow.link.readme.capitalized"),
                resourceLoader = LocalResourceLoader.current,
                onClick = {
                    scope.launch {
                        openLinkInBrowser(it)
                    }
                },
            )
        }

    }
}