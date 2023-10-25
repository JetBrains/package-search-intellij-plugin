package com.jetbrains.packagesearch.plugin.ui

import androidx.compose.runtime.Stable

@Stable
data class ActionState(val isPerforming: Boolean, val actionType: ActionType, val actionId: String? = null)

enum class ActionType {
    ADD, REMOVE, UPDATE;
}