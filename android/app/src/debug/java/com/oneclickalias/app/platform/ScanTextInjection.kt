package com.oneclickalias.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Debug builds only (the release build has an empty version): while the QR
 * scanner is on screen, texts sent with
 * `adb shell am broadcast -n com.oneclickalias.app/.platform.DebugScanReceiver --es text '<text>'`
 * reach [onText] exactly like a text the camera recognised. Automated tests
 * cannot hold a camera up to a screen; this is how they test the QR login on
 * a device.
 */
@Composable
fun ScanTextInjection(onText: (String) -> Unit) {
    val current by rememberUpdatedState(onText)
    LaunchedEffect(Unit) { DebugScans.texts.collect { current(it) } }
}
