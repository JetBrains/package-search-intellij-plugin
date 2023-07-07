package org.jetbrains.packagesearch.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.Author
import org.jetbrains.packagesearch.api.v3.LicenseFile
import org.jetbrains.packagesearch.api.v3.Licenses
import org.jetbrains.packagesearch.api.v3.VersionsContainer
import org.jetbrains.packagesearch.api.v3.Vulnerability
import org.jetbrains.packagesearch.gradle.File
import org.jetbrains.packagesearch.gradle.GradleMetadata
import org.jetbrains.packagesearch.gradle.Variant
import org.jetbrains.packagesearch.maven.Dependency
import org.jetbrains.packagesearch.maven.Developer
import org.jetbrains.packagesearch.maven.GoogleMavenCentralMirror
import org.jetbrains.packagesearch.maven.License
import org.jetbrains.packagesearch.maven.PomResolver
import org.jetbrains.packagesearch.maven.ProjectObjectModel
import org.jetbrains.packagesearch.maven.buildGradleMetadataUrl
import org.jetbrains.packagesearch.maven.central.MavenCentralApiResponse
import org.jetbrains.packagesearch.maven.dependencies
import org.jetbrains.packagesearch.maven.developers
import org.jetbrains.packagesearch.maven.licenses
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

fun List<License>.toApiModel() = first().toApiModel()?.let {
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

fun Variant.toApiModel(): ApiMavenPackage.ApiVariant = when {
    availableAt != null -> ApiMavenPackage.ApiVariant.WithAvailableAt(
        name = name,
        attributes = attributes?.toApiAttributes() ?: emptyMap(),
        availableAt = ApiMavenPackage.ApiVariant.WithAvailableAt.AvailableAt(
            url = availableAt!!.url,
            group = availableAt!!.group,
            module = availableAt!!.module,
            version = availableAt!!.version
        )
    )

    else -> ApiMavenPackage.ApiVariant.WithFiles(
        name = name,
        attributes = attributes?.toApiAttributes() ?: emptyMap(),
        dependencies = dependencies?.map { it.toApiModel() } ?: emptyList(),
        files = files?.map { it.toApiModel() } ?: emptyList()
    )
}

fun Map<String, String>.toApiAttributes() = mapValues { (name, value) ->
    ApiMavenPackage.ApiVariant.Attribute.create(name, value)
}

fun GradleMetadataDependency.toApiModel() =
    ApiMavenPackage.ApiGradleDependency(
        group,
        module,
        version.requires
    )

fun File.toApiModel() = ApiMavenPackage.ApiVariant.File(
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
) = get("https://search.maven.org/solrsearch/select?q=g:$groupId+AND+a:$artifactId&core=gav&rows=50&wt=json")
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
        .map { doc ->
            doc.v to coroutineScope {
                val pom = async { pomSolver.resolve(doc.g, doc.a, doc.v) }
                val gradleMetadata = runCatching {
                    get(GoogleMavenCentralMirror.buildGradleMetadataUrl(doc.g, doc.a, doc.v))
                        .body<GradleMetadata>()
                }.getOrNull()
                MavenCoordinateWithVersions.Metadata(
                    gradleMetadata = gradleMetadata,
                    pom = pom.await(),
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
                    parentComponent = gradleMetadata.component.url,
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
                .maxBy { it.normalized },
            latest = mavenVersions.values.maxBy { it.normalized },
            all = mavenVersions
        ),
        groupId = groupId,
        artifactId = artifactId
    )

}

suspend fun PomResolver.toApiModels(ids: Iterable<String>) =
    ids.asFlow()
        .buffer()
        .filter { it.startsWith("maven:") }
        .map { it.removePrefix("maven:") }
        .map { it.split(":").let { it[0] to it[1] } }
        .mapNotNull { httpClient.getMavenCentralInfo(it.first, it.second) }
        .map { httpClient.getBuildSystemsMetadata(it, this) }
        .map { it.toApiModels() }
        .toList()