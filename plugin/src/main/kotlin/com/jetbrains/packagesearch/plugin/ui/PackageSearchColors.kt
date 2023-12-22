package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.ui.graphics.Color
import com.jetbrains.packagesearch.plugin.ui.bridge.pickComposeColorFromLaf

object PackageSearchColors {
    object Backgrounds {
        fun packageItemHeader(): Color =
                pickComposeColorFromLaf("ToolWindow.HeaderTab.selectedInactiveBackground")


        fun attributeBadge() = packageItemHeader()

    }


}