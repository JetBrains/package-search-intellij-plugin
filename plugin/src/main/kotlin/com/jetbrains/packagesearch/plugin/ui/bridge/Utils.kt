package com.jetbrains.packagesearch.plugin.ui.bridge

import androidx.compose.ui.graphics.Color
import java.awt.Desktop
import java.net.URI
import javax.swing.UIDefaults
import javax.swing.UIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun java.awt.Color.toComposeColor(): Color {
    return Color(red, green, blue, alpha)
}

fun UIDefaults.getComposeColor(key: String): Color? {
    return getColor(key)?.toComposeColor()
}

fun CoroutineScope.openLinkInBrowser(url: String) {
    launch {
        Desktop.getDesktop()
            ?.takeIf { it.isSupported(Desktop.Action.BROWSE) }
            ?.browse(URI(url))
    }
}


fun pickComposeColorFromLaf(key: String) =
    UIManager.getLookAndFeelDefaults().getComposeColor(key) ?: Color.Unspecified


fun isLightTheme(): Boolean {
    val laf = UIManager.getLookAndFeelDefaults()
    val brightness = laf.getComposeColor("ToolWindow.background")?.getBrightness() ?: 200f
    return brightness < 128
}

private fun Color.getBrightness() = (red * 299 + green * 587 + blue * 114) / 1000

