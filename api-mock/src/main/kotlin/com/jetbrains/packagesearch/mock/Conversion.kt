package com.jetbrains.packagesearch.mock

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.Author
import org.jetbrains.packagesearch.api.v3.VersionWithRepositories
import org.jetbrains.packagesearch.api.v3.VersionsContainer
import org.jetbrains.packagesearch.api.v3.Vulnerability
import org.jetbrains.packagesearch.gradle.File
import org.jetbrains.packagesearch.gradle.GradleMetadata
import org.jetbrains.packagesearch.gradle.Variant
import org.jetbrains.packagesearch.maven.Dependency
import org.jetbrains.packagesearch.maven.Developer
import org.jetbrains.packagesearch.maven.GoogleMavenCentralMirror
import org.jetbrains.packagesearch.maven.MavenMetadata
import org.jetbrains.packagesearch.maven.PomResolver
import org.jetbrains.packagesearch.maven.ProjectObjectModel
import org.jetbrains.packagesearch.maven.buildGradleMetadataUrl
import org.jetbrains.packagesearch.maven.dependencies
import org.jetbrains.packagesearch.maven.developers
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import org.jetbrains.packagesearch.gradle.Dependency as GradleMetadataDependency

fun Dependency.toApiModel() = version?.let {
    org.jetbrains.packagesearch.api.v3.Dependency(
        groupId = groupId,
        artifactId = artifactId,
        version = it,
        scope = scope,
    )
}

fun Developer.toApiModel() = Author(
    name = name,
    email = email,
    org = organization
)

fun Variant.toApiModel(): ApiMavenPackage.GradleVersion.ApiVariant = when {
    availableAt != null -> ApiMavenPackage.GradleVersion.ApiVariant.WithAvailableAt(
        name = name,
        attributes = attributes?.toApiAttributes() ?: emptyMap(),
        availableAt = ApiMavenPackage.GradleVersion.ApiVariant.WithAvailableAt.AvailableAt(
            url = availableAt!!.url,
            group = availableAt!!.group,
            module = availableAt!!.module,
            version = availableAt!!.version
        )
    )

    else -> ApiMavenPackage.GradleVersion.ApiVariant.WithFiles(
        name = name,
        attributes = attributes?.toApiAttributes() ?: emptyMap(),
        dependencies = dependencies?.map { it.toApiModel() } ?: emptyList(),
        files = files?.map { it.toApiModel() } ?: emptyList()
    )
}

fun Map<String, JsonPrimitive>.toApiAttributes() = mapValues { (name, value) ->
    ApiMavenPackage.GradleVersion.ApiVariant.Attribute.create(name, value.content)
}

fun GradleMetadataDependency.toApiModel() =
    ApiMavenPackage.ApiGradleDependency(
        group,
        module,
        version?.requires
    )

fun File.toApiModel() = ApiMavenPackage.GradleVersion.ApiVariant.File(
    name = name,
    url = url,
    size = size,
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

data class MavenCoordinateWithVersions(
    val groupId: String,
    val artifactId: String,
    val latestStable: Metadata?,
    val latest: Metadata,
    val allVersion: List<NormalizedVersion>,
) {
    data class Metadata(
        val version: NormalizedVersion,
        val pom: ProjectObjectModel,
        val gradleMetadata: GradleMetadata?,
        val artifacts: List<String>,
    )
}

suspend fun HttpClient.buildMetadata(
    pomSolver: PomResolver,
    groupId: String,
    artifactId: String,
    normalizedVersion: NormalizedVersion,
): MavenCoordinateWithVersions.Metadata? = coroutineScope {
    val latestVersionPomJob = async {
        pomSolver.getPom(groupId, artifactId, normalizedVersion.versionName)
    }
    val latestVersionGradleMetadataUrl = GoogleMavenCentralMirror.buildGradleMetadataUrl(
        groupId = groupId,
        artifactId = artifactId,
        version = normalizedVersion.versionName
    )
    val latestVersionGradleMetadata = runCatching { get(latestVersionGradleMetadataUrl).body<GradleMetadata>() }
        .getOrNull()
    val latestVersionPom = latestVersionPomJob.await() ?: return@coroutineScope null

    MavenCoordinateWithVersions.Metadata(
        version = normalizedVersion,
        pom = latestVersionPom,
        gradleMetadata = latestVersionGradleMetadata,
        artifacts = emptyList()
    )
}

suspend fun HttpClient.getBuildSystemsMetadata(
    metadata: MavenMetadata,
    pomSolver: PomResolver,
): MavenCoordinateWithVersions? = coroutineScope {
    val normalizedVersions = metadata.versioning?.versions?.version
        ?.map { NormalizedVersion.from(it.content) }
        ?: emptyList()

    val latest = normalizedVersions.max()
    val latestStable = normalizedVersions.filter { it.isStable }.maxOrNull()
    val groupId = metadata.groupId ?: return@coroutineScope null
    val artifactId = metadata.artifactId ?: return@coroutineScope null
    val latestJob =
        async { buildMetadata(pomSolver, groupId, artifactId, latest) }
    val latestStableVersion = latestStable
        ?.let { buildMetadata(pomSolver, groupId, artifactId, it) }

    val latestVersion = latestJob.await() ?: return@coroutineScope null

    MavenCoordinateWithVersions(
        groupId = groupId,
        artifactId = artifactId,
        latestStable = latestStableVersion,
        latest = latestVersion,
        allVersion = normalizedVersions
    )
}

fun MavenCoordinateWithVersions.toMavenApiModel(): ApiMavenPackage = ApiMavenPackage(
    id = "maven:$groupId:$artifactId",
    idHash = ApiPackage.hashPackageId("maven:$groupId:$artifactId"),
    rankingMetric = 20.0,
    versions = VersionsContainer(
        latestStable = latestStable?.toApiMavenVersion(),
        latest = latest.toApiMavenVersion(),
        all = allVersion.map { VersionWithRepositories(it, setOf("maven-central")) }
    ),
    groupId = groupId,
    artifactId = artifactId
)

fun MavenCoordinateWithVersions.Metadata.toApiMavenVersion() =
    when (gradleMetadata) {
        null -> ApiMavenPackage.MavenVersion(
            normalized = version,
            repositoryIds = setOf("maven-central"),
            vulnerability = Vulnerability(false),
            dependencies = pom.dependencies.mapNotNull { it.toApiModel() },
            artifacts = emptyList(),
            name = pom.name,
            description = pom.description,
            authors = pom.developers.map { it.toApiModel() },
            scmUrl = pom.scm?.url,
//                    licenses = pom.licenses.toApiModel()
        )

        else -> ApiMavenPackage.GradleVersion(
            normalized = version,
            repositoryIds = setOf("maven-central"),
            vulnerability = Vulnerability(false),
            dependencies = pom.dependencies.mapNotNull { it.toApiModel() },
            artifacts = emptyList(),
            variants = gradleMetadata.variants?.map { it.toApiModel() } ?: emptyList(),
            parentComponent = gradleMetadata.component?.url,
            name = pom.name,
            description = pom.description,
            authors = pom.developers.map { it.toApiModel() },
            scmUrl = pom.scm?.url,
//                    licenses = pom.licenses.toApiModel()
        )
    }

internal var HttpTimeout.HttpTimeoutCapabilityConfiguration.requestTimeout: Duration?
    get() = requestTimeoutMillis?.milliseconds
    set(value) {
        requestTimeoutMillis = value?.inWholeMilliseconds
    }

val MAVEN_CENTRAL_API_REPOSITORY
    get() = listOf<ApiRepository>(
        ApiMavenRepository(
            id = "maven-central",
            lastChecked = Clock.System.now(),
            url = "https://repo.maven.apache.org/maven2",
            alternateUrls = emptyList(),
            friendlyName = "Maven Central",
            userFacingUrl = null,
            packageCount = 0,
            artifactCount = 0,
            namedLinks = null
        )
    )