package com.jetbrains.packagesearch.plugin.ui.sections.infobox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jetbrains.packagesearch.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.plugin.ui.ActionState
import com.jetbrains.packagesearch.plugin.ui.ActionType
import com.jetbrains.packagesearch.plugin.ui.LocalIsActionPerformingState
import com.jetbrains.packagesearch.plugin.ui.LocalPackageSearchService
import com.jetbrains.packagesearch.plugin.ui.bridge.LabelInfo
import com.jetbrains.packagesearch.plugin.ui.bridge.openLinkInBrowser
import com.jetbrains.packagesearch.plugin.ui.bridge.pointerChangeToHandModifier
import com.jetbrains.packagesearch.plugin.utils.logWarn
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.jetbrains.packagesearch.api.v3.LicenseFile
import org.jetbrains.packagesearch.api.v3.Licenses


@Composable
internal fun DefaultActionButton(
    actionName: String,
    actionType: ActionType,
    actionLambda: suspend CoroutineScope.() -> Unit,
) {
    var isActionPerforming by LocalIsActionPerformingState.current
    val service = LocalPackageSearchService.current
    Box(
        modifier = Modifier.pointerChangeToHandModifier()
    ) {
        OutlinedButton(
            enabled = isActionPerforming == null,
            onClick = {
                if (isActionPerforming == null) {
                    val actionId = UUID.randomUUID().toString()
                    isActionPerforming = ActionState(true, actionType, actionId)
                    service.coroutineScope.launch {
                        runCatching {
                            actionLambda()
                        }
                        delay(5.seconds)
                        if (isActionPerforming?.actionId == actionId) {
                            logWarn("Action update timeout for infobox panel")
                            isActionPerforming = null
                        }
                    }

                }

            },
            modifier = Modifier.padding(2.dp)
        ) {
            Text(
                actionName,
                modifier = Modifier.padding(2.dp)
            )
        }
    }
}


@Composable
internal fun PackageOverviewNameId(
    modifier: Modifier = Modifier,
    name: String?,
    id: String,
) {
    Row(modifier.padding(12.dp, 12.dp, 4.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name ?: id, fontWeight = FontWeight.Bold)
            if (name != null) LabelInfo(id)
        }
    }
}

@Composable
internal fun RowScope.LicenseLinks(
    it: Licenses<out LicenseFile>,
) {
    val scope = LocalPackageSearchService.current.coroutineScope
    LabelInfo(
        modifier = Modifier.defaultMinSize(90.dp),
        text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.details.info.licenses")
    )

    val licenses = listOf(it.mainLicense) + it.otherLicenses

    licenses.forEachIndexed { index, licenseFile ->
        val name = licenseFile.name
            ?: licenseFile.url?.substringAfterLast("/")
            ?: return@forEachIndexed
        when (val url = licenseFile.url) {
            null -> Text(name)
            else -> ExternalLink(
                text = name,
                onClick = {
                    scope.launch {
                        openLinkInBrowser(url)
                    }
                },
            )
        }
        if (index != licenses.lastIndex) {
            Text(",")
        }
    }
}


val ApiPackage.packageTypeName
    get() = when (this) {
        is ApiMavenPackage -> "maven"
    }