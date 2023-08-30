package com.jetbrains.packagesearch.plugin

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.packageSearchBundle"

object PackageSearchBundle {
    private val bundle = DynamicBundle(PackageSearchBundle::class.java, BUNDLE)
    @Nls
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any
    ): String = bundle.getMessage(key, *params)
}