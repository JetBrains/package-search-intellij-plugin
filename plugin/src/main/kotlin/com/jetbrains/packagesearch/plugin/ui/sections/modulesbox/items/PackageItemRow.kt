package org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jetbrains.packagesearch.plugin.LocalProjectCoroutineScope
import com.jetbrains.packagesearch.plugin.LocalProjectService
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDependencyManager
import com.jetbrains.packagesearch.plugin.core.data.WithIcon
import com.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleData
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.getComposeColor
import com.jetbrains.packagesearch.plugin.ui.bridge.getPackageActions
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf
import javax.swing.UIManager
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.jewel.Divider
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.Link
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.painterResource
import org.jetbrains.jewel.styling.LocalLinkStyle
import org.jetbrains.packagesearch.api.v3.ApiPackage

@Suppress("unused")
enum class PackageQuality {
    Unknown, Bad, Warning, Good
}

fun PackageQuality.getIconResourcePath() = "icons/intui/quality/${name.lowercase()}.svg"

class PackageSearchAction(
    val name: String,
    private val _action: suspend () -> Unit
) {

    val action: suspend () -> Unit = {
        _action()
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PopupContent(
    otherActions: List<PackageSearchAction>,
    borderColor: Color,
    isActionPerforming: MutableState<Boolean>,
    dropDownItemIdOpen: MutableState<Any?>
) {
    val scope = LocalProjectCoroutineScope.current
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
            softWrap = false
        )
        Divider(color = borderColor)
        otherActions.forEachIndexed { index, action ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Link(
                    resourceLoader = LocalResourceLoader.current,
                    text = action.name,
                    enabled = !isActionPerforming.value,
                    onClick = {
                        isActionPerforming.value = true
                        scope.launch {
                            action.action()
                        }.invokeOnCompletion {
                            it?.printStackTrace()
                            isActionPerforming.value = false
                            dropDownItemIdOpen.value = null
                        }
                    })
            }
        }
    }
}

@Composable
fun RemotePackageRow(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    isSelected: Boolean,
    apiPackage: ApiPackage,
    dropDownItemIdOpen: MutableState<Any?>,
    dependencyManager: PackageSearchDependencyManager?,
    selectedModules: List<PackageSearchModuleData>,
    isActionPerforming: MutableState<Boolean>
) {
    val packageIconResource = remember {
        "icons/intui/" + "question.svg" //"gradle.svg" todo fix icons
    }
    val localProjectService = LocalProjectService.current

    val actions = remember {
        dependencyManager?.let {
            apiPackage.getPackageActions(selectedModules, localProjectService, dependencyManager)
        }
    }
    val packageSearchQuality = remember { PackageQuality.values().random() }
    PackageRowImpl(
        modifier = modifier,
        isActive = isActive,
        isSelected = isSelected,
        iconResource = packageIconResource,
        packageName = apiPackage.name ?: apiPackage.id,
        packageId = if (apiPackage.name.isNullOrEmpty()) "" else apiPackage.id,
        quality = packageSearchQuality,
        action = actions?.first,
        otherActions = actions?.second ?: emptyList(),
        dropDownItemIdOpen = dropDownItemIdOpen,
        isActionPerforming = isActionPerforming
    )
}


@Composable
fun LocalPackageRow(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    isSelected: Boolean,
    packageSearchDeclaredPackage: PackageSearchDeclaredPackage,
    dropDownItemIdOpen: MutableState<Any?>,
    selectedModules: List<PackageSearchModuleData>,
    isActionPerforming: MutableState<Boolean>
) {
    val packageIconResource = remember {
        when (val icon = packageSearchDeclaredPackage.icon) {
            is WithIcon.PathSourceType.ClasspathResources, is WithIcon.PathSourceType.File -> icon.path
            is WithIcon.PathSourceType.Network -> TODO("not implemented")
            is WithIcon.PathSourceType.Platform -> TODO("not implemented")
        }
    }
    val packageSearchQuality by remember { mutableStateOf(PackageQuality.values().random()) }

    val actions = packageSearchDeclaredPackage.getPackageActions(
        selectedModules = selectedModules, LocalProjectService.current
    )

    PackageRowImpl(
        modifier = modifier,
        isActive = isActive,
        isSelected = isSelected,
        iconResource = packageIconResource,
        packageName = packageSearchDeclaredPackage.displayName,
        packageId = packageSearchDeclaredPackage.id,
        quality = packageSearchQuality,
        action = actions.first,
        otherActions = actions.second,
        dropDownItemIdOpen = dropDownItemIdOpen,
        isActionPerforming = isActionPerforming
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PackageRowImpl(
    modifier: Modifier,
    isActive: Boolean,
    isSelected: Boolean,
    iconResource: String,
    packageName: String,
    packageId: String,
    quality: PackageQuality,
    action: PackageSearchAction? = null,
    otherActions: List<PackageSearchAction> = emptyList(),
    dropDownItemIdOpen: MutableState<Any?>,
    isActionPerforming: MutableState<Boolean>
) {
    val scope = LocalProjectCoroutineScope.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(
                when {
                    isSelected && isActive ->
                        pickComposeColorFromLaf("Tree.selectionBackground")

                    isSelected && !isActive ->
                        pickComposeColorFromLaf("Tree.selectionInactiveBackground")

                    else -> Color.Unspecified
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(iconResource, LocalResourceLoader.current), modifier = Modifier.size(16.dp))
            Text(text = packageName)
            LabelInfo(packageId)
        }
        Row(horizontalArrangement = Arrangement.End) {
            Icon(painterResource(quality.getIconResourcePath(), LocalResourceLoader.current))
            Row(
                Modifier
                    .defaultMinSize(90.dp, 16.dp)
                    .padding(start = 8.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showProgress by remember { mutableStateOf(false) }

                Row(Modifier.width(74.dp), horizontalArrangement = Arrangement.End) {
                    if (showProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 1.dp,
                            color = LocalLinkStyle.current.colors.contentDisabled
                        )
                    } else {
                        action?.let {
                            Link(
                                resourceLoader = LocalResourceLoader.current,
                                enabled = !isActionPerforming.value,
                                text = it.name,
                                onClick = {
                                    isActionPerforming.value = true
                                    showProgress = true
                                    scope.launch {
                                        it.action()
                                    }.invokeOnCompletion {
                                        it?.printStackTrace()
                                        isActionPerforming.value = false
                                        showProgress = false
                                    }
                                })
                        }
                    }

                }
                var hovered by remember(packageId) { mutableStateOf(false) }
                Box(
                    Modifier
                        .defaultMinSize(16.dp, 16.dp)
                        .then(
                            if (hovered || dropDownItemIdOpen.value == packageId) {
                                UIManager
                                    .getLookAndFeelDefaults()
                                    .getComposeColor("ActionButton.hoverBackground")
                                    ?.let {
                                        Modifier
                                            .background(it)
                                            .border(1.dp, it)
                                    } ?: Modifier
                            } else Modifier
                        )
                        .pointerInput(packageId) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Enter -> hovered = true
                                        PointerEventType.Exit -> hovered = false
                                        PointerEventType.Press -> {
                                            dropDownItemIdOpen.value =
                                                if (dropDownItemIdOpen.value == packageId) null else packageId
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Icon(painterResource("icons/intui/moreVertical.svg", LocalResourceLoader.current))
                    if (dropDownItemIdOpen.value == packageId) {
                        val borderColor = UIManager
                            .getLookAndFeelDefaults()
                            .getComposeColor("OnePixelDivider.background") ?: Color.Unspecified

                        val bgColor = UIManager
                            .getLookAndFeelDefaults()
                            .getComposeColor("PopupMenu.background") ?: Color.Unspecified
                        val contentOffsetX = with(LocalDensity.current) { 184.dp.toPx() + 1 }

                        Popup(
                            offset = IntOffset(-contentOffsetX.roundToInt(), 32),
                            onDismissRequest = { dropDownItemIdOpen.value = null },
                            properties = PopupProperties(focusable = true),
                            onPreviewKeyEvent = { false },
                            onKeyEvent = { false }
                        ) {
                            Box(
                                modifier =
                                Modifier.width(200.dp)
                                    .clip(shape = RoundedCornerShape(10.dp))
                                    .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
                                    .background(color = bgColor)
                            ) {
                                PopupContent(otherActions, borderColor, isActionPerforming, dropDownItemIdOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}