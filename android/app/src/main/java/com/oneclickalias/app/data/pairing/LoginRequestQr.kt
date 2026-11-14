package com.oneclickalias.app.data.pairing

/**
 * What a scanned QR text means when the signed-in app signs in a browser
 * (docs/flows.md, "Sign in a browser"). Checked exactly as scanned.
 */
sealed interface LoginRequestQr {
    /** A browser's sign-in code of the version this app speaks; [code] goes to `claim`. */
    data class Valid(val code: String) : LoginRequestQr {
        // The code signs a browser into this account once approved.
        override fun toString(): String = "Valid(code=<redacted>)"
    }

    /** A code for signing in the app (`OCA-PAIR-…`), shown by a signed-in dashboard. */
    data object AppSignInCode : LoginRequestQr

    /** Starts with `OCA-LOGIN-` but has another format version: the app needs an update. */
    data object UnsupportedVersion : LoginRequestQr

    /** Not a OneClickAlias code at all. */
    data object NotOurs : LoginRequestQr

    companion object {
        private const val PREFIX = "OCA-LOGIN-"
        private const val VERSION_PREFIX = "OCA-LOGIN-1-"

        /** Schema `LoginRequestQrPayload`. */
        private val PAYLOAD = Regex("^OCA-LOGIN-1-[A-Z2-7]{32}$")

        fun parse(text: String): LoginRequestQr = when {
            PAYLOAD.matches(text) -> Valid(text.removePrefix(VERSION_PREFIX))
            text.startsWith(PREFIX) -> UnsupportedVersion
            PairingQr.parse(text) !is PairingQr.NotOurs -> AppSignInCode
            else -> NotOurs
        }
    }
}
