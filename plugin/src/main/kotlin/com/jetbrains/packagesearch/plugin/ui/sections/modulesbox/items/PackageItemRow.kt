package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalProjectService
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import kotlinx.coroutines.launch
import org.jetbrains.jewel.Divider
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.Link
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.painterResource
import org.jetbrains.jewel.styling.LocalLazyTreeStyle
import org.jetbrains.jewel.styling.LocalLinkStyle
import org.jetbrains.jewel.util.appendIf
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import kotlin.math.roundToInt

@Suppress("unused")
enum class PackageQuality {
    Unknown, Bad, Warning, Good
}

fun PackageQuality.getIconResourcePath() = "icons/intui/quality/${name.lowercase()}.svg"

@Composable
internal fun DeclaredPackageMoreActionPopup(
    dependencyManager: PackageSearchDependencyManager,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    borderColor: Color = pickComposeColorFromLaf("OnePixelDivider.background"),
    onDismissRequest: () -> Unit,
) {
    val context = LocalProjectService.current
    Column(
        Modifier
            .padding(vertical = 4.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            modifier = Modifier.width(176.dp),
            text = "Other Actions",
            textAlign = TextAlign.Center,
            softWrap = false,
        )
        Divider(color = borderColor)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            var isActionPerforming by LocalIsActionPerformingState.current
            Link(
                resourceLoader = LocalResourceLoader.current,
                text = "Remove",
                enabled = !isActionPerforming,
                onClick = {
                    isActionPerforming = true
                    context.coroutineScope.launch {
                        dependencyManager.removeDependency(
                            context,
                            packageSearchDeclaredPackage.getRemoveData(),
                        )
                    }
                        .invokeOnCompletion { onDismissRequest() }
                },
            )
        }
    }
}

@Composable
fun PackageRow(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    isSelected: Boolean,
    isCompact: Boolean,
    packageIcon: Painter?,
    actionPopupId: String,
    packageNameContent: (@Composable RowScope.() -> Unit),
    editPackageContent: (@Composable RowScope.() -> Unit)? = null,
    mainActionContent: (@Composable () -> Unit)? = null,
    popupContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(
                when {
                    isSelected && isActive -> LocalLazyTreeStyle.current.colors.elementBackgroundSelectedFocused
                    isSelected && !isActive -> LocalLazyTreeStyle.current.colors.elementBackgroundSelected
                    else -> Color.Transparent
                },
            ).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(16.dp)) {
                if (packageIcon != null) {
                    Icon(
                        painter = packageIcon,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = null,
                    )
                }
            }
            packageNameContent()
        }
        Row {
            if (!isCompact && editPackageContent != null) {
                editPackageContent()
            }
            // enable when package quality will be live
//            Icon(
//                painterResource(packageSearchQuality.getIconResourcePath(), LocalResourceLoader.current),
//                contentDescription = null
//            )
            Row(
                Modifier
                    .defaultMinSize(90.dp, 16.dp)
                    .padding(start = 8.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.width(74.dp), horizontalArrangement = Arrangement.End) {
                    mainActionContent?.invoke()
                }
                var hovered by remember(key1 = actionPopupId) { mutableStateOf(false) }
                var globalPopupId by LocalGlobalPopupIdState.current
                Box(
                    Modifier
                        .defaultMinSize(16.dp, 16.dp)
                        .appendIf(hovered || globalPopupId == actionPopupId) {
                            background(pickComposeColorFromLaf("ActionButton.hoverBackground"))
                                .border(1.dp, pickComposeColorFromLaf("ActionButton.hoverBorderColor"))
                        }
                        .appendIf(popupContent != null) {
                            pointerInput(key1 = actionPopupId) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Enter -> hovered = true
                                            PointerEventType.Exit -> hovered = false
                                            PointerEventType.Press -> {
                                                globalPopupId =
                                                    if (globalPopupId == actionPopupId) {
                                                        null
                                                    } else {
                                                        actionPopupId
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    if (popupContent != null) {
                        Icon(
                            painterResource("icons/intui/moreVertical.svg", LocalResourceLoader.current),
                            contentDescription = null,
                        )
                        if (globalPopupId == actionPopupId) {
                            val borderColor = pickComposeColorFromLaf("OnePixelDivider.background")

                            val bgColor = pickComposeColorFromLaf("PopupMenu.background")
                            val contentOffsetX = with(LocalDensity.current) { 184.dp.toPx() + 1 }

                            Popup(
                                offset = IntOffset(-contentOffsetX.roundToInt(), 32),
                                onDismissRequest = { globalPopupId = null },
                                properties = PopupProperties(focusable = true),
                                onPreviewKeyEvent = { false },
                                onKeyEvent = { false },
                            ) {
                                Box(
                                    modifier =
                                    Modifier.width(200.dp)
                                        .clip(shape = RoundedCornerShape(10.dp))
                                        .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
                                        .background(color = bgColor),
                                ) {
                                    popupContent()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpgradePackageActionLink(
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    dependencyManager: PackageSearchDependencyManager,
) {
    val upgrade = packageSearchDeclaredPackage.evaluateUpgrade() ?: return
    PackageActionLink("Upgrade") { context ->
        dependencyManager.updateDependencies(
            context = context,
            data = listOf(
                packageSearchDeclaredPackage.getUpdateData(
                    newVersion = upgrade.versionName,
                    newScope = packageSearchDeclaredPackage.scope,
                ),
            ),
        )
    }
}

@Composable
fun InstallPackageActionLink(
    apiPackage: ApiPackage,
    module: PackageSearchModule.WithVariants,
    variant: PackageSearchModuleVariant,
    dependencyManager: PackageSearchDependencyManager,
) {
    val version = apiPackage.latestVersion
    PackageActionLink("Add") { context ->
        dependencyManager.addDependency(
            context = context,
            data = variant.getInstallData(
                apiPackage = apiPackage,
                selectedVersion = version.versionName,
                selectedScope = module.defaultScope,
            ),
        )
    }
}

@Composable
fun PackageActionLink(
    text: String,
    action: suspend (PackageSearchKnownRepositoriesContext) -> Unit,
) {
    var showProgress by remember { mutableStateOf(false) }
    var isActionPerforming by LocalIsActionPerformingState.current
    val service = LocalProjectService.current
    when {
        showProgress -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 1.dp,
            color = LocalLinkStyle.current.colors.contentDisabled,
        )

        else -> Link(
            resourceLoader = LocalResourceLoader.current,
            enabled = !isActionPerforming,
            text = text, // TODO localize
            onClick = {
                isActionPerforming = true
                showProgress = true
                service.coroutineScope.launch {
                    action(service)
                }.invokeOnCompletion { showProgress = false }
            },
        )
    }
}

@Composable
fun InstallPackageActionLink(
    apiPackage: ApiPackage,
    module: PackageSearchModule.Base,
    dependencyManager: PackageSearchDependencyManager,
) {
    val version = apiPackage.latestVersion
    PackageActionLink("Add") { context ->
        dependencyManager.addDependency(
            context = context,
            data = module.getInstallData(
                apiPackage = apiPackage,
                selectedVersion = version.versionName,
                selectedScope = module.defaultScope,
            ),
        )
    }
}

fun PackageSearchDeclaredPackage.evaluateUpgrade(stableOnly: Boolean): NormalizedVersion? {
    val targetVersion = if (stableOnly) latestStableVersion else latestVersion
    return if (targetVersion?.let { declaredVersion < it } == true) targetVersion else null
}

@Composable
fun PackageSearchDeclaredPackage.evaluateUpgrade(): NormalizedVersion? =
    evaluateUpgrade(LocalIsOnlyStableVersions.current.value)

fun ApiPackage.getLatestVersion(stableOnly: Boolean): NormalizedVersion =
    versions.latestStable?.normalized?.takeIf { stableOnly } ?: versions.latest.normalized

val ApiPackage.latestVersion: NormalizedVersion
    @Composable
    get() = getLatestVersion(LocalIsOnlyStableVersions.current.value)
