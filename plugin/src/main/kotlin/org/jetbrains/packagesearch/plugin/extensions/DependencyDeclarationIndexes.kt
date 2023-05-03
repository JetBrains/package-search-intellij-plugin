package org.jetbrains.packagesearch.plugin.extensions

import kotlinx.serialization.Serializable

/**
 * Container class for declaration coordinates for a dependency in a build file. \
 * Example for Gradle:
 * ```
 *    implementation("io.ktor:ktor-server-cio:2.0.0")
 * // ▲               ▲                       ▲
 * // |               ∟ coordinatesStartIndex |
 * // ∟ wholeDeclarationStartIndex            ∟ versionStartIndex
 * //
 * ```
 * Example for Maven:
 * ```
 *      <dependency>
 * //    ▲ wholeDeclarationStartIndex
 *          <groupId>io.ktor</groupId>
 * //                ▲ coordinatesStartIndex
 *          <artifactId>ktor-server-cio</artifactId>
 *          <version>2.0.0</version>
 * //                ▲ versionStartIndex
 *      </dependency>
 * ```
 * @param wholeDeclarationStartIndex index of the first character where the whole declarations starts.
 *
 */
@Serializable
data class DependencyDeclarationIndexes(
    val wholeDeclarationStartIndex: Int,
    val coordinatesStartIndex: Int,
    val versionStartIndex: Int?
)