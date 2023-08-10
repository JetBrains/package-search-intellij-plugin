package org.jetbrains.packagesearch.plugin.ui.sections.modulesbox.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.onClick
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.*
import org.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import org.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ModulesHeader(
    moduleName: String,
    toggleCollapse: () -> Unit,
    badges: List<Pair<String, () -> Unit>>,
    groupSize: Int,
    isGroupExpanded: Boolean,
    collectiveActionItemCount: Int = 0,
    availableCollectiveCallback: Pair<String, () -> Unit>? = null,
    isActionPerforming: MutableState<Boolean>,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(1.dp)
            .height(28.dp)
            .background(pickComposeColorFromLaf("WelcomeScreen.separatorColor")),
        horizontalArrangement = SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .onClick {
                        toggleCollapse()
                    }
            ) {
                val iconResource =
                    remember(isGroupExpanded) { if (!isGroupExpanded) "icons/intui/chevronRight.svg" else "icons/intui/chevronDown.svg" }
                Icon(painterResource(iconResource, LocalResourceLoader.current), tint = Color.Gray)
            }
            Text(
                modifier = Modifier.padding(start = 4.dp),
                fontWeight = FontWeight.Bold,
                text = moduleName,
            )
            LabelInfo(
                modifier = Modifier.padding(horizontal = 4.dp),
                text = groupSize.toString(),
            )
            badges.forEach {
                //chips when are ready todo
//                BaseChip(
//                    Color.LightGray
//                ) {
//                    Box(
//                        Modifier.onClick { it.second() }
//                    ) {
//                        Text(
//                            text = it.first,
//                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
//                        )
//                    }
//                }
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (collectiveActionItemCount > 0) {
                availableCollectiveCallback?.let {
                    Link(
                        resourceLoader = LocalResourceLoader.current,
                        text = "${it.first} ($collectiveActionItemCount)",
                        onClick = { availableCollectiveCallback.second.invoke() }
                    )
                }
            }
        }
    }
}
