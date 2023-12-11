package com.jetbrains.packagesearch.plugin.ui.panels.side

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.PackageSearchBundle.message
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.plugin.fus.logDetailsLinkClick
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.model.infopanel.InfoPanelContent
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.EditPackageEvent.SetPackageScope
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.EditPackageEvent.SetPackageVersion
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.OnPackageAction.GoToSource
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.OnPackageAction.Install.WithVariant
import com.jetbrains.packagesearch.plugin.ui.model.packageslist.PackageListItemEvent.OnPackageAction.Remove
import com.jetbrains.packagesearch.plugin.ui.panels.packages.DeclaredPackageActionPopup
import com.jetbrains.packagesearch.plugin.ui.panels.packages.RemotePackageWithVariantsActionPopup
import com.jetbrains.packagesearch.plugin.ui.panels.packages.ScopeSelectionDropdown
import com.jetbrains.packagesearch.plugin.ui.panels.packages.VersionSelectionDropdown
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun PackageOverviewTab(
    onLinkClick: (String) -> Unit,
    onPackageEvent: (PackageListItemEvent) -> Unit,
    content: InfoPanelContent.PackageInfo,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().padding(start = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PackageTitle(modifier = Modifier.weight(1f), content.title, content.subtitle)
            Actions(content, onPackageEvent)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (content is InfoPanelContent.PackageInfo.Declared) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    LabelInfo(
                        modifier = Modifier.defaultMinSize(90.dp),
                        text = message("packagesearch.ui.toolwindow.packages.details.info.version")
                    )
                    VersionSelectionDropdown(
                        declaredVersion = content.declaredVersion,
                        availableVersions = content.availableVersions,
                        latestVersion = content.latestVersion,
                        enabled = !content.isLoading,
                    ) {
                        onPackageEvent(SetPackageVersion(content.packageListId, it))
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    LabelInfo(
                        modifier = Modifier.defaultMinSize(90.dp),
                        text = message("packagesearch.ui.toolwindow.packages.details.info.scope")
                    )
                    ScopeSelectionDropdown(
                        declaredScope = content.declaredScope,
                        availableScopes = content.availableScopes,
                        enabled = !content.isLoading,
                        allowMissingScope = content.allowMissingScope,
                    ) {
                        onPackageEvent(SetPackageScope(content.packageListId, it))
                    }
                }
            }

            content.type?.let { PackageType(it) }
            if (content.repositories.isNotEmpty()) {
                DetailLabel(
                    name = message("packagesearch.ui.toolwindow.packages.details.info.repositories"),
                    value = content.repositories.map { it.name }.joinToString(", ")
                )
            }
            if (content.licenses.isNotEmpty()) {
                PackageLinks(content.licenses, onLinkClick)
            }
            if (content.authors.isNotEmpty()) {
                DetailLabel(
                    name = message("packagesearch.ui.toolwindow.packages.details.info.authors"),
                    value = content.authors.joinToString(", ")
                )
            }
            content.description?.let { description ->
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = description.trimStart(),
                    textAlign = TextAlign.Start,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            content.scm?.let {
                ScmLinks(it, onLinkClick)
            }

            content.readmeUrl?.let { readmeUrl ->
                ExternalLink(
                    text = message("packagesearch.ui.toolwindow.link.readme.capitalized"),
                    onClick = {
                        onLinkClick(readmeUrl)
                        logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.Readme)
                    }
                )
            }
        }
    }
}

@Composable
private fun Actions(
    tabContent: InfoPanelContent.PackageInfo,
    onPackageEvent: (PackageListItemEvent) -> Unit,
) {
    var isPopupOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (tabContent) {
            is InfoPanelContent.PackageInfo.Declared -> {
                val latestVersion = tabContent.latestVersion
                if (latestVersion != null) {
                    OutlinedButton(
                        onClick = { onPackageEvent(SetPackageVersion(tabContent.packageListId, latestVersion)) },
                        enabled = !tabContent.isLoading,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Text(text = message("packagesearch.ui.toolwindow.packages.actions.update"))
                    }
                }
                when {
                    tabContent.isLoading -> CircularProgressIndicator()
                    else -> DeclaredPackageActionPopup(
                        isOpen = isPopupOpen,
                        onDismissRequest = { isPopupOpen = false },
                        onOpenPopupRequest = { isPopupOpen = true },
                        onGoToSource = { onPackageEvent(GoToSource(tabContent.packageListId)) },
                        onRemove = { onPackageEvent(Remove(tabContent.packageListId)) },
                    )
                }
            }

            is InfoPanelContent.PackageInfo.Remote.Base -> {
                OutlinedButton(
                    onClick = {
                        val tabContentId = tabContent.packageListId
                        val event = PackageListItemEvent.OnPackageAction.Install.Base(
                            headerId = tabContentId.headerId,
                            eventId = tabContentId,
                        )
                        onPackageEvent(event)
                    },
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(text = message("packagesearch.ui.toolwindow.packages.actions.install"))
                }
            }

            is InfoPanelContent.PackageInfo.Remote.WithVariant -> {
                OutlinedButton(
                    onClick = {
                        val tabContentId = tabContent.packageListId
                        val event = WithVariant(
                            headerId = tabContentId.headerId,
                            eventId = tabContentId,
                            selectedVariantName = tabContent.primaryVariant
                        )
                        onPackageEvent(event)
                    },
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(text = message("packagesearch.ui.toolwindow.packages.actions.install"))
                }
                RemotePackageWithVariantsActionPopup(
                    isOpen = isPopupOpen,
                    primaryVariantName = tabContent.primaryVariant,
                    additionalVariants = tabContent.additionalVariants,
                    onOpenPopupRequest = { isPopupOpen = true },
                    onDismissRequest = { isPopupOpen = false },
                    onInstall = {
                        onPackageEvent(
                            WithVariant(
                                eventId = tabContent.packageListId,
                                headerId = tabContent.packageListId.headerId,
                                selectedVariantName = it
                            )
                        )
                    },
                    isInstalledInPrimaryVariant = tabContent.isInstalledInPrimaryVariant,
                )

            }
        }
    }
}

@Composable
private fun PackageType(type: InfoPanelContent.PackageInfo.Type) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LabelInfo(
            modifier = Modifier.defaultMinSize(90.dp),
            text = message("packagesearch.ui.toolwindow.packages.columns.type")
        )
        val iconPath = if (JewelTheme.isDark) type.icon.darkIconPath else type.icon.lightIconPath

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(iconPath, null, IconProvider::class.java)
            Text(type.name)
        }

    }
}

@Composable
private fun DetailLabel(name: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LabelInfo(
            modifier = Modifier.defaultMinSize(90.dp, 16.dp),
            text = name
        )
        Text(value)
    }
}

@Composable
private fun ScmLinks(
    scm: InfoPanelContent.PackageInfo.Scm,
    onLinkClick: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExternalLink(
            text = message("packagesearch.ui.toolwindow.link.github"),
            onClick = {
                logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.Scm)
                onLinkClick(scm.url)
            },
        )
        if (scm is InfoPanelContent.PackageInfo.Scm.GitHub) {
            Icon(resource = "icons/Rating.svg", contentDescription = null, IconProvider::class.java)
            LabelInfo(scm.stars.toString())
        }
    }
}

@Composable
private fun PackageTitle(
    modifier: Modifier = Modifier,
    name: String?,
    id: String,
) {
    Row(
        modifier = modifier.padding(12.dp, 12.dp, 4.dp, 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name ?: id, fontWeight = FontWeight.Bold)
            if (name != null) LabelInfo(id)
        }
    }
}

@Composable
private fun PackageLinks(
    licenses: List<InfoPanelContent.PackageInfo.License>,
    onLinkClick: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LabelInfo(
            modifier = Modifier.defaultMinSize(90.dp),
            text = message("packagesearch.ui.toolwindow.packages.details.info.licenses")
        )
        licenses.forEachIndexed { index, license ->
            when (license.url) {
                null -> Text(license.name)
                else -> ExternalLink(
                    text = license.name,
                    onClick = {
                        onLinkClick(license.url)
                        logDetailsLinkClick(FUSGroupIds.DetailsLinkTypes.License)
                    },
                )
            }
            if (index != licenses.lastIndex) {
                Text(",")
            }
        }
    }
}
