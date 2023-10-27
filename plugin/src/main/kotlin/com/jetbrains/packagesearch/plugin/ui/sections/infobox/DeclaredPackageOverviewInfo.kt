
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.intellij.icons.AllIcons
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.getAvailableVersions
import com.jetbrains.packagesearch.plugin.ui.ActionType
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
import com.jetbrains.packagesearch.plugin.ui.sections.infobox.displayRepositoryLinks
import com.jetbrains.packagesearch.plugin.ui.sections.infobox.packageTypeName
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.DeclaredPackageMoreActionPopup
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.evaluateUpgrade
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.latestVersion
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.GitHub

@Composable
fun DeclaredPackageOverviewInfo(
    selectedPackage: InfoBoxDetail.Package.DeclaredPackage,
    selectedPackageVariant: PackageSearchModule.WithVariants? = null,
) {
    val service = LocalPackageSearchService.current
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
                    DefaultActionButton(
                        actionName = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.actions.update"),
                        actionType = ActionType.UPDATE
                    ) {
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
                    Icon("general/chevron-down.svg", null, AllIcons::class.java)

                    if (openActionPopup) {
                        val contentOffsetX = with(LocalDensity.current) { 100.dp.toPx().toInt() + 1 }
                        val contentOffsetY = with(LocalDensity.current) { 32.dp.toPx().toInt() + 1 }
                        val borderColor: Color =
                            remember(JewelTheme.isDark) { pickComposeColorFromLaf("OnePixelDivider.background") }
                        val backgroundColor: Color =
                            remember(JewelTheme.isDark) { pickComposeColorFromLaf("PopupMenu.background") }
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
                }else{
                    Text(modifier = Modifier.padding(4.dp), text = selectedPackage.declaredDependency.declaredVersion.versionName)
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


                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(selectedPackage.declaredDependency.icon.lightIconPath, null, IconProvider::class.java)
                    val typeName = selectedPackage
                        .declaredDependency
                        .remoteInfo
                        ?.packageTypeName
                    Text(
                        typeName
                            ?: PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.columns.typeUnknown")
                    )
                }

            }
            val declaredVersion = selectedPackage.declaredDependency.declaredVersion
            selectedPackage
                .declaredDependency
                .remoteInfo
                ?.versions
                ?.all
                ?.firstOrNull { it.normalizedVersion.versionName == declaredVersion.versionName }
                ?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LabelInfo(
                            modifier = Modifier.defaultMinSize(90.dp),
                            text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.repo")
                        )
                        displayRepositoryLinks(it.repositoryIds)
                    }
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
        selectedPackage.declaredDependency.remoteInfo
            ?.description
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                Text(modifier = Modifier.padding(2.dp), text = it)
            }

        selectedPackage.declaredDependency.remoteInfo?.let { OtherLinks(it) }
        }

    }
}

@Composable
fun ColumnScope.OtherLinks(apiPackage: ApiPackage) {
    val scope = LocalPackageSearchService.current.coroutineScope
    //repo link
    apiPackage.scm?.let {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            val repoName = when (it) {
                is GitHub -> PackageSearchBundle.message("packagesearch.ui.toolwindow.link.github")
            }
            ExternalLink(
                repoName,
                onClick = {
                    scope.launch {
                        openLinkInBrowser(it.url)
                    }
                },
            )
            if ((it.stars != null)) {
                Icon(resource = "icons/Rating.svg", contentDescription = null, IconProvider::class.java)
                LabelInfo(it.stars.toString())
            }
        }

        it.license?.let out@{ scmLincenseFile ->
            val url = scmLincenseFile.htmlUrl ?: scmLincenseFile.url ?: return@out
            ExternalLink(
                scmLincenseFile.name ?: it.url,
                onClick = {
                    scope.launch {
                        openLinkInBrowser(url)
                    }
                },
            )
        }
        //project site
        (it as? GitHub)?.let {
            ExternalLink(
                PackageSearchBundle.message("packagesearch.ui.toolwindow.link.projectSite.capitalized"),
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
                onClick = {
                    scope.launch {
                        openLinkInBrowser(it)
                    }
                },
            )
        }

    }
}