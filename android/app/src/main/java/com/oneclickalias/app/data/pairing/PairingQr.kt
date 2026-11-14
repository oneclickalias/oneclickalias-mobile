package com.oneclickalias.app.data.pairing

/**
 * What a scanned QR text means for the QR login (docs/flows.md,
 * "Log in with a code"). The text is checked exactly as scanned: no trimming, no case
 * change, no other normalisation.
 */
sealed interface PairingQr {
    /** A sign-in code of the version this app speaks; [code] goes to `claim`. */
    data class Valid(val code: String) : PairingQr {
        // The code is a one-time credential for about two minutes.
        override fun toString(): String = "Valid(code=<redacted>)"
    }

    /** Starts with `OCA-PAIR-` but has another format version: the app needs an update. */
    data object UnsupportedVersion : PairingQr

    /** Not a OneClickAlias sign-in code at all. */
    data object NotOurs : PairingQr

    companion object {
        private const val PREFIX = "OCA-PAIR-"
        private const val VERSION_PREFIX = "OCA-PAIR-1-"

        /** Schema `PairingQrPayload`. */
        private val PAYLOAD = Regex("^OCA-PAIR-1-[A-Z2-7]{32}$")

        fun parse(text: String): PairingQr = when {
            PAYLOAD.matches(text) -> Valid(text.removePrefix(VERSION_PREFIX))
            text.startsWith(PREFIX) && !text.startsWith(VERSION_PREFIX) -> UnsupportedVersion
            else -> NotOurs
        }
    }
}
