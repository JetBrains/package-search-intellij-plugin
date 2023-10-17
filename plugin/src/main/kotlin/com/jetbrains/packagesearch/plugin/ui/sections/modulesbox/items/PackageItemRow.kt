package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items

import ai.grazie.utils.mpp.UUID
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.toComposeColor
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.getGlobalColorsWithTrasparentFocusOverride
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.Link
import org.jetbrains.jewel.LocalGlobalColors
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.bridge.SwingBridgeService
import org.jetbrains.jewel.bridge.retrieveStatelessIcon
import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.styling.LocalLazyTreeStyle
import org.jetbrains.jewel.styling.LocalLinkStyle
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiPackageVersion
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion.Missing

@Suppress("unused")
enum class PackageQuality {
    Unknown, Bad, Warning, Good
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(2f).fillMaxWidth()) {
            if (packageIcon != null) {
                Icon(
                    painter = packageIcon,
                    modifier = Modifier.size(16.dp),
                    contentDescription = null,
                )
            } else Box(Modifier.size(16.dp)) {}
            packageNameContent()
        }
        if (!isCompact && editPackageContent != null) {
            Row(
                modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompositionLocalProvider(
                    LocalGlobalColors provides getGlobalColorsWithTrasparentFocusOverride(),
                ) {
                    editPackageContent()
                }
            }
        }
        Row(
            modifier = Modifier
                .defaultMinSize(90.dp, 16.dp)
                .padding(start = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // enable when package quality will be live
//            Icon(
//                painterResource(packageSearchQuality.getIconResourcePath(), LocalResourceLoader.current),
//                contentDescription = null
//            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.defaultMinSize(60.dp), horizontalArrangement = Arrangement.End) {
                    mainActionContent?.invoke()
                }
                var globalPopupId by LocalGlobalPopupIdState.current
                val bgColor = remember(IntelliJTheme.isDark) { JBColor.background().toComposeColor() }
                val borderColor = remember(IntelliJTheme.isDark) { JBColor.border().toComposeColor() }
                Box(Modifier.clip(RoundedCornerShape(2.dp))) {// do not remove this box! it is needed for popup to work without glitches
                    if (popupContent != null) {
                        val svgLoader = service<SwingBridgeService>().svgLoader
                        val painterProvider = retrieveStatelessIcon(
                            iconPath = "actions/more.svg",
                            svgLoader = svgLoader,
                            iconData = IntUiTheme.iconData
                        )
                        val painter by painterProvider.getPainter(LocalResourceLoader.current)
                        Icon(
                            modifier = Modifier
                                .clickable {
                                    globalPopupId = if (globalPopupId == actionPopupId) {
                                        null
                                    } else {
                                        actionPopupId
                                    }
                                }
                                .padding(2.dp),
                            painter = painter, contentDescription = null
                        )

                        if (globalPopupId == actionPopupId) {
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
                                        .border(
                                            width = 1.dp,
                                            color = borderColor,
                                            shape = RoundedCornerShape(10.dp)
                                        )
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
fun PackageActionLink(
    text: String,
    action: suspend (PackageSearchKnownRepositoriesContext) -> Unit,
) {
    var showProgress by remember { mutableStateOf(false) }
    val isActionPerforming = LocalIsActionPerformingState.current
    val service = LocalPackageSearchService.current
    when {
        showProgress -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 1.dp,
            color = LocalLinkStyle.current.colors.contentDisabled,
        )

        else -> Link(
            resourceLoader = LocalResourceLoader.current,
            enabled = !isActionPerforming.value.isPerforming,
            text = text,
            onClick = {
                val id = UUID.random().text
                isActionPerforming.value = ActionState(true, id)
                showProgress = true
                service.coroutineScope.launch {
                    action(service)
                }.invokeOnCompletion {
                    it?.let {
                        System.err.println(it.stackTraceToString())
                    }
                    showProgress = false
                }
                service.coroutineScope.launch {
                    delay(5.seconds)
                    if (isActionPerforming.value.actionId == id) {
                        System.err.println("Remove action has been cancelled due a time out")
                        isActionPerforming.value = ActionState(false)
                    }
                }
            },
        )
    }
}

fun PackageSearchDeclaredPackage.evaluateUpgrade(stableOnly: Boolean): NormalizedVersion? {
    val targetVersion = if (stableOnly) latestStableVersion else latestVersion
    return if (declaredVersion !is Missing && declaredVersion < targetVersion) targetVersion else null
}

@Composable
fun PackageSearchDeclaredPackage.evaluateUpgrade(): NormalizedVersion? =
    evaluateUpgrade(LocalIsOnlyStableVersions.current.value)

fun ApiPackage.getLatestVersion(stableOnly: Boolean): ApiPackageVersion =
    versions.latestStable?.takeIf { stableOnly } ?: versions.latest

val ApiPackage.latestVersion: ApiPackageVersion
    @Composable
    get() = getLatestVersion(LocalIsOnlyStableVersions.current.value)
