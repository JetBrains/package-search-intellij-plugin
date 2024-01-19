package com.jetbrains.packagesearch.plugin.ui.panels.side

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchModuleVariant
import com.jetbrains.packagesearch.plugin.ui.bridge.AttributeBadge
import com.jetbrains.packagesearch.plugin.ui.bridge.FlowRow
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.model.infopanel.InfoPanelContent
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text

@Composable
fun HeaderAttributesTab(
    content: InfoPanelContent.Attributes,
    scrollState: ScrollState,
) {
    HeaderAttributesTabImpl(
        content = content,
        scrollState = scrollState,
        contentTitle = PackageSearchBundle.message("packagesearch.ui.toolwindow.sidepanel.searchResultSupport"),
        attributeTypeName = PackageSearchBundle.message("packagesearch.ui.toolwindow.sidepanel.platformsList"),
        sourceSetString = PackageSearchBundle.message("packagesearch.ui.toolwindow.sidepanel.searchResultSupport.sourceSets"),
    )
}

@Composable
private fun HeaderAttributesTabImpl(
    content: InfoPanelContent.Attributes,
    contentTitle: String,
    scrollState: ScrollState,
    attributeTypeName: String,
    sourceSetString: String,
) {
    val scope = rememberCoroutineScope()
    val attributes = content.attributes
    // Global positions of attributes will be used to store the y offset used on scrollToItem
    val attributeGlobalPositionMap = remember { mutableMapOf<Int, Int>() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
        if (content is InfoPanelContent.Attributes.FromSearchHeader) {
            Text(
                text = contentTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        FlowRow(xSpacing = 4.dp) {
            attributes.forEachIndexed { index, attribute ->
                AttributeBadge(text = attribute.value) {
                    scope.scrollToAttribute(scrollState, attributeGlobalPositionMap, index)
                }
            }
        }

        if (content is InfoPanelContent.Attributes.FromSearchHeader) {
            SourceSetsList(content, sourceSetString)
        }

        AttributeItems(attributeTypeName = attributeTypeName, attributes, attributeGlobalPositionMap)
    }
}


@Composable
private fun SourceSetsList(
    content: InfoPanelContent.Attributes.FromSearchHeader,
    title: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = content.defaultSourceSet)
            LabelInfo(PackageSearchBundle.message("packagesearch.ui.toolwindow.variant.default.text"))
        }
        Text(text = content.additionalSourceSets.joinToString(", "))

    }
}

@Composable
private fun AttributeItems(
    attributeTypeName: String,
    attributesName: List<PackageSearchModuleVariant.Attribute>,
    attributeGlobalPosition: MutableMap<Int, Int>,
) {
    Text(
        text = attributeTypeName,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 4.dp)
    )

    attributesName.forEachIndexed { index, attribute ->
        AttributeItem(
            modifier = Modifier.onGloballyPositioned {
                attributeGlobalPosition[index] = it.positionInParent().y.roundToInt()
            },
            attributeName = attribute.value,
            nestedAttributesName = attribute.flatten()
        )
    }
}

private fun PackageSearchModuleVariant.Attribute.flatten(): List<String> =
    when (this) {
        is PackageSearchModuleVariant.Attribute.NestedAttribute -> children.flatMap { it.flatten() }
        is PackageSearchModuleVariant.Attribute.StringAttribute -> listOf(value)
    }


private fun CoroutineScope.scrollToAttribute(
    scrollState: ScrollState,
    attributeGlobalPosition: MutableMap<Int, Int>,
    index: Int,
) {
    launch {
        scrollState.animateScrollTo(
            value = attributeGlobalPosition[index] ?: 0,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessVeryLow
            )
        )
    }
}

@Composable
fun AttributeItem(modifier: Modifier = Modifier, attributeName: String, nestedAttributesName: List<String>) {

    Row(modifier = modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.width(160.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = attributeName)
            LabelInfo(nestedAttributesName.size.toString())
        }

        Text(text = nestedAttributesName.joinToString("\n"))
    }
}


//@Preview
//@Composable
//private fun HeaderAttributesPreviewTab() {
//    val activeTabMock = InfoPanelContent.Attributes.FromVariant(
//        tabTitleData = "FromVariant",
//        variantName = "jvm",
//        attributes = generateAttributesMock()
//    )
//    val scrollStateMock = ScrollState(0)
//    Column(Modifier.padding(8.dp)) {
//        IntUiTheme(true) {
//            Column(Modifier.background(LocalGlobalColors.current.paneBackground).padding(16.dp)) {
//                HeaderAttributesTabImpl(
//                    content = activeTabMock,
//                    scrollState = scrollStateMock,
//                    contentTitle = "FromSearch results that support:",
//                    attributeTypeName = "Platforms:",
//                    sourceSetString = "Source Sets:"
//                )
//
//            }
//
//            Divider(orientation = Orientation.Horizontal, modifier = Modifier.padding(vertical = 16.dp))
//
//        }
//
//
//        IntUiTheme(false) {
//            Column(Modifier.background(LocalGlobalColors.current.paneBackground).padding(16.dp)) {
//                HeaderAttributesTabImpl(
//                    content = activeTabMock,
//                    scrollState = scrollStateMock,
//                    contentTitle = "FromSearch results that support:",
//                    attributeTypeName = "Platforms:",
//                    sourceSetString = "Source Sets:"
//                )
//
//            }
//        }
//    }
//}

private fun generateAttributesMock(): List<PackageSearchModuleVariant.Attribute> {
    val attributes = mutableListOf<PackageSearchModuleVariant.Attribute>()

    platformListMock.take(Random.nextInt(2, 10)).forEach {
        attributes.add(
            if (Random.nextBoolean()) {
                PackageSearchModuleVariant.Attribute.NestedAttribute(
                    it,
                    platformListMock.take(Random.nextInt(2, 10)).map {
                        PackageSearchModuleVariant.Attribute.StringAttribute(it)
                    }
                )


            } else {

                PackageSearchModuleVariant.Attribute.StringAttribute(it)
            }
        )
    }

    return attributes

}

internal val platformListMock
    get() = buildList {
        add("Android")
        add("android")
        add("Apple")
        add("iOS")
        add("iosX64")
        add("iosArm64")
        add("iosSimulatorArm64")
        add("macOS")
        add("macosArm64")
        add("macosX64")

        add("watchOS")
        add("watchosArm32")
        add("watchosArm64")
        add("watchosX64")
        add("watchosSimulatorArm64")

        add("tvOS")
        add("tvosX64")
        add("tvosArm64")
        add("tvosSimulatorArm64")


        add("Java")
        add("jvm")

        add("JavaScript")
        add("jsLegacy")
        add("jslr")

        add("Linux")
        add("LinuxMipsel32")
        add("LinuxArm64")
        add("LinuxArm32Hfp")
        add("LinuxX64")

        add("Windows")
        add("WindowsX64")
        add("WindowsX86")

        add("WebAssembly")
        add("wasm")
        add("wasm32")

    }