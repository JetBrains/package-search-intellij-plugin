package org.jetbrains.packagesearch.gradle

import org.gradle.api.plugins.ExtensionContainer
import org.gradle.kotlin.dsl.findByType

fun String.containsAny(toDelete: List<String>) =
    toDelete.any { it in this }

inline fun <reified T : Any> ExtensionContainer.withType(function: T.() -> Unit) =
    findByType<T>()?.apply(function)