package com.jetbrains.packagesearch.plugin.tests

import com.jetbrains.packagesearch.plugin.tests.dumps.DumpPackageSearchModules
import com.jetbrains.performancePlugin.CommandProvider

class PKGSCommandProvider : CommandProvider {

    override fun getCommands() = mapOf(
        DumpPackageSearchModules.COMMAND
    )

}

