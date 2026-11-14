package com.oneclickalias.app.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.EditText
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * True when the user has a password manager set as the system autofill
 * service (Settings > Passwords / Autofill service), and autofill is allowed
 * for this app. [context] must be the activity: the application context's
 * `AutofillManager` has no autofill client and reports false after the first
 * autofill session.
 */
fun passwordManagerAvailable(context: Context): Boolean {
    val manager = context.getSystemService(AutofillManager::class.java) ?: return false
    return manager.isAutofillSupported && manager.isEnabled
}

/**
 * Hands a secret (the new account number) to the user's password manager as a
 * password, through the Android autofill framework: works with any autofill
 * service (KeePassDX, Bitwarden, Proton Pass, …) and needs no Play Services.
 * AndroidX Credential Manager is not used: before Android 14 it needs Play
 * Services.
 *
 * The framework offers to save only a value that changed after the service
 * saw the screen; a number that is just shown never triggers it. So this keeps
 * an invisible, empty password field ([PasswordManagerSaveField]). [save] asks
 * the service about that field explicitly, waits for its answer, puts the
 * secret in, commits the autofill session (the service shows its save dialog)
 * and empties the field again. The secret is never logged or kept here.
 */
class PasswordManagerSave internal constructor() {
    internal var field: EditText? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running: Attempt? = null

    /** Main thread. Does nothing while the field is not on screen. */
    fun save(secret: String) {
        val field = field ?: return
        val manager = field.context.getSystemService(AutofillManager::class.java) ?: return
        stop()
        field.text.clear()
        val attempt = Attempt(field, manager, secret)
        running = attempt
        manager.registerCallback(attempt)
        handler.postDelayed(attempt, RESPONSE_TIMEOUT_MILLIS)
        manager.requestAutofill(field)
    }

    /** Drops an attempt still waiting for the service (screen left, or tapped again). */
    internal fun stop() {
        val attempt = running ?: return
        running = null
        handler.removeCallbacks(attempt)
        attempt.manager.unregisterCallback(attempt)
        attempt.manager.cancel()
    }

    override fun toString(): String = "PasswordManagerSave(running=${running != null})"

    /** Runs once: on the service's answer for the field, or after the timeout. */
    private inner class Attempt(val field: EditText, val manager: AutofillManager, private var secret: String?) :
        AutofillManager.AutofillCallback(),
        Runnable {
        override fun onAutofillEvent(view: View, event: Int) {
            if (view === field) {
                handler.removeCallbacks(this)
                handler.post(this)
            }
        }

        override fun run() {
            val value = secret ?: return
            secret = null
            if (running === this) running = null
            handler.removeCallbacks(this)
            manager.unregisterCallback(this)
            field.setText(value)
            manager.commit()
            field.text.clear()
        }

        override fun toString(): String = "Attempt(secret=<redacted>)"
    }

    companion object {
        /** A service that never answers must not leave the button dead. */
        internal const val RESPONSE_TIMEOUT_MILLIS = 2_000L
    }
}

/** The [PasswordManagerSave] of this screen; place [PasswordManagerSaveField] for it. */
@Composable
fun rememberPasswordManagerSave(): PasswordManagerSave {
    val save = remember { PasswordManagerSave() }
    DisposableEffect(save) { onDispose { save.stop() } }
    return save
}

/**
 * The invisible, empty password field [save] works with: 1dp, transparent,
 * not focusable and hidden from screen readers. Only a password manager sees it.
 */
@Composable
fun PasswordManagerSaveField(save: PasswordManagerSave, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isFocusable = false
                isFocusableInTouchMode = false
                isSaveEnabled = false
                save.field = this
            }
        },
        onRelease = { if (save.field === it) save.field = null },
        modifier = modifier
            .size(1.dp)
            .alpha(0f)
            .clearAndSetSemantics {}
    )
}
