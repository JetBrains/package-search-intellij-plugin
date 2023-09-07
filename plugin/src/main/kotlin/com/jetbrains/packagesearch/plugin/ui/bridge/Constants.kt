package com.jetbrains.packagesearch.plugin.ui.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

const val TextHeaderMultiplier = 2f
const val TextSubHeaderMultiplier = 1.5f
const val TextBodyMultiplier = 1f
const val TextCaptionMultiplier = 0.75f
const val TextSmallMultiplier = 0.5f
const val TextTinyMultiplier = 0.25f

// default spacing
@Composable
fun SpacingNone() = 0.dp

@Composable
fun SpacingMainElements() = 4.dp

@Composable
fun SpacingInnerElements() = 2.dp
