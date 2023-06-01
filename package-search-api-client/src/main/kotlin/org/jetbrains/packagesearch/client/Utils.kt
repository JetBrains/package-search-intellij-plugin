package org.jetbrains.packagesearch.client

import io.ktor.http.*
import org.jetbrains.packagesearch.api.v3.http.GetScmByUrlRequest

suspend fun PackageSearchApiClient.getScmByUrl(urls: List<String>): String? =
    getScmByUrl(GetScmByUrlRequest(urls))

fun buildUrl(action: URLBuilder.() -> Unit) = URLBuilder().apply(action).build()