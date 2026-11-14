package com.oneclickalias.app.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * Sets `FLAG_SECURE` on the window while this is in the composition and
 * [enabled]: no screenshots, no screen recording, blank thumbnail in the app
 * switcher. Use it only while an account number is visible, or while app
 * lock is on (then for the whole signed-in app). Counted per
 * window, so two screens overlapping during a transition do not clear it early.
 */
@Composable
fun SecureWindow(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.findActivity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose {}
        SecureFlagCounter.acquire(window)
        onDispose { SecureFlagCounter.release(window) }
    }
}

/** Main thread only, like all window changes. */
private object SecureFlagCounter {
    private val holders = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val count = holders[window] ?: 0
        holders[window] = count + 1
        if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        val count = (holders[window] ?: 1) - 1
        if (count <= 0) {
            holders.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            holders[window] = count
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
