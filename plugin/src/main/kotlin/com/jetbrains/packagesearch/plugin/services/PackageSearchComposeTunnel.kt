package org.jetbrains.packagesearch.plugin.services

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Service(Service.Level.PROJECT)
class PackageSearchComposeTunnel() {

    internal val infoTabStateFlow = MutableStateFlow<InfoTabState>(InfoTabState.Close)

}

@Serializable
sealed interface InfoTabState {
    @Serializable
    object Open : InfoTabState

    @Serializable
    object Close : InfoTabState
}