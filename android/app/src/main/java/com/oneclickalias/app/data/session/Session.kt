package com.oneclickalias.app.data.session

/**
 * The signed-in device's credentials. Lives in memory in `SessionManager` and,
 * encrypted, in [SessionStore]; never anywhere else.
 *
 * @property accessTokenExpiresAtMillis wall-clock time the access token expires
 *   (receipt time plus `expiresIn`).
 * @property obtainedAtMillis wall-clock time this token pair was received.
 */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val deviceId: String,
    val accessTokenExpiresAtMillis: Long,
    val obtainedAtMillis: Long
) {
    // Tokens must never reach logcat, crash reports or test output by accident.
    override fun toString(): String = "Session(<redacted>)"
}
