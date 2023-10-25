package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.plugin.core.data.IconProvider
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.ActionType
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.toComposeColor
import com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.getGlobalColorsWithTransparentFocusOverride
import com.jetbrains.packagesearch.plugin.utils.logWarn
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.styling.LocalLazyTreeStyle
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
    icon: String?,
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
            if (icon != null) {
                Icon(
                    resource = icon,
                    modifier = Modifier.size(16.dp),
                    contentDescription = null,
                    iconClass = IconProvider::class.java
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
                    LocalGlobalColors provides getGlobalColorsWithTransparentFocusOverride(),
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
                val bgColor = remember(JewelTheme.isDark) { JBColor.background().toComposeColor() }
                val borderColor = remember(JewelTheme.isDark) { JBColor.border().toComposeColor() }
                Box(Modifier.clip(RoundedCornerShape(2.dp))) {// do not remove this box! it is needed for popup to work without glitches
                    if (popupContent != null) {
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
                            resource = "actions/more.svg",
                            contentDescription = null,
                            iconClass = AllIcons::class.java
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
    actionType: ActionType,
    action: suspend (PackageSearchKnownRepositoriesContext) -> Unit,
) {
    var showProgress by remember { mutableStateOf(false) }
    val isActionPerforming = LocalIsActionPerformingState.current
    val service = LocalPackageSearchService.current
    when {
        showProgress -> CircularProgressIndicator(modifier = Modifier.size(16.dp))
        else -> Link(
            enabled = isActionPerforming.value?.isPerforming?.not() ?:true,
            text = text,
            onClick = {
                val id = UUID.randomUUID().toString()
                isActionPerforming.value = ActionState(true, actionType, id)
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
                    if (isActionPerforming.value?.actionId == id) {
                        logWarn("Package Action has been cancelled due a time out")
                        isActionPerforming.value = null
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
