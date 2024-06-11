package com.jetbrains.packagesearch.plugin.tests.scripts

import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import org.w3c.dom.Document
import org.w3c.dom.Element

private val domFileName="builtinRegistry"

fun main(){
    scoutRegistryFiles().forEach {
        println("Patching file: $it")
        patchKotlinGradlePlugin(it)
    }
}

// WARNING: USE THIS FUNCTION WITH CAUTION AND ONLY FOR TESTS PURPOSES
internal fun scoutRegistryFiles(): List<Path> {
    println("Scouting registry files")
    val userDir = Path.of(System.getProperty("user.home"))
    println("current directory: $userDir")
    println("contains: ${userDir.listDirectoryEntries()}")
    println ("Scouting for gradle ide caches")
    val gradleDir= Path.of(userDir.toString(), ".gradle")
    println("gradle directory exists: ${gradleDir.exists()}")
    val cacheDir= Paths.get(gradleDir.toString(), "caches", "modules-2", "files-2.1", "com.jetbrains.intellij.idea", "ideaIC")
    println("idea cache directory exists: ${cacheDir.exists()}")

    //scout for the registry file
    val configsFiles= buildList {
        cacheDir.toFile().walk().forEach {
            if (it.name.contains(domFileName) && it.extension.lowercase().endsWith("xml")) {
                add(it.toPath())
            }
        }
    }

    println("Found ${configsFiles.size} registry files")
    configsFiles.forEach { println(it) }

    return configsFiles
}

internal fun patchKotlinGradlePlugin(xmlPath: Path) {
    if (!xmlPath.exists()) {
       error("can not find XML file to patch: $xmlPath")
    }

    val xmlFile = xmlPath.toFile()
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val document: Document = documentBuilder.parse(xmlFile)

    // Create the new plugin element with its attributes
    val pluginElement: Element = document.createElement("plugin")
    pluginElement.setAttribute("directoryName", "Kotlin")
    pluginElement.setAttribute("id", "org.jetbrains.kotlin")

    // Create the dependencies element
    val dependenciesElement: Element = document.createElement("dependencies")

    // List of dependency values
    val dependencyValues = listOf(
        "com.intellij.modules.platform",
        "com.intellij.modules.java",
        "com.intellij.modules.java-capable",
        "com.intellij.java"
    )

    // Add each dependency to the dependencies element
    for (dependencyValue in dependencyValues) {
        val dependencyElement: Element = document.createElement("dependency")
        dependencyElement.appendChild(document.createTextNode(dependencyValue))
        dependenciesElement.appendChild(dependencyElement)
    }

    // Append the dependencies element to the plugin element
    pluginElement.appendChild(dependenciesElement)

    // Append the plugin element to the root element
    document.documentElement.appendChild(pluginElement)

    // Save the updated document back to the file
    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    val source = DOMSource(document)
    val result = StreamResult(xmlFile)
    transformer.transform(source, result)

    println("XML file updated successfully.")
}