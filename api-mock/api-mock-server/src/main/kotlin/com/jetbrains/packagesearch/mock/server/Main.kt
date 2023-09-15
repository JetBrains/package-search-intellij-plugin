package com.jetbrains.packagesearch.mock.server

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.coroutineScope

suspend fun main(): Unit = coroutineScope {
    embeddedServer(CIO, port = 8081, module = Application::PackageSearchMockServer).start()
}