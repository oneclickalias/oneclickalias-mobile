package com.oneclickalias.app.data.lock

/** Why the system prompt is shown; decides its title. */
enum class AuthPurpose {
    /** The lock screen: open the app again. */
    Unlock,

    /** Turning app lock on: proves the prompt works on this phone. */
    Enable,

    /** Approving the sign-in of a browser while app lock is on. */
    ApproveSignIn
}

/**
 * The system's authentication prompt: biometrics (fingerprint, face) with the
 * phone's screen lock (PIN, pattern, password) as fallback. Behind an
 * interface so [AppLock] runs in JVM tests with a fake.
 */
interface DeviceAuthenticator {
    /** The phone has a screen lock (and maybe biometrics) the prompt can use. */
    fun canAuthenticate(): Boolean

    /** Shows the prompt; true once the user is confirmed, false if cancelled or unavailable. */
    suspend fun authenticate(purpose: AuthPurpose): Boolean
}
