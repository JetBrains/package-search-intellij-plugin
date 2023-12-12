package com.jetbrains.packagesearch.plugin.maven

import com.intellij.openapi.roots.DependencyScope
import com.jetbrains.packagesearch.plugin.core.data.EditModuleContext
import com.jetbrains.packagesearch.plugin.core.data.PackageSearchDeclaredMavenPackage
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import java.io.Closeable
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import org.jetbrains.packagesearch.api.v3.ApiMavenRepository
import org.jetbrains.packagesearch.maven.HttpClientMavenPomProvider
import org.jetbrains.packagesearch.maven.MavenUrlBuilder
import org.jetbrains.packagesearch.maven.PomResolver
import org.jetbrains.packagesearch.maven.buildJarUrl
import org.jetbrains.packagesearch.maven.buildMavenUrl
import org.jetbrains.packagesearch.maven.buildSourcesJarUrl
import org.jetbrains.packagesearch.maven.dependencies

internal class EditJpsModuleContext : EditModuleContext {
    override val data = JpsActions()
}

internal class JpsActions {
    private val toRemove = mutableListOf<String>()
    private val toAdd = mutableListOf<Add>()
    private val toModify = mutableListOf<Modify>()

    internal data class Modify(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val oldScope: DependencyScope,
        val newScope: DependencyScope,
    ) {
        val id: String
            get() = "$groupId:$artifactId:$version"
    }

    internal data class Add(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val scope: DependencyScope?,
        val repositories: List<ApiMavenRepository>,
    ) {
        val id: String
            get() = "$groupId:$artifactId:$version"
    }

    fun remove(declaredPackage: PackageSearchDeclaredMavenPackage) {
        val declaredVersionName = declaredPackage.declaredVersion?.versionName ?: return
        toRemove.add("${declaredPackage.groupId}:${declaredPackage.artifactId}:$declaredVersionName}")
    }

    fun add(
        groupId: String,
        artifactId: String,
        version: String,
        scope: DependencyScope,
        repositories: List<ApiMavenRepository>,
    ) {
        toAdd.add(Add(groupId, artifactId, version, scope, repositories))
    }

    fun modify(
        scope: DependencyScope,
        declaredPackage: PackageSearchDeclaredMavenPackage,
        newVersion: String,
        knownRepositories: Map<String, ApiMavenRepository>,
    ) {
        if (newVersion == declaredPackage.declaredVersion?.versionName) {
            toModify.add(Modify(
                groupId = declaredPackage.groupId,
                artifactId = declaredPackage.artifactId,
                version = declaredPackage.declaredVersion?.versionName ?: return,
                oldScope = DependencyScope.entries
                    .firstOrNull { it.name.equals(declaredPackage.declaredScope, true) }
                    ?: DependencyScope.COMPILE,
                newScope = scope
            ))
            return
        }
        val declaredVersionName = declaredPackage.declaredVersion?.versionName ?: return
        toRemove.add("${declaredPackage.groupId}:${declaredPackage.artifactId}:$declaredVersionName")
        toAdd.add(
            Add(
                groupId = declaredPackage.groupId,
                artifactId = declaredPackage.artifactId,
                version = newVersion,
                scope = DependencyScope.COMPILE,
                repositories = declaredPackage.remoteInfo?.versions?.all
                    ?.firstOrNull { it.normalizedVersion.versionName == newVersion }
                    ?.repositoryIds
                    ?.mapNotNull { knownRepositories[it] }
                    ?: emptyList()
            )
        )
    }

    fun getToAdd() = toAdd.toList()
    fun getToRemove() = toRemove.toList()
    fun getToModify() = toModify.toList()
}

class JpsDownloader(private val downloadsPath: Path) : Closeable {


    private val xml = PomResolver.defaultXml()
    private val httpClient = HttpClientMavenPomProvider.defaultHttpClient(xml) {
        install(HttpTimeout) {
            requestTimeoutMillis = 1000
            connectTimeoutMillis = 10000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 5)
            exponentialDelay()
        }
    }

    data class DownloadedLibrary(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val classesPath: Path,
        val sourcesPath: Path,
    )

    private data class LibraryToDownload(
        val groupId: String,
        val artifactId: String,
        val version: String,
    )

    suspend fun downloadLibrary(
        groupId: String,
        artifactId: String,
        version: String,
        repositories: List<ApiMavenRepository>,
    ): Set<DownloadedLibrary> = coroutineScope {
        val urlBuilders = repositories.map { ApiMavenRepositoryUrlBuilder(it) }
        val pomProvider = HttpClientMavenPomProvider(urlBuilders, httpClient, xml)
        val resolver = PomResolver(pomProvider, xml)

        val initial = LibraryToDownload(groupId, artifactId, version)
        val toProcess = mutableListOf(initial)
        val downloaded = mutableSetOf(initial)
        val toDownload = Channel<LibraryToDownload>(capacity = Channel.UNLIMITED)
        toDownload.send(initial)
        launch {
            while (toProcess.isNotEmpty()) {
                val library = toProcess.removeFirst()
                val pom = resolver.getPom(library.groupId, library.artifactId, library.version) ?: continue
                val dependenciesToDownload = pom.dependencies
                    .filter { it.scope == "compile" || it.scope == "runtime" || it.scope == null }
                    .mapNotNull {
                        LibraryToDownload(
                            groupId = it.groupId,
                            artifactId = it.artifactId,
                            version = it.version ?: return@mapNotNull null,
                        )
                    }
                    .filter { it !in downloaded }
                downloaded.addAll(dependenciesToDownload)
                toProcess.addAll(dependenciesToDownload)
                launch { dependenciesToDownload.forEach { toDownload.send(it) } }
            }
        }
        toDownload.consumeAsFlow()
            .buffer(5)
            .transform { library ->
                val outputDir = library.groupId
                    .split(":")
                    .fold(downloadsPath) { acc, s -> acc / s }
                    .resolve(library.artifactId)
                    .resolve(library.version)
                    .createDirectories()

                val classesPath = outputDir
                    .resolve("${library.artifactId}-${library.version}.jar")
                val done1 = urlBuilders.asFlow()
                    .map { it.buildJarUrl(library.groupId, library.artifactId, library.version) }
                    .map { httpClient.get(it).bodyAsChannel() }
                    .map { it.copyAndClose(classesPath.toFile().writeChannel()) }
                    .map { true }
                    .take(1)
                    .catch { emit(false) }
                    .firstOrNull() ?: false
                val sourcesPath = outputDir.resolve("${library.artifactId}-${library.version}-sources.jar")
                val done2 = urlBuilders.asFlow()
                    .map { it.buildSourcesJarUrl(library.groupId, library.artifactId, library.version) }
                    .map { httpClient.get(it).bodyAsChannel() }
                    .map { it.copyAndClose(sourcesPath.toFile().writeChannel()) }
                    .take(1)
                    .map { true }
                    .catch { emit(false) }
                    .firstOrNull() ?: false
                if (done1 && done2) {
                    emit(
                        DownloadedLibrary(
                            groupId = library.groupId,
                            artifactId = library.artifactId,
                            version = library.version,
                            classesPath = classesPath,
                            sourcesPath = sourcesPath,
                        )
                    )
                }
            }
            .toSet()
    }

    override fun close() = httpClient.close()
}

class ApiMavenRepositoryUrlBuilder(val repository: ApiMavenRepository) : MavenUrlBuilder {
    override fun buildArtifactUrl(
        groupId: String,
        artifactId: String,
        version: String,
        artifactExtension: String,
    ): Url = buildMavenUrl(
        groupId = groupId,
        artifactId = artifactId,
        version = version,
        host = repository.url,
        artifactExtension = artifactExtension,
    )

    override fun buildMetadataUrl(groupId: String, artifactId: String) =
        buildMavenUrl(
            groupId = groupId,
            artifactId = artifactId,
            version = null,
            host = repository.url,
            artifactExtension = "maven-metadata.xml",
        )
}
