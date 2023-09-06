package com.jetbrains.packagesearch.server

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.*
import org.jetbrains.packagesearch.api.v3.Licenses
import org.jetbrains.packagesearch.gradle.File
import org.jetbrains.packagesearch.gradle.GradleMetadata
import org.jetbrains.packagesearch.gradle.Variant
import org.jetbrains.packagesearch.maven.*
import org.jetbrains.packagesearch.maven.Dependency
import org.jetbrains.packagesearch.maven.central.MavenCentralApiResponse
import org.jetbrains.packagesearch.packageversionutils.normalization.NormalizedVersion
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.packagesearch.gradle.Dependency as GradleMetadataDependency


fun Dependency.toApiModel() = version?.let {
    org.jetbrains.packagesearch.api.v3.Dependency(
        groupId = groupId,
        artifactId = artifactId,
        version = it,
        scope = scope,
    )
}

fun List<License>.toApiModel() = firstOrNull()?.toApiModel()?.let {
    Licenses(
        it,
        drop(1).mapNotNull { it.toApiModel() }
    )
}

fun License.toApiModel() = url?.let {
    LicenseFile(
        name = name,
        url = it
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

data class MavenCoordinateWithVersions(
    val groupId: String,
    val artifactId: String,
    val versions: Map<String, Metadata>
) {
    data class Metadata(
        val pom: ProjectObjectModel,
        val gradleMetadata: GradleMetadata?,
        val publicationDate: Instant,
        val artifacts: List<String>
    )
}

suspend fun HttpClient.getMavenCentralInfo(
    groupId: String,
    artifactId: String
) = get("https://search.maven.org/solrsearch/select?q=g:$groupId+AND+a:$artifactId&core=gav&rows=5&wt=json")
    .body<MavenCentralApiResponse>()
    .takeIf { it.response.docs.isNotEmpty() }

suspend fun HttpClient.getBuildSystemsMetadata(
    mavenCentralApiResponse: MavenCentralApiResponse,
    pomSolver: PomResolver
) = MavenCoordinateWithVersions(
    mavenCentralApiResponse.response.docs.first().g,
    mavenCentralApiResponse.response.docs.first().a,
    mavenCentralApiResponse.response.docs
        .asFlow()
        .buffer()
        .mapNotNull { doc ->
            coroutineScope {
                val pomJob = async { pomSolver.resolve(doc.g, doc.a, doc.v) }
                val gradleMetadata = runCatching {
                    get(GoogleMavenCentralMirror.buildGradleMetadataUrl(doc.g, doc.a, doc.v))
                        .body<GradleMetadata>()
                }.getOrNull()
                val pom = pomJob.await() ?: return@coroutineScope null
                doc.v to MavenCoordinateWithVersions.Metadata(
                    gradleMetadata = gradleMetadata,
                    pom = pom,
                    publicationDate = Instant.fromEpochMilliseconds(doc.timestamp),
                    artifacts = doc.ec.map { "${doc.g}-${doc.v}-$it" }
                )
            }
        }
        .toList()
        .toMap()
)

fun MavenCoordinateWithVersions.toApiModels(): ApiPackage {
    val mavenVersions = versions
        .mapValues { (version, metadata) ->
            val (pom, gradleMetadata) = metadata
            when (gradleMetadata) {
                null -> ApiMavenPackage.MavenVersion(
                    normalized = NormalizedVersion.from(version, metadata.publicationDate),
                    repositoryIds = listOf("maven-central"),
                    vulnerability = Vulnerability(false),
                    dependencies = pom.dependencies.mapNotNull { it.toApiModel() },
                    artifacts = emptyList(),
                    name = pom.name,
                    description = pom.description,
                    authors = pom.developers.map { it.toApiModel() },
                    scmUrl = pom.scm?.url,
                    licenses = pom.licenses.toApiModel()
                )

                else -> ApiMavenPackage.GradleVersion(
                    normalized = NormalizedVersion.from(version, metadata.publicationDate),
                    repositoryIds = listOf("maven-central"),
                    vulnerability = Vulnerability(false),
                    dependencies = pom.dependencies.mapNotNull { it.toApiModel() },
                    artifacts = emptyList(),
                    variants = gradleMetadata.variants?.map { it.toApiModel() } ?: emptyList(),
                    parentComponent = gradleMetadata.component?.url,
                    name = pom.name,
                    description = pom.description,
                    authors = pom.developers.map { it.toApiModel() },
                    scmUrl = pom.scm?.url,
                    licenses = pom.licenses.toApiModel()
                )
            }
        }

    return ApiMavenPackage(
        id = "maven:$groupId:$artifactId",
        idHash = ApiPackage.hashPackageId("maven:$groupId:$artifactId"),
        rankingMetric = 20.0,
        versions = VersionsContainer(
            latestStable = mavenVersions.values
                .filter { it.normalized.isStable }
                .maxByOrNull { it.normalized },
            latest = mavenVersions.values.maxBy { it.normalized },
            all = mavenVersions
        ),
        groupId = groupId,
        artifactId = artifactId
    )

}

suspend fun PomResolver.toApiModels(ids: Iterable<String>) =
    ids.asFlow()
        .filter { it.startsWith("maven:") }
        .map { it.removePrefix("maven:") }
        .map { it.split(":").let { it[0] to it[1] } }
        .buffer()
        .mapNotNull { httpClient.getMavenCentralInfo(it.first, it.second) }
        .buffer()
        .map { httpClient.getBuildSystemsMetadata(it, this) }
        .map { it.toApiModels() }
        .toList()

internal var HttpTimeout.HttpTimeoutCapabilityConfiguration.requestTimeout: Duration?
    get() = requestTimeoutMillis?.milliseconds
    set(value) {
        requestTimeoutMillis = value?.inWholeMilliseconds
    }

internal fun HttpRequestRetry.Configuration.constantDelay(
    delay: Duration = 1.seconds,
    randomization: Duration = 1.seconds,
    respectRetryAfterHeader: Boolean = true
) {
    constantDelay(delay.inWholeMilliseconds, randomization.inWholeMilliseconds, respectRetryAfterHeader)
}