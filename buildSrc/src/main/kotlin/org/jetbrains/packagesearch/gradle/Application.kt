package org.intellij.jewel.workshop.build

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
@XmlSerialName("application")
data class Application(
    val components: List<Component>,
) {
    object Laf {
        val newUiStrings = listOf("ExperimentalDark", "ExperimentalLight")

        val DARK = Application(
            listOf(
                Component(
                    name = "LafManager",
                    autodetect = "true",
                    laf = Laf(
                        className = "com.intellij.ide.ui.laf.darcula.DarculaLaf",
                        themeId = "ExperimentalDark"
                    )
                )
            )
        )
        val LIGHT = Application(
            listOf(
                Component(
                    name = "LafManager",
                    autodetect = "true",
                    laf = Laf(
                        className = "com.intellij.ide.ui.laf.IntelliJLaf",
                        themeId = "ExperimentalLight"
                    )
                )
            )
        )
    }
}

@Serializable
@XmlSerialName("component")
data class Component(
    val name: String,
    val autodetect: String? = null,
    val options: List<Option>? = null,
    val entries: List<Entry>? = null,
    val laf: Laf? = null,
)

@Serializable
@XmlSerialName("option")
data class Option(val name: String, val value: String)

@Serializable
@XmlSerialName("entry")
data class Entry(val key: String, val value: String)

@Serializable
@XmlSerialName("laf")
data class Laf(
    @SerialName("class-name") val className: String,
    val themeId: String? = null,
)

val CDATA_PREFIX = "<![CDATA["
val CDATA_SUFFIX = "]]>"

@Serializable
@XmlSerialName("application")
data class LogSettings(
    val components: List<DebugComponent>,
)

@Serializable
@XmlSerialName("component")
data class DebugComponent(val name: String, @XmlValue val content: String)

@Serializable
data class DebugLog(val categories: List<Category>)
@Serializable
data class Category(val category: String, val level: String)