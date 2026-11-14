package com.oneclickalias.app.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [SecretClipboard] on the system clipboard:
 * - The clip is flagged `IS_SENSITIVE`, so Android 13+ hides it in the copy
 *   preview and keyboards' clipboard suggestions (older versions ignore it).
 * - After [clearAfterMillis] the clip is cleared, but only if it still holds
 *   our secret. Only a SHA-256 hash is kept for that check, not the secret.
 *
 * Limits (Android): apps can read the clipboard only while in the foreground
 * (Android 10+). In the background the check cannot see the current clip; it
 * then clears unless a newer clip was observed while we were in the
 * foreground, so a clip copied in another app during that window may be
 * cleared too. If the process dies before the timer fires, nothing is cleared
 * (Android 13+ clears the clipboard by itself after an hour).
 */
class SensitiveClipboard(
    context: Context,
    private val scope: CoroutineScope,
    private val clearAfterMillis: Long = DEFAULT_CLEAR_AFTER_MILLIS
) : SecretClipboard {
    private val manager = context.applicationContext.getSystemService(ClipboardManager::class.java)
    private var secretHash: ByteArray? = null
    private var clearJob: Job? = null

    init {
        manager.addPrimaryClipChangedListener {
            // Fires for our own copy too; only a readable, different clip counts as foreign.
            val text = readText() ?: return@addPrimaryClipChangedListener
            val hash = secretHash ?: return@addPrimaryClipChangedListener
            if (!MessageDigest.isEqual(hash, sha256(text))) forget()
        }
    }

    override fun copy(label: String, secret: String) {
        val clip = ClipData.newPlainText(label, secret)
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        clearJob?.cancel()
        secretHash = sha256(secret)
        manager.setPrimaryClip(clip)
        clearJob = scope.launch {
            delay(clearAfterMillis)
            clearIfStillOurs()
        }
    }

    private fun clearIfStillOurs() {
        val hash = secretHash ?: return
        val text = readText()
        if (text == null || MessageDigest.isEqual(hash, sha256(text))) clear()
        forget()
    }

    private fun clear() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private fun forget() {
        clearJob?.cancel()
        clearJob = null
        secretHash = null
    }

    /** Current clip text, or null when there is none or the app may not read it. */
    private fun readText(): String? = manager.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.text
        ?.toString()

    private fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())

    companion object {
        const val DEFAULT_CLEAR_AFTER_MILLIS = 60_000L

        // Value of ClipDescription.EXTRA_IS_SENSITIVE (API 33); the platform docs
        // ask to use the literal on older versions.
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
