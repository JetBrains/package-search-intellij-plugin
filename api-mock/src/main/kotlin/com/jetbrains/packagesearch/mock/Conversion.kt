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
import org.jetbrains.packagesearch.api.v3.PomLicenseFile
import org.jetbrains.packagesearch.api.v3.VersionWithRepositories
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
import org.jetbrains.packagesearch.maven.central.Doc
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

suspend fun HttpClient.getMavenCentralInfo(
    groupId: String,
    artifactId: String,
) = get("https://search.maven.org/solrsearch/select?q=g:$groupId+AND+a:$artifactId&core=gav&rows=5&wt=json")
    .body<MavenCentralApiResponse>()
    .takeIf { it.response.docs.isNotEmpty() }

suspend fun HttpClient.buildMetadata(
    pomSolver: PomResolver,
    doc: Doc,
    normalizedVersion: NormalizedVersion,
): MavenCoordinateWithVersions.Metadata? = coroutineScope {
    val latestVersionPomJob = async {
        pomSolver.getPom(doc.groupId, doc.artifactId, normalizedVersion.versionName)
    }
    val latestVersionGradleMetadataUrl = GoogleMavenCentralMirror.buildGradleMetadataUrl(
        groupId = doc.groupId,
        artifactId = doc.artifactId,
        version = normalizedVersion.versionName
    )
    val latestVersionGradleMetadata = runCatching { get(latestVersionGradleMetadataUrl).body<GradleMetadata>() }
        .getOrNull()
    val latestVersionPom = latestVersionPomJob.await() ?: return@coroutineScope null

    MavenCoordinateWithVersions.Metadata(
        version = normalizedVersion,
        pom = latestVersionPom,
        gradleMetadata = latestVersionGradleMetadata,
        artifacts = doc.ec.map { "${doc.groupId}-${normalizedVersion.versionName}-$it" }
    )
}

suspend fun HttpClient.getBuildSystemsMetadata(
    mavenCentralApiResponse: MavenCentralApiResponse,
    pomSolver: PomResolver,
): MavenCoordinateWithVersions? = coroutineScope {
    val firstDoc = mavenCentralApiResponse.response.docs.firstOrNull() ?: return@coroutineScope null

    val normalizedVersions = mavenCentralApiResponse.response.docs
        .associateBy { NormalizedVersion.from(it.version, Instant.fromEpochMilliseconds(it.timestamp)) }

    val (latestNormalizedVersion, latestDoc) = normalizedVersions.maxBy { it.key }
    val latestStablePair = normalizedVersions.filterKeys { it.isStable }.maxByOrNull { it.key }
    val latestVersionJob = async { buildMetadata(pomSolver, latestDoc, latestNormalizedVersion) }
    val latestStableVersion = latestStablePair
        ?.let { (latestStableNormalizedVersion, latestStableDoc) ->
            buildMetadata(pomSolver, latestStableDoc, latestStableNormalizedVersion)
        }

    val latestVersion = latestVersionJob.await() ?: return@coroutineScope null

    MavenCoordinateWithVersions(
        groupId = firstDoc.groupId,
        artifactId = firstDoc.artifactId,
        latestStable = latestStableVersion,
        latest = latestVersion,
        allVersion = normalizedVersions.keys.toList()
    )
}

inline fun <T, R : Any> Flow<T>.mapNotNullScoped(
    crossinline transform: suspend CoroutineScope.(value: T) -> R?,
): Flow<R> = transform { value ->
    val transformed = coroutineScope { transform(value) } ?: return@transform
    emit(transformed)
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