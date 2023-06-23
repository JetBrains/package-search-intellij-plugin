@file:Suppress("UnstableApiUsage")

package org.jetbrains.packagesearch.plugin.gradle

import com.intellij.openapi.module.Module
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.search.buildPackagesType
import org.jetbrains.packagesearch.api.v3.search.javaApi
import org.jetbrains.packagesearch.api.v3.search.javaRuntime
import org.jetbrains.packagesearch.api.v3.search.libraryElements
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredPackage
import org.jetbrains.packagesearch.plugin.core.data.PackageSearchModule
import org.jetbrains.packagesearch.plugin.core.extensions.PackageSearchModuleBuilderContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension

class GradleModuleTransformer : BaseGradleModuleTransformer() {

    override suspend fun Module.transform(
        context: PackageSearchModuleBuilderContext,
        model: PackageSearchGradleModel,
        buildFile: Path?,
    ): PackageSearchGradleModule {

    }

}
