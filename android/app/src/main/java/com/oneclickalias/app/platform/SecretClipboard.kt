package com.oneclickalias.app.platform

/** Copies a secret (the account number) so that it does not linger. */
fun interface SecretClipboard {
    fun copy(label: String, secret: String)
}
