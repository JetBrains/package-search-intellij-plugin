package com.jetbrains.packagesearch.mock

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.ApiRepository
import org.jetbrains.packagesearch.api.v3.Author
import org.jetbrains.packagesearch.api.v3.Licenses
import org.jetbrains.packagesearch.api.v3.PomLicenseFile
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

fun List<License>.toApiModel() = firstOrNull()?.toApiModel()?.let {
    Licenses(
        it,
        drop(1).mapNotNull { it.toApiModel() }
    )
}

fun License.toApiModel() = url?.let {
    PomLicenseFile(
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
    val versions: Map<String, Metadata>,
) {
    data class Metadata(
        val pom: ProjectObjectModel,
        val gradleMetadata: GradleMetadata?,
        val publicationDate: Instant,
        val artifacts: List<String>,
    )
}

suspend fun HttpClient.getMavenCentralInfo(
    groupId: String,
    artifactId: String,
) = get("https://search.maven.org/solrsearch/select?q=g:$groupId+AND+a:$artifactId&core=gav&rows=5&wt=json")
    .body<MavenCentralApiResponse>()
    .takeIf { it.response.docs.isNotEmpty() }

suspend fun HttpClient.getBuildSystemsMetadata(
    mavenCentralApiResponse: MavenCentralApiResponse,
    pomSolver: PomResolver,
): MavenCoordinateWithVersions? {
    val firstDoc = mavenCentralApiResponse.response.docs.firstOrNull() ?: return null
    return MavenCoordinateWithVersions(
        firstDoc.groupId,
        firstDoc.artifactId,
        mavenCentralApiResponse.response.docs
            .asFlow()
            .buffer()
            .mapNotNullScoped { doc ->
                val version = doc.version ?: return@mapNotNullScoped null
                val pomJob = async { pomSolver.getPom(doc.groupId, doc.artifactId, version) }
                val gradleMetadataUrl = GoogleMavenCentralMirror.buildGradleMetadataUrl(
                    groupId = doc.groupId,
                    artifactId = doc.artifactId,
                    version = version
                )
                val gradleMetadata = runCatching { get(gradleMetadataUrl).body<GradleMetadata>() }
                    .getOrNull()
                val pom = pomJob.await() ?: return@mapNotNullScoped null
                version to MavenCoordinateWithVersions.Metadata(
                    gradleMetadata = gradleMetadata,
                    pom = pom,
                    publicationDate = Instant.fromEpochMilliseconds(doc.timestamp),
                    artifacts = doc.ec.map { "${doc.groupId}-$version-$it" }
                )
            }
            .toList()
            .toMap()
    )
}

inline fun <T, R : Any> Flow<T>.mapNotNullScoped(
    crossinline transform: suspend CoroutineScope.(value: T) -> R?,
): Flow<R> = transform { value ->
    val transformed = coroutineScope { transform(value) } ?: return@transform
    emit(transformed)
}

fun MavenCoordinateWithVersions.toMavenApiModel(): ApiMavenPackage {
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
//                    licenses = pom.licenses.toApiModel()
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
//                    licenses = pom.licenses.toApiModel()
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

internal var HttpTimeout.HttpTimeoutCapabilityConfiguration.requestTimeout: Duration?
    get() = requestTimeoutMillis?.milliseconds
    set(value) {
        requestTimeoutMillis = value?.inWholeMilliseconds
    }

internal fun HttpRequestRetry.Configuration.constantDelay(
    delay: Duration = 1.seconds,
    randomization: Duration = 1.seconds,
    respectRetryAfterHeader: Boolean = true,
) {
    constantDelay(delay.inWholeMilliseconds, randomization.inWholeMilliseconds, respectRetryAfterHeader)
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