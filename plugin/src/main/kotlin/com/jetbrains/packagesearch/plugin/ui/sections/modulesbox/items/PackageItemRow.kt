package com.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items

import ai.grazie.utils.mpp.UUID
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import com.intellij.ui.JBColor
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchKnownRepositoriesContext
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.LocalGlobalPopupIdState
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalIsOnlyStableVersions
import com.jetbrains.packagesearch.plugin.ui.LocalProjectService
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import com.jetbrains.packagesearch.plugin.ui.bridge.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.Divider
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.IntelliJTheme
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
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
enum class PackageQuality {
    Unknown, Bad, Warning, Good
}

fun PackageQuality.getIconResourcePath() = "icons/intui/quality/${name.lowercase()}.svg"

@Composable
internal fun DeclaredPackageMoreActionPopup(
    dependencyManager: PackageSearchDependencyManager,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    borderColor: Color = remember(IntelliJTheme.isDark) { pickComposeColorFromLaf("OnePixelDivider.background") },
) {
    val context = LocalProjectService.current
    val popupOpenStatus = LocalGlobalPopupIdState.current
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
            val service = LocalProjectService.current
            val isActionPerforming = LocalIsActionPerformingState.current
            Link(
                resourceLoader = LocalResourceLoader.current,
                text = "Remove",
                enabled = !isActionPerforming.value.isPerforming,
                onClick = {
                    val id = UUID.random().text
                    isActionPerforming.value = ActionState(true, id)
                    context.coroutineScope.launch {
                        dependencyManager.removeDependency(
                            context,
                            packageSearchDeclaredPackage.getRemoveData(),
                        )
                    }.invokeOnCompletion {
                        it?.let {
                            System.err.println(it.stackTraceToString())
                        }
                        popupOpenStatus.value = null
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
            Row(modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {editPackageContent()}
        }
        Row {
            // enable when package quality will be live
//            Icon(
//                painterResource(packageSearchQuality.getIconResourcePath(), LocalResourceLoader.current),
//                contentDescription = null
//            )
            Row(
                Modifier
                    .defaultMinSize(90.dp, 16.dp)
                    .padding(start = 8.dp, end = 4.dp)
                ,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.defaultMinSize(42.dp), horizontalArrangement = Arrangement.End) {
                    mainActionContent?.invoke()
                }
                var hovered by remember(key1 = actionPopupId) { mutableStateOf(false) }
                var globalPopupId by LocalGlobalPopupIdState.current
                val bgColor = remember(IntelliJTheme.isDark) { JBColor.background().toComposeColor() }
                val borderColor = remember(IntelliJTheme.isDark) { JBColor.border().toComposeColor() }
                Box(
                    Modifier
                        .defaultMinSize(16.dp, 16.dp)
                        .appendIf(hovered || globalPopupId == actionPopupId) {
                            background(bgColor)
                                .border(1.dp, borderColor)
                        }
                        .appendIf(popupContent != null) {
                            pointerInput(key1 = actionPopupId, key2 = IntelliJTheme.globalColors) {
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
                            painterResource("actions/more.svg", LocalResourceLoader.current),
                            contentDescription = null,
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
    val service = LocalProjectService.current
    when {
        showProgress -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 1.dp,
            color = LocalLinkStyle.current.colors.contentDisabled,
        )

        else -> Link(
            resourceLoader = LocalResourceLoader.current,
            enabled = !isActionPerforming.value.isPerforming,
            text = text, // TODO localize
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
