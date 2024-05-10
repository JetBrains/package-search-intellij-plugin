package com.jetbrains.packagesearch.plugin.utils

import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.packagesearch.api.v3.ApiPackage

internal fun Map<String, ApiPackage>.getIds(useHash: Boolean) = map {
    when {
        useHash -> it.value.idHash
        else -> it.value.id
    }
}.toSet()

internal fun List<ApiPackageCacheEntry>.getIds(useHash: Boolean) = mapNotNull {
    when {
        useHash -> it.packageIdHash
        else -> it.packageId
    }
}.toSet()

internal fun Iterable<String>.hashIfNeeded(useHash: Boolean) =
    if (!useHash) map { ApiPackage.hashPackageId(it) } else toList()

internal fun Random.Default.nextDuration(start: Duration, end: Duration): Duration {
    require(start < end) { "Start duration must be less than end duration" }
    val interval = end - start
    return nextLong(interval.inWholeMinutes).minutes + start
}

fun <T> Result<T>.suspendSafe() = onFailure { if (it is CancellationException) throw it }