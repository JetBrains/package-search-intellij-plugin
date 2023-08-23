package org.jetbrains.packagesearch.plugin.ui.sections.infobox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.jewel.Chip
import org.jetbrains.jewel.LocalTextStyle
import org.jetbrains.jewel.Text
import org.jetbrains.packagesearch.plugin.ui.bridge.SpacingInnerElements
import org.jetbrains.packagesearch.plugin.ui.bridge.SpacingMainElements
import org.jetbrains.packagesearch.plugin.ui.bridge.TextHeaderMultiplier
import org.jetbrains.packagesearch.plugin.ui.bridge.TextSubHeaderMultiplier


@Composable
fun PlatformsTabContent() {
    Column(verticalArrangement = Arrangement.spacedBy(SpacingMainElements())) {
        Column(verticalArrangement = Arrangement.spacedBy(SpacingInnerElements())) {
            Text(
                text = "test",
                fontWeight = FontWeight.Bold,
                fontSize = LocalTextStyle.current.fontSize * TextHeaderMultiplier
            )
            listOf("apple","android").forEach { platform ->
                Chip(){
                    Text(text = platform)
                }
            }
        }
        Text(
            "Plaforms:", fontWeight = FontWeight.Bold,
            fontSize = LocalTextStyle.current.fontSize * TextSubHeaderMultiplier
        )
        //list platforms details

    }


}