@file:Suppress("UnstableApiUsage")

package com.jetbrains.packagesearch.plugin.services

import com.intellij.ide.actions.cache.RecoveryAction
import com.jetbrains.packagesearch.plugin.core.utils.IntelliJApplication
import com.jetbrains.packagesearch.plugin.utils.PackageSearchApplicationCachesService

class CleanPackageSearchApplicationCacheAction :
    RecoveryAction by IntelliJApplication.PackageSearchApplicationCachesService