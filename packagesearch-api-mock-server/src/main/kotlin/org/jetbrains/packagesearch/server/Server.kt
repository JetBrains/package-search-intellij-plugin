package org.jetbrains.packagesearch.server

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.*
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage.ApiVariant.WithAvailableAt
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage.ApiVariant.WithAvailableAt.AvailableAt
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage.ApiVariant.WithFiles
import org.jetbrains.packagesearch.api.v3.ApiGradlePackage.GradleVersion
import org.jetbrains.packagesearch.api.v3.Licenses
import org.jetbrains.packagesearch.gradle.File
import org.jetbrains.packagesearch.gradle.GradleMetadata
import org.jetbrains.packagesearch.gradle.Variant
import org.jetbrains.packagesearch.maven.*
import org.jetbrains.packagesearch.maven.Dependency
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.gradle.Dependency as GradleMetadataDependency

suspend fun getPackages(httpClient: HttpClient, pomResolver: PomResolver) = flowOf(
    "https://repo1.maven.org/maven2/io/ktor/ktor-client-cio/2.3.0/ktor-client-cio-2.3.0",
    "https://repo1.maven.org/maven2/io/ktor/ktor-server-cio/2.3.0/ktor-server-cio-2.3.0",
)
    .scopedMap {
        val gradleRequest =
            async { httpClient.get("$it.module").body<GradleMetadata>() }
        val pomRequest =
            async { pomResolver.resolve(Url("$it.pom")) }
        PackageBuildFiles(
            gradleMetadata = gradleRequest.await(),
            pom = pomRequest.await()
        )
    }
    .map { it.toGradleApiPackage() }
    .toList()

fun <T, R> Flow<T>.scopedMap(transform: suspend CoroutineScope.(T) -> R): Flow<R> =
    map { coroutineScope { transform(it) } }

data class PackageBuildFiles(
    val gradleMetadata: GradleMetadata,
    val pom: ProjectObjectModel
)

private fun Dependency.toApiModel() = version?.let {
    org.jetbrains.packagesearch.api.v3.Dependency(
        groupId = groupId,
        artifactId = artifactId,
        version = it,
        scope = scope,
    )
}

fun PackageBuildFiles.toGradleApiPackage(): ApiPackage = ApiGradlePackage(
    id = "maven:${gradleMetadata.component.group}:${gradleMetadata.component.module}",
    idHash = ApiPackage.hashPackageId("maven:${gradleMetadata.component.group}:${gradleMetadata.component.module}"),
    name = gradleMetadata.component.module,
    description = null,
    authors = pom.developers.map { it.toApiModel() },
    scmUrl = pom.scm?.url,
    licenses = pom.licenses.toApiModel(),
    rankingMetric = 20.0,
    groupId = gradleMetadata.component.group,
    artifactId = gradleMetadata.component.module,
    versions = listOf(
        GradleVersion(
            normalized = NormalizedVersion.from(gradleMetadata.component.version, Clock.System.now()),
            repositoryIds = listOf("maven-central"),
            vulnerability = Vulnerability(
                isVulnerable = false,
                issues = emptyList()
            ),
            variants = gradleMetadata.variants?.map { it.toApiModel() } ?: emptyList(),
            parentComponent = gradleMetadata.component.url,
            dependencies = pom.dependencies.mapNotNull { it.toApiModel() },
            artifacts = emptyList()
        )
    )
)

private fun List<License>.toApiModel() = first().toApiModel()?.let {
    Licenses(
        it,
        drop(1).mapNotNull { it.toApiModel() }
    )
}

private fun License.toApiModel() = url?.let {
    LicenseFile(
        name = name,
        url = it
    )
}

private fun Developer.toApiModel() = Author(
    name = name,
    email = email,
    org = organization
)


fun Variant.toApiModel(): ApiGradlePackage.ApiVariant = when {
    availableAt != null -> WithAvailableAt(
        name = name,
        attributes = attributes ?: emptyMap(),
        availableAt = AvailableAt(
            url = availableAt!!.url,
            group = availableAt!!.group,
            module = availableAt!!.module,
            version = availableAt!!.version
        )
    )

    else -> WithFiles(
        name = name,
        attributes = attributes ?: emptyMap(),
        dependencies = dependencies?.map { it.toApiModel() } ?: emptyList(),
        files = files?.map { it.toApiModel() } ?: emptyList()
    )
}

fun GradleMetadataDependency.toApiModel() =
    ApiGradlePackage.ApiGradleDependency(
        group,
        module,
        version.requires
    )

fun File.toApiModel() = ApiGradlePackage.ApiVariant.File(
    name = name,
    url = url,
    size = size.toLong(),
    sha256 = sha256,
    md5 = md5,
    sha512 = sha512,
    sha1 = sha1
)

fun ContentNegotiation.Config.xml(xml: XML) {
    val handler = KotlinxSerializationConverter(xml)
    register(ContentType.Application.Xml, handler)
    register(ContentType.Text.Xml, handler)
}