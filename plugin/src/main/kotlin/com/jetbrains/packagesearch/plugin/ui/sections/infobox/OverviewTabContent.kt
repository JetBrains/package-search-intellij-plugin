package org.jetbrains.packagesearch.plugin.ui.sections.infobox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.LocalProjectCoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jewel.*
import org.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import org.jetbrains.packagesearch.plugin.ui.bridge.TextSubHeaderMultiplier
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageQuality
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.PackageSearchAction
import org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items.getIconResourcePath


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PackageNameAndActions(
    packageName: String,
    packageId: String,
    packageQuality: PackageQuality = PackageQuality.Unknown,
    defaultAction: PackageSearchAction? = null,
    otherActions: List<PackageSearchAction> = listOf()
) {

    val scope = LocalProjectCoroutineScope.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                fontWeight = FontWeight.Bold,
                fontSize = LocalTextStyle.current.fontSize * TextSubHeaderMultiplier,
                text = packageName,
                modifier = Modifier.weight(1f, fill = false).padding(end = 4.dp)
            )
            Icon(painterResource(packageQuality.getIconResourcePath(), LocalResourceLoader.current))

            Row(
                Modifier.defaultMinSize(88.dp, 24.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (otherActions.isEmpty()) {
                    defaultAction?.let {
                        OutlinedButton(modifier = Modifier.defaultMinSize(88.dp, 24.dp), onClick = {
                            scope.launch {
                                defaultAction.action()
                            }.invokeOnCompletion {
                                it?.printStackTrace()
                            }
                        }) {
                            Text(
                                text = defaultAction.name,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    Dropdown(
                        menuContent = {
                            passiveItem {
                                Text(
                                    modifier = Modifier.width(176.dp),
                                    text = "Other Actions",
                                    textAlign = TextAlign.Center,
                                    softWrap = false
                                )
                            }
                            divider()
                            otherActions.forEach { action ->
                                passiveItem {
                                    Text(
                                        modifier = Modifier.defaultMinSize(64.dp, 28.dp)
                                            .clickable {
                                                scope.launch {
                                                    action.action()
                                                }.invokeOnCompletion {
                                                    it?.printStackTrace()
                                                }
                                            },
                                        text = action.name,
                                        softWrap = false,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        },
                        resourceLoader = LocalResourceLoader.current,
                        content = {
                            Text(
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        defaultAction?.action?.invoke() ?: otherActions.first().action()
                                    }.invokeOnCompletion {
                                        it?.printStackTrace()
                                    }
                                }, text = defaultAction?.name ?: otherActions.first().name,
                                softWrap = false,
                                textAlign = TextAlign.Center
                            )

                        })

                }
            }

        }
        LabelInfo(packageId)
    }
}
