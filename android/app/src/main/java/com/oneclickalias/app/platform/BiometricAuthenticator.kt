package com.oneclickalias.app.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.oneclickalias.app.data.lock.AuthPurpose
import com.oneclickalias.app.data.lock.DeviceAuthenticator
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [DeviceAuthenticator] on AndroidX Biometric: fingerprint or face, with the
 * screen lock (PIN, pattern, password) as fallback.
 *
 * `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` is the one biometric-plus-credential
 * combination the library supports from API 26 to the newest
 * (`DEVICE_CREDENTIAL` alone needs API 30, `BIOMETRIC_STRONG or
 * DEVICE_CREDENTIAL` fails on API 28 and 29). No crypto object: app lock
 * guards the screen, it unlocks no key.
 *
 * The prompt needs the visible activity; [MainActivity][com.oneclickalias.app.MainActivity]
 * attaches itself in `onCreate`.
 */
class BiometricAuthenticator(context: Context) : DeviceAuthenticator {
    private val appContext = context.applicationContext
    private var activity = WeakReference<FragmentActivity>(null)

    fun attach(activity: FragmentActivity) {
        this.activity = WeakReference(activity)
    }

    override fun canAuthenticate(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(purpose: AuthPurpose): Boolean {
        val host = activity.get()
            // The library silently ignores a prompt after onSaveInstanceState; never wait for that.
            ?.takeUnless { it.isFinishing || it.isDestroyed || it.supportFragmentManager.isStateSaved }
            ?: return false
        return suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                // Cancel, lockout, no screen lock any more: the lock screen stays.
                // A single unrecognised finger (onAuthenticationFailed) keeps the prompt open.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            val prompt = BiometricPrompt(host, ContextCompat.getMainExecutor(appContext), callback)
            prompt.authenticate(promptInfo(purpose))
        }
    }

    private fun promptInfo(purpose: AuthPurpose): BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(
            when (purpose) {
                AuthPurpose.Unlock -> "Unlock OneClickAlias"
                AuthPurpose.Enable -> "Turn on app lock"
                AuthPurpose.ApproveSignIn -> "Approve the sign-in"
            }
        )
        .setSubtitle("Use your fingerprint, face or screen lock")
        .setAllowedAuthenticators(AUTHENTICATORS)
        // Face unlock opens the app without an extra tap.
        .setConfirmationRequired(false)
        .build()

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
