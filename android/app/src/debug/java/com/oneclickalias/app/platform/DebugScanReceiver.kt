package com.oneclickalias.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug builds only: hands the `text` extra to an open QR scanner, see
 * [ScanTextInjection]. Declared in src/debug/AndroidManifest.xml and protected
 * by the `DUMP` permission, which the adb shell holds and normal apps cannot
 * get, so other apps on the phone cannot inject sign-in codes either.
 */
class DebugScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        DebugScans.texts.tryEmit(text)
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}
