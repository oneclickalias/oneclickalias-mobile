package com.oneclickalias.app.platform

import androidx.compose.runtime.Composable

/**
 * Release builds: nothing. The debug build's version of this file lets a test
 * send a "scanned" text over adb (src/debug); a release build has no way to
 * inject one.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ScanTextInjection(onText: (String) -> Unit) = Unit
