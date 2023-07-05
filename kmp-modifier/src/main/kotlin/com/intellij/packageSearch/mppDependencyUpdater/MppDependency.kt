package com.intellij.packageSearch.mppDependencyUpdater

sealed interface MppDependency {
  val version: String?

  data class Maven(
    val groupId: String,
    val artifactId: String,
    override val version: String?,
    val configuration: String
  ): MppDependency

  data class Npm(
    val name: String,
    override val version: String?,
    val configuration: String
  ): MppDependency

  data class Cocoapods(
    val name: String,
    override val version: String?
  ): MppDependency
}